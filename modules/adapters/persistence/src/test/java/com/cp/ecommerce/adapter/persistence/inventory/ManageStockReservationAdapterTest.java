package com.cp.ecommerce.adapter.persistence.inventory;

import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.inventory.entity.StockLevelEntity;
import com.cp.ecommerce.adapter.persistence.inventory.entity.StockLevelEntityRepository;
import com.cp.ecommerce.adapter.persistence.inventory.entity.StockReservationEntity;
import com.cp.ecommerce.adapter.persistence.inventory.entity.StockReservationEntityRepository;
import com.cp.ecommerce.adapter.persistence.inventory.entity.StockReservationStatus;
import com.cp.ecommerce.domain.inventory.StockLevel;
import com.cp.ecommerce.foundation.exception.InsufficientStockException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManageStockReservationAdapterTest {

    private static final String RESERVATION_ID = "RES-1";
    private static final String SKU = "SKU-1";

    private static final String RESERVATION_KEY = "RES-1:SKU-1";

    @Mock
    private transient StockLevelEntityRepository stockLevelEntityRepository;

    @Mock
    private transient StockReservationEntityRepository stockReservationEntityRepository;

    private transient ManageStockReservationAdapter adapter;

    @BeforeEach
    void setUp() {

        adapter = new ManageStockReservationAdapter(stockLevelEntityRepository, stockReservationEntityRepository);
    }

    @Test
    void shouldReserveAndPersistLedgerAtomically() {

        final StockLevelEntity stock = stock(10, 2);
        given(stockLevelEntityRepository.findBySkuForUpdate(SKU)).willReturn(Optional.of(stock));
        given(stockReservationEntityRepository.findById(RESERVATION_KEY)).willReturn(Optional.empty());
        given(stockLevelEntityRepository.saveAndFlush(stock)).willReturn(stock);

        final StockLevel result = adapter.reserveStock(RESERVATION_ID, SKU, 3);

        assertThat(result.getQuantityReserved()).isEqualTo(5);
        verify(stockReservationEntityRepository).save(any(StockReservationEntity.class));
    }

    @Test
    void shouldReplayExistingReservationWithoutDoubleReserve() {

        final StockLevelEntity stock = stock(10, 3);
        final StockReservationEntity reservation = reservation(3, StockReservationStatus.RESERVED);
        given(stockLevelEntityRepository.findBySkuForUpdate(SKU)).willReturn(Optional.of(stock));
        given(stockReservationEntityRepository.findById(RESERVATION_KEY)).willReturn(Optional.of(reservation));

        final StockLevel result = adapter.reserveStock(RESERVATION_ID, SKU, 3);

        assertThat(result.getQuantityReserved()).isEqualTo(3);
        verify(stockLevelEntityRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldNotResurrectReleasedReservation() {

        final StockLevelEntity stock = stock(10, 0);
        final StockReservationEntity reservation = reservation(3, StockReservationStatus.RELEASED);
        given(stockLevelEntityRepository.findBySkuForUpdate(SKU)).willReturn(Optional.of(stock));
        given(stockReservationEntityRepository.findById(RESERVATION_KEY)).willReturn(Optional.of(reservation));

        assertThat(adapter.reserveStock(RESERVATION_ID, SKU, 3).getQuantityReserved()).isZero();
        verify(stockLevelEntityRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectReservationIdentityReusedWithDifferentQuantity() {

        final StockLevelEntity stock = stock(10, 3);
        given(stockLevelEntityRepository.findBySkuForUpdate(SKU)).willReturn(Optional.of(stock));
        given(stockReservationEntityRepository.findById(RESERVATION_KEY))
                .willReturn(Optional.of(reservation(3, StockReservationStatus.RESERVED)));

        assertThatThrownBy(() -> adapter.reserveStock(RESERVATION_ID, SKU, 4)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different quantity");
    }

    @Test
    void shouldRejectReserveWhenAvailableStockIsInsufficientIncludingMissingSku() {

        given(stockLevelEntityRepository.findBySkuForUpdate(SKU)).willReturn(Optional.empty());
        given(stockReservationEntityRepository.findById(RESERVATION_KEY)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.reserveStock(RESERVATION_ID, SKU, 1)).isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void shouldReturnZeroStockWhenReleasingUnknownReservationForMissingSku() {

        given(stockLevelEntityRepository.findBySkuForUpdate(SKU)).willReturn(Optional.empty());
        given(stockReservationEntityRepository.findById(RESERVATION_KEY)).willReturn(Optional.empty());

        final StockLevel result = adapter.releaseStock(RESERVATION_ID, SKU);

        assertThat(result.getQuantityOnHand()).isZero();
        assertThat(result.getQuantityReserved()).isZero();
        verify(stockLevelEntityRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldReleaseOwnedReservationOnce() {

        final StockLevelEntity stock = stock(10, 7);
        final StockReservationEntity reservation = reservation(3, StockReservationStatus.RESERVED);
        given(stockLevelEntityRepository.findBySkuForUpdate(SKU)).willReturn(Optional.of(stock));
        given(stockReservationEntityRepository.findById(RESERVATION_KEY)).willReturn(Optional.of(reservation));
        given(stockLevelEntityRepository.saveAndFlush(stock)).willReturn(stock);

        final StockLevel result = adapter.releaseStock(RESERVATION_ID, SKU);

        assertThat(result.getQuantityReserved()).isEqualTo(4);
        assertThat(reservation.getStatus()).isEqualTo(StockReservationStatus.RELEASED);
        verify(stockReservationEntityRepository).save(reservation);
    }

    @Test
    void shouldNoOpWhenReservationDoesNotExist() {

        final StockLevelEntity stock = stock(10, 4);
        given(stockLevelEntityRepository.findBySkuForUpdate(SKU)).willReturn(Optional.of(stock));
        given(stockReservationEntityRepository.findById(RESERVATION_KEY)).willReturn(Optional.empty());

        assertThat(adapter.releaseStock(RESERVATION_ID, SKU).getQuantityReserved()).isEqualTo(4);
        verify(stockLevelEntityRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldNoOpWhenReservationWasAlreadyReleased() {

        final StockLevelEntity stock = stock(10, 4);
        given(stockLevelEntityRepository.findBySkuForUpdate(SKU)).willReturn(Optional.of(stock));
        given(stockReservationEntityRepository.findById(RESERVATION_KEY))
                .willReturn(Optional.of(reservation(3, StockReservationStatus.RELEASED)));

        assertThat(adapter.releaseStock(RESERVATION_ID, SKU).getQuantityReserved()).isEqualTo(4);
        verify(stockLevelEntityRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldFulfillOwnedReservationOnce() {

        final StockLevelEntity stock = stock(10, 5);
        final StockReservationEntity reservation = reservation(3, StockReservationStatus.RESERVED);
        given(stockLevelEntityRepository.findBySkuForUpdate(SKU)).willReturn(Optional.of(stock));
        given(stockReservationEntityRepository.findById(RESERVATION_KEY)).willReturn(Optional.of(reservation));
        given(stockLevelEntityRepository.saveAndFlush(stock)).willReturn(stock);

        final StockLevel result = adapter.fulfillStock(RESERVATION_ID, SKU);

        assertThat(result.getQuantityOnHand()).isEqualTo(7);
        assertThat(result.getQuantityReserved()).isEqualTo(2);
        assertThat(reservation.getStatus()).isEqualTo(StockReservationStatus.FULFILLED);
        verify(stockReservationEntityRepository).save(reservation);
    }

    @Test
    void shouldReplayAlreadyFulfilledReservationWithoutDoubleConsumption() {

        final StockLevelEntity stock = stock(7, 2);
        given(stockLevelEntityRepository.findBySkuForUpdate(SKU)).willReturn(Optional.of(stock));
        given(stockReservationEntityRepository.findById(RESERVATION_KEY))
                .willReturn(Optional.of(reservation(3, StockReservationStatus.FULFILLED)));

        final StockLevel result = adapter.fulfillStock(RESERVATION_ID, SKU);

        assertThat(result.getQuantityOnHand()).isEqualTo(7);
        assertThat(result.getQuantityReserved()).isEqualTo(2);
        verify(stockLevelEntityRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectFulfillmentWhenReservationDoesNotExist() {

        given(stockLevelEntityRepository.findBySkuForUpdate(SKU)).willReturn(Optional.of(stock(10, 3)));
        given(stockReservationEntityRepository.findById(RESERVATION_KEY)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.fulfillStock(RESERVATION_ID, SKU)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found for fulfillment");
    }

    @Test
    void shouldRejectFulfillmentWhenReservationIsNotReserved() {

        given(stockLevelEntityRepository.findBySkuForUpdate(SKU)).willReturn(Optional.of(stock(10, 3)));
        given(stockReservationEntityRepository.findById(RESERVATION_KEY))
                .willReturn(Optional.of(reservation(3, StockReservationStatus.RELEASED)));

        assertThatThrownBy(() -> adapter.fulfillStock(RESERVATION_ID, SKU)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only RESERVED");
    }

    @Test
    void shouldRejectFulfillmentWhenAggregateReservedQuantityIsTooSmall() {

        given(stockLevelEntityRepository.findBySkuForUpdate(SKU)).willReturn(Optional.of(stock(10, 2)));
        given(stockReservationEntityRepository.findById(RESERVATION_KEY))
                .willReturn(Optional.of(reservation(3, StockReservationStatus.RESERVED)));

        assertThatThrownBy(() -> adapter.fulfillStock(RESERVATION_ID, SKU)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot fulfill reservation");
    }

    @Test
    void shouldRejectFulfillmentWhenOnHandQuantityIsTooSmall() {

        given(stockLevelEntityRepository.findBySkuForUpdate(SKU)).willReturn(Optional.of(stock(2, 3)));
        given(stockReservationEntityRepository.findById(RESERVATION_KEY))
                .willReturn(Optional.of(reservation(3, StockReservationStatus.RESERVED)));

        assertThatThrownBy(() -> adapter.fulfillStock(RESERVATION_ID, SKU)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot fulfill reservation");
    }

    @Test
    void shouldRejectLedgerThatExceedsAggregateReservation() {

        final StockLevelEntity stock = stock(10, 2);
        given(stockLevelEntityRepository.findBySkuForUpdate(SKU)).willReturn(Optional.of(stock));
        given(stockReservationEntityRepository.findById(RESERVATION_KEY))
                .willReturn(Optional.of(reservation(3, StockReservationStatus.RESERVED)));

        assertThatThrownBy(() -> adapter.releaseStock(RESERVATION_ID, SKU)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeds aggregate");
    }

    private static StockLevelEntity stock(final int onHand, final int reserved) {

        return StockLevelEntity.builder().sku(SKU).quantityOnHand(onHand).quantityReserved(reserved).version(1).build();
    }

    private static StockReservationEntity reservation(final int quantity, final StockReservationStatus status) {

        return StockReservationEntity.builder()
                .reservationKey(RESERVATION_KEY)
                .reservationId(RESERVATION_ID)
                .sku(SKU)
                .quantity(quantity)
                .status(status)
                .build();
    }
}
