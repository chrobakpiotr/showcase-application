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
import com.cp.ecommerce.domain.inventory.port.outgoing.SaveStockLevelOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for reading and mutating stock levels.
 *
 * <p>
 * Every mutation follows an optimistic "read - compute next state - save" cycle (see ADR 0026): rather than locking a row for
 * the duration of the business decision, it re-reads the current state, recomputes the mutation on top of it, and retries the
 * whole cycle - up to {@link #MAX_ATTEMPTS} times - whenever {@link SaveStockLevelOutPort#save(StockLevel)} reports that
 * another request won the race first ({@link StockLevelConflictException}).
 */
@UseCase
@RequiredArgsConstructor
public class ManageStockUseCase implements GetStockLevelInPort, ManageStockInPort {

    private static final int MAX_ATTEMPTS = 3;

    private final FindStockLevelOutPort findStockLevelOutPort;

    private final SaveStockLevelOutPort saveStockLevelOutPort;

    @Override
    public StockLevel getStockLevel(final String sku) {

        return Optional.ofNullable(findStockLevelOutPort.find(sku))
                .orElseGet(() -> StockLevel.builder().sku(sku).quantityOnHand(0).quantityReserved(0).build());
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

            final StockLevel mutated = mutation.apply(getStockLevel(sku));
            mutated.assertValidationsEmpty();
            try {

                return saveStockLevelOutPort.save(mutated);
            } catch (final StockLevelConflictException conflict) {

                lastConflict = conflict;
            }
        }
        throw lastConflict;
    }

}
