package com.cp.ecommerce.domain.inventory.usecase;

import java.util.Optional;
import java.util.function.UnaryOperator;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.adapter.common.exception.InsufficientStockException;
import com.cp.ecommerce.adapter.common.exception.StockLevelConflictException;
import com.cp.ecommerce.domain.inventory.StockLevel;
import com.cp.ecommerce.domain.inventory.port.incoming.GetStockLevelInPort;
import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.inventory.port.outgoing.FindStockLevelOutPort;
import com.cp.ecommerce.domain.inventory.port.outgoing.ManageStockReservationOutPort;
import com.cp.ecommerce.domain.inventory.port.outgoing.MutateStockLevelOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for reading and mutating stock levels.
 *
 * <p>
 * Generic mutations use a bounded optimistic retry. One call to {@link MutateStockLevelOutPort} is one complete transactional
 * attempt, so a conflict is retried against a fresh persistence context instead of a transaction already marked rollback-only.
 * Identity-aware reservation operations remain delegated to their R02 ledger boundary.
 */
@UseCase
@RequiredArgsConstructor
public class ManageStockUseCase implements GetStockLevelInPort, ManageStockInPort {

    private static final int MAX_ATTEMPTS = 3;

    private final FindStockLevelOutPort findStockLevelOutPort;

    private final MutateStockLevelOutPort mutateStockLevelOutPort;

    private final ManageStockReservationOutPort manageStockReservationOutPort;

    @Override
    public StockLevel getStockLevel(final String sku) {

        return Optional.ofNullable(findStockLevelOutPort.find(sku)).orElseGet(() -> zeroStock(sku));
    }

    @Override
    public StockLevel receiveStock(final String sku, final int quantity) {

        return applyWithRetry(
                sku,
                current -> StockLevel.builder()
                        .sku(sku)
                        .quantityOnHand(current.getQuantityOnHand() + quantity)
                        .quantityReserved(current.getQuantityReserved())
                        .version(current.getVersion())
                        .build());
    }

    @Override
    public StockLevel reserveStock(final String sku, final int quantity) {

        return applyWithRetry(sku, current -> {

            if (current.getQuantityAvailable() < quantity) {

                throw new InsufficientStockException(
                        "Cannot reserve " + quantity + " unit(s) of SKU " + sku + ": only " + current.getQuantityAvailable()
                                + " available");
            }
            return StockLevel.builder()
                    .sku(sku)
                    .quantityOnHand(current.getQuantityOnHand())
                    .quantityReserved(current.getQuantityReserved() + quantity)
                    .version(current.getVersion())
                    .build();
        });
    }

    @Override
    public StockLevel releaseStock(final String sku, final int quantity) {

        return applyWithRetry(
                sku,
                current -> StockLevel.builder()
                        .sku(sku)
                        .quantityOnHand(current.getQuantityOnHand())
                        .quantityReserved(Math.max(0, current.getQuantityReserved() - quantity))
                        .version(current.getVersion())
                        .build());
    }

    @Override
    public StockLevel reserveStock(final String reservationId, final String sku, final int quantity) {

        return manageStockReservationOutPort.reserveStock(reservationId, sku, quantity);
    }

    @Override
    public StockLevel releaseStock(final String reservationId, final String sku) {

        return manageStockReservationOutPort.releaseStock(reservationId, sku);
    }

    @Override
    public StockLevel fulfillStock(final String sku, final int quantity) {

        return applyWithRetry(sku, current -> {

            if (current.getQuantityReserved() < quantity) {

                throw new InsufficientStockException(
                        "Cannot fulfill " + quantity + " unit(s) of SKU " + sku + ": only " + current.getQuantityReserved()
                                + " reserved");
            }
            return StockLevel.builder()
                    .sku(sku)
                    .quantityOnHand(current.getQuantityOnHand() - quantity)
                    .quantityReserved(current.getQuantityReserved() - quantity)
                    .version(current.getVersion())
                    .build();
        });
    }

    private StockLevel applyWithRetry(final String sku, final UnaryOperator<StockLevel> mutation) {

        StockLevelConflictException lastConflict = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {

            try {

                return mutateStockLevelOutPort.mutate(sku, mutation);
            } catch (final StockLevelConflictException conflict) {

                lastConflict = conflict;
            }
        }
        throw lastConflict;
    }

    private static StockLevel zeroStock(final String sku) {

        return StockLevel.builder().sku(sku).quantityOnHand(0).quantityReserved(0).build();
    }
}
