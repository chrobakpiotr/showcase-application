package com.cp.ecommerce.adapter.persistence.inventory;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.common.exception.InsufficientStockException;
import com.cp.ecommerce.adapter.persistence.inventory.entity.StockLevelEntity;
import com.cp.ecommerce.adapter.persistence.inventory.entity.StockLevelEntityRepository;
import com.cp.ecommerce.adapter.persistence.inventory.entity.StockReservationEntity;
import com.cp.ecommerce.adapter.persistence.inventory.entity.StockReservationEntityRepository;
import com.cp.ecommerce.adapter.persistence.inventory.entity.StockReservationStatus;
import com.cp.ecommerce.domain.inventory.StockLevel;
import com.cp.ecommerce.domain.inventory.port.outgoing.ManageStockReservationOutPort;

import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Atomic identity-aware inventory mutation adapter.
 */
@PersistenceAdapter
@Transactional
@RequiredArgsConstructor
class ManageStockReservationAdapter implements ManageStockReservationOutPort {

    private final StockLevelEntityRepository stockLevelEntityRepository;

    private final StockReservationEntityRepository stockReservationEntityRepository;

    @Override
    public StockLevel reserveStock(final String reservationId, final String sku, final int quantity) {

        final StockLevelEntity stock = stockForUpdate(sku);
        final String key = reservationKey(reservationId, sku);
        final StockReservationEntity existing = stockReservationEntityRepository.findById(key).orElse(null);
        if (existing != null) {

            if (existing.getQuantity() != quantity) {

                throw new IllegalStateException(
                        "Reservation identity reused with a different quantity: " + reservationId + " / " + sku);
            }
            return toDomain(stock);
        }
        final int available = stock.getQuantityOnHand() - stock.getQuantityReserved();
        if (available < quantity) {

            throw new InsufficientStockException(
                    "Cannot reserve " + quantity + " unit(s) of SKU " + sku + ": only " + available + " available");
        }
        stock.setQuantityReserved(stock.getQuantityReserved() + quantity);
        final StockLevelEntity saved = stockLevelEntityRepository.saveAndFlush(stock);
        stockReservationEntityRepository.save(
                StockReservationEntity.builder()
                        .reservationKey(key)
                        .reservationId(reservationId)
                        .sku(sku)
                        .quantity(quantity)
                        .status(StockReservationStatus.RESERVED)
                        .build());
        return toDomain(saved);
    }

    @Override
    public StockLevel releaseStock(final String reservationId, final String sku) {

        final StockLevelEntity stock = stockForUpdate(sku);
        final StockReservationEntity reservation = stockReservationEntityRepository.findById(reservationKey(reservationId, sku))
                .orElse(null);
        if (reservation == null || reservation.getStatus() == StockReservationStatus.RELEASED) {

            return toDomain(stock);
        }
        if (stock.getQuantityReserved() < reservation.getQuantity()) {

            throw new IllegalStateException("Stock reservation ledger exceeds aggregate reserved quantity for SKU " + sku);
        }
        stock.setQuantityReserved(stock.getQuantityReserved() - reservation.getQuantity());
        final StockLevelEntity saved = stockLevelEntityRepository.saveAndFlush(stock);
        reservation.setStatus(StockReservationStatus.RELEASED);
        stockReservationEntityRepository.save(reservation);
        return toDomain(saved);
    }

    @Override
    public StockLevel fulfillStock(final String reservationId, final String sku) {

        final StockLevelEntity stock = stockForUpdate(sku);
        final StockReservationEntity reservation = stockReservationEntityRepository.findById(reservationKey(reservationId, sku))
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Stock reservation not found for fulfillment: " + reservationId + " / " + sku));
        if (reservation.getStatus() == StockReservationStatus.FULFILLED) {
            return toDomain(stock);
        }
        if (reservation.getStatus() != StockReservationStatus.RESERVED) {
            throw new IllegalStateException("Only RESERVED stock can be fulfilled: " + reservationId + " / " + sku);
        }
        if (stock.getQuantityReserved() < reservation.getQuantity() || stock.getQuantityOnHand() < reservation.getQuantity()) {
            throw new IllegalStateException("Stock aggregate cannot fulfill reservation for SKU " + sku);
        }
        stock.setQuantityReserved(stock.getQuantityReserved() - reservation.getQuantity());
        stock.setQuantityOnHand(stock.getQuantityOnHand() - reservation.getQuantity());
        final StockLevelEntity saved = stockLevelEntityRepository.saveAndFlush(stock);
        reservation.setStatus(StockReservationStatus.FULFILLED);
        stockReservationEntityRepository.save(reservation);
        return toDomain(saved);
    }

    private StockLevelEntity stockForUpdate(final String sku) {

        return stockLevelEntityRepository.findBySkuForUpdate(sku)
                .orElseGet(() -> StockLevelEntity.builder().sku(sku).quantityOnHand(0).quantityReserved(0).version(0).build());
    }

    private static String reservationKey(final String reservationId, final String sku) {

        return reservationId + ":" + sku;
    }

    private static StockLevel toDomain(final StockLevelEntity stock) {

        return StockLevel.builder()
                .sku(stock.getSku())
                .quantityOnHand(stock.getQuantityOnHand())
                .quantityReserved(stock.getQuantityReserved())
                .version(stock.getVersion())
                .build();
    }
}
