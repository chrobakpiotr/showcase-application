package com.cp.ecommerce.domain.inventory.usecase;

import java.util.function.UnaryOperator;

import com.cp.ecommerce.domain.inventory.StockLevel;
import com.cp.ecommerce.domain.inventory.port.outgoing.FindStockLevelOutPort;
import com.cp.ecommerce.domain.inventory.port.outgoing.ManageStockReservationOutPort;
import com.cp.ecommerce.domain.inventory.port.outgoing.MutateStockLevelOutPort;
import com.cp.ecommerce.foundation.exception.InsufficientStockException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManageStockUseCaseMutationWave2Test {

    private static final String SKU = "SKU-1";

    @Mock
    private FindStockLevelOutPort findStockLevelOutPort;
    @Mock
    private MutateStockLevelOutPort mutateStockLevelOutPort;
    @Mock
    private ManageStockReservationOutPort manageStockReservationOutPort;

    private ManageStockUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ManageStockUseCase(findStockLevelOutPort, mutateStockLevelOutPort, manageStockReservationOutPort);
    }

    @Test
    void shouldAllowReservationAtExactAvailableBoundaryAndRejectOneMore() {
        applyAgainst(stock(5, 2, 4));

        assertThat(useCase.reserveStock(SKU, 3).getQuantityReserved()).isEqualTo(5);
        assertThatThrownBy(() -> useCase.reserveStock(SKU, 4)).isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void shouldAllowFulfillmentAtExactReservedBoundaryAndRejectOneMore() {
        applyAgainst(stock(8, 3, 4));

        final StockLevel fulfilled = useCase.fulfillStock(SKU, 3);
        assertThat(fulfilled.getQuantityOnHand()).isEqualTo(5);
        assertThat(fulfilled.getQuantityReserved()).isZero();

        assertThatThrownBy(() -> useCase.fulfillStock(SKU, 4)).isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void shouldDelegateIdentityAwareFulfillAndReturnExactResult() {
        final StockLevel expected = stock(7, 0, 5);
        given(manageStockReservationOutPort.fulfillStock("RES-1", SKU)).willReturn(expected);

        assertThat(useCase.fulfillStock("RES-1", SKU)).isSameAs(expected);
        verify(manageStockReservationOutPort).fulfillStock("RES-1", SKU);
    }

    private void applyAgainst(final StockLevel current) {
        given(mutateStockLevelOutPort.mutate(eq(SKU), any())).willAnswer(invocation -> {
            final UnaryOperator<StockLevel> mutation = invocation.getArgument(1);
            return mutation.apply(current);
        });
    }

    private static StockLevel stock(final int onHand, final int reserved, final long version) {
        return StockLevel.builder().sku(SKU).quantityOnHand(onHand).quantityReserved(reserved).version(version).build();
    }
}
