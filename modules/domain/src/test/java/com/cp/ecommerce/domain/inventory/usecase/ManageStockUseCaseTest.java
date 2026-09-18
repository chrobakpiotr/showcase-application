package com.cp.ecommerce.domain.inventory.usecase;

import java.util.function.UnaryOperator;

import com.cp.ecommerce.adapter.common.exception.InsufficientStockException;
import com.cp.ecommerce.adapter.common.exception.StockLevelConflictException;
import com.cp.ecommerce.domain.inventory.StockLevel;
import com.cp.ecommerce.domain.inventory.port.outgoing.FindStockLevelOutPort;
import com.cp.ecommerce.domain.inventory.port.outgoing.ManageStockReservationOutPort;
import com.cp.ecommerce.domain.inventory.port.outgoing.MutateStockLevelOutPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link ManageStockUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class ManageStockUseCaseTest {

    private static final String SKU = "SKU-1001";

    @Mock
    private transient FindStockLevelOutPort findStockLevelOutPort;

    @Mock
    private transient MutateStockLevelOutPort mutateStockLevelOutPort;

    @Mock
    private transient ManageStockReservationOutPort manageStockReservationOutPort;

    @InjectMocks
    private transient ManageStockUseCase manageStockUseCase;

    @Test
    void shouldReturnZeroStockLevelWhenNeverReceived() {

        given(findStockLevelOutPort.find(SKU)).willReturn(null);

        final StockLevel result = manageStockUseCase.getStockLevel(SKU);

        assertThat(result.getSku()).isEqualTo(SKU);
        assertThat(result.getQuantityOnHand()).isZero();
        assertThat(result.getQuantityReserved()).isZero();
    }

    @Test
    void shouldReturnPersistedStockLevelWhenPresent() {

        final StockLevel existing = stock(10, 2, 5);
        given(findStockLevelOutPort.find(SKU)).willReturn(existing);

        assertThat(manageStockUseCase.getStockLevel(SKU)).isSameAs(existing);
    }

    @Test
    void shouldCreateStockOnFirstReceive() {

        applyMutationAgainst(stock(0, 0, 0));

        final StockLevel result = manageStockUseCase.receiveStock(SKU, 15);

        assertThat(result.getQuantityOnHand()).isEqualTo(15);
        assertThat(result.getQuantityReserved()).isZero();
    }

    @Test
    void shouldIncreaseOnHandQuantity() {

        applyMutationAgainst(stock(10, 3, 2));

        final StockLevel result = manageStockUseCase.receiveStock(SKU, 5);

        assertThat(result.getQuantityOnHand()).isEqualTo(15);
        assertThat(result.getQuantityReserved()).isEqualTo(3);
        assertThat(result.getVersion()).isEqualTo(2);
    }

    @Test
    void shouldReserveWhenFreshStateHasEnoughAvailable() {

        applyMutationAgainst(stock(10, 2, 1));

        final StockLevel result = manageStockUseCase.reserveStock(SKU, 5);

        assertThat(result.getQuantityReserved()).isEqualTo(7);
        assertThat(result.getQuantityOnHand()).isEqualTo(10);
    }

    @Test
    void shouldRejectReserveAgainstFreshInsufficientState() {

        applyMutationAgainst(stock(10, 8, 2));

        assertThatThrownBy(() -> manageStockUseCase.reserveStock(SKU, 5)).isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void shouldReleaseReservedStock() {

        applyMutationAgainst(stock(10, 5, 1));

        assertThat(manageStockUseCase.releaseStock(SKU, 3).getQuantityReserved()).isEqualTo(2);
    }

    @Test
    void shouldClampReleasedReservationToZero() {

        applyMutationAgainst(stock(10, 2, 1));

        assertThat(manageStockUseCase.releaseStock(SKU, 10).getQuantityReserved()).isZero();
    }

    @Test
    void shouldFulfillReservedStock() {

        applyMutationAgainst(stock(10, 5, 1));

        final StockLevel result = manageStockUseCase.fulfillStock(SKU, 4);

        assertThat(result.getQuantityOnHand()).isEqualTo(6);
        assertThat(result.getQuantityReserved()).isEqualTo(1);
    }

    @Test
    void shouldRejectFulfillAgainstFreshInsufficientState() {

        applyMutationAgainst(stock(10, 2, 1));

        assertThatThrownBy(() -> manageStockUseCase.fulfillStock(SKU, 5)).isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void shouldRetryConflictAndApplyMutationToFreshState() {

        final StockLevel freshState = stock(12, 2, 2);
        given(mutateStockLevelOutPort.mutate(eq(SKU), any()))
                .willThrow(new StockLevelConflictException(SKU, new IllegalStateException("stale version")))
                .willAnswer(invocation -> {
                    final UnaryOperator<StockLevel> mutation = invocation.getArgument(1);
                    return mutation.apply(freshState);
                });

        final StockLevel result = manageStockUseCase.receiveStock(SKU, 5);

        assertThat(result.getQuantityOnHand()).isEqualTo(17);
        verify(mutateStockLevelOutPort, times(2)).mutate(eq(SKU), any());
    }

    @Test
    void shouldGiveUpAfterThreeConflicts() {

        given(mutateStockLevelOutPort.mutate(eq(SKU), any()))
                .willThrow(new StockLevelConflictException(SKU, new IllegalStateException("stale version")));

        assertThatThrownBy(() -> manageStockUseCase.receiveStock(SKU, 5)).isInstanceOf(StockLevelConflictException.class);
        verify(mutateStockLevelOutPort, times(3)).mutate(eq(SKU), any());
    }

    @Test
    void shouldDelegateIdentityAwareReserve() {

        final StockLevel expected = stock(10, 3, 1);
        given(manageStockReservationOutPort.reserveStock("RES-1", SKU, 3)).willReturn(expected);

        assertThat(manageStockUseCase.reserveStock("RES-1", SKU, 3)).isSameAs(expected);
        verify(manageStockReservationOutPort).reserveStock("RES-1", SKU, 3);
        verify(mutateStockLevelOutPort, never()).mutate(any(), any());
    }

    @Test
    void shouldDelegateIdentityAwareRelease() {

        final StockLevel expected = stock(10, 0, 2);
        given(manageStockReservationOutPort.releaseStock("RES-1", SKU)).willReturn(expected);

        assertThat(manageStockUseCase.releaseStock("RES-1", SKU)).isSameAs(expected);
        verify(manageStockReservationOutPort).releaseStock("RES-1", SKU);
        verify(mutateStockLevelOutPort, never()).mutate(any(), any());
    }

    private void applyMutationAgainst(final StockLevel current) {

        given(mutateStockLevelOutPort.mutate(eq(SKU), any())).willAnswer(invocation -> {
            final UnaryOperator<StockLevel> mutation = invocation.getArgument(1);
            return mutation.apply(current);
        });
    }

    private static StockLevel stock(final int onHand, final int reserved, final long version) {

        return StockLevel.builder().sku(SKU).quantityOnHand(onHand).quantityReserved(reserved).version(version).build();
    }
}
