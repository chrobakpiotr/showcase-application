package com.cp.ecommerce.domain.inventory.usecase;

import com.cp.ecommerce.adapter.common.exception.InsufficientStockException;
import com.cp.ecommerce.adapter.common.exception.StockLevelConflictException;
import com.cp.ecommerce.domain.inventory.StockLevel;
import com.cp.ecommerce.domain.inventory.port.outgoing.FindStockLevelOutPort;
import com.cp.ecommerce.domain.inventory.port.outgoing.SaveStockLevelOutPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private transient SaveStockLevelOutPort saveStockLevelOutPort;

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

        final StockLevel existing = StockLevel.builder().sku(SKU).quantityOnHand(10).quantityReserved(2).version(5).build();
        given(findStockLevelOutPort.find(SKU)).willReturn(existing);

        final StockLevel result = manageStockUseCase.getStockLevel(SKU);

        assertThat(result).isSameAs(existing);
    }

    @Test
    void shouldCreateNewStockLevelOnFirstReceive() {

        given(findStockLevelOutPort.find(SKU)).willReturn(null);
        given(saveStockLevelOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final StockLevel result = manageStockUseCase.receiveStock(SKU, 15);

        assertThat(result.getQuantityOnHand()).isEqualTo(15);
        assertThat(result.getQuantityReserved()).isZero();
        assertThat(result.getVersion()).isZero();
    }

    @Test
    void shouldIncreaseOnHandQuantityOnReceive() {

        final StockLevel existing = StockLevel.builder().sku(SKU).quantityOnHand(10).quantityReserved(3).version(2).build();
        given(findStockLevelOutPort.find(SKU)).willReturn(existing);
        given(saveStockLevelOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final StockLevel result = manageStockUseCase.receiveStock(SKU, 5);

        assertThat(result.getQuantityOnHand()).isEqualTo(15);
        assertThat(result.getQuantityReserved()).isEqualTo(3);
        assertThat(result.getVersion()).isEqualTo(2);
    }

    @Test
    void shouldReserveStockWhenEnoughAvailable() {

        final StockLevel existing = StockLevel.builder().sku(SKU).quantityOnHand(10).quantityReserved(2).version(1).build();
        given(findStockLevelOutPort.find(SKU)).willReturn(existing);
        given(saveStockLevelOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final StockLevel result = manageStockUseCase.reserveStock(SKU, 5);

        assertThat(result.getQuantityReserved()).isEqualTo(7);
        assertThat(result.getQuantityOnHand()).isEqualTo(10);
    }

    @Test
    void shouldThrowInsufficientStockExceptionWhenReservingMoreThanAvailable() {

        final StockLevel existing = StockLevel.builder().sku(SKU).quantityOnHand(10).quantityReserved(8).version(1).build();
        given(findStockLevelOutPort.find(SKU)).willReturn(existing);

        assertThatThrownBy(() -> manageStockUseCase.reserveStock(SKU, 5)).isInstanceOf(InsufficientStockException.class);
        verify(saveStockLevelOutPort, never()).save(any());
    }

    @Test
    void shouldReleaseReservedStock() {

        final StockLevel existing = StockLevel.builder().sku(SKU).quantityOnHand(10).quantityReserved(5).version(1).build();
        given(findStockLevelOutPort.find(SKU)).willReturn(existing);
        given(saveStockLevelOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final StockLevel result = manageStockUseCase.releaseStock(SKU, 3);

        assertThat(result.getQuantityReserved()).isEqualTo(2);
    }

    @Test
    void shouldClampReleasedReservationToZeroWhenReleasingMoreThanReserved() {

        final StockLevel existing = StockLevel.builder().sku(SKU).quantityOnHand(10).quantityReserved(2).version(1).build();
        given(findStockLevelOutPort.find(SKU)).willReturn(existing);
        given(saveStockLevelOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final StockLevel result = manageStockUseCase.releaseStock(SKU, 10);

        assertThat(result.getQuantityReserved()).isZero();
    }

    @Test
    void shouldFulfillReservedStockDecreasingBothOnHandAndReserved() {

        final StockLevel existing = StockLevel.builder().sku(SKU).quantityOnHand(10).quantityReserved(5).version(1).build();
        given(findStockLevelOutPort.find(SKU)).willReturn(existing);
        given(saveStockLevelOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final StockLevel result = manageStockUseCase.fulfillStock(SKU, 4);

        assertThat(result.getQuantityOnHand()).isEqualTo(6);
        assertThat(result.getQuantityReserved()).isEqualTo(1);
    }

    @Test
    void shouldThrowInsufficientStockExceptionWhenFulfillingMoreThanReserved() {

        final StockLevel existing = StockLevel.builder().sku(SKU).quantityOnHand(10).quantityReserved(2).version(1).build();
        given(findStockLevelOutPort.find(SKU)).willReturn(existing);

        assertThatThrownBy(() -> manageStockUseCase.fulfillStock(SKU, 5)).isInstanceOf(InsufficientStockException.class);
        verify(saveStockLevelOutPort, never()).save(any());
    }

    @Test
    void shouldRetryOnConflictAndSucceedOnceCurrentStateAllowsIt() {

        final StockLevel existing = StockLevel.builder().sku(SKU).quantityOnHand(10).quantityReserved(2).version(1).build();
        given(findStockLevelOutPort.find(SKU)).willReturn(existing);
        given(saveStockLevelOutPort.save(any()))
                .willThrow(new StockLevelConflictException(SKU, new IllegalStateException("stale version")))
                .willAnswer(invocation -> invocation.getArgument(0));

        final StockLevel result = manageStockUseCase.receiveStock(SKU, 5);

        assertThat(result.getQuantityOnHand()).isEqualTo(15);
        verify(saveStockLevelOutPort, times(2)).save(any());
    }

    @Test
    void shouldGiveUpAfterExhaustingRetryAttempts() {

        final StockLevel existing = StockLevel.builder().sku(SKU).quantityOnHand(10).quantityReserved(2).version(1).build();
        given(findStockLevelOutPort.find(SKU)).willReturn(existing);
        given(saveStockLevelOutPort.save(any()))
                .willThrow(new StockLevelConflictException(SKU, new IllegalStateException("stale version")));

        assertThatThrownBy(() -> manageStockUseCase.receiveStock(SKU, 5)).isInstanceOf(StockLevelConflictException.class);
        verify(saveStockLevelOutPort, times(3)).save(any());
    }

}
