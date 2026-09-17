package com.cp.ecommerce.domain.order.usecase;

import java.util.List;

import com.cp.ecommerce.domain.order.DuplicateOrderCheckResult;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.DetectDuplicateOrderOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.FindRecentOrdersByCustomerOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static java.util.Collections.emptyList;

/**
 * Tests for {@link DetectDuplicateOrderUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class DetectDuplicateOrderUseCaseTest {

    @Mock
    private transient FindRecentOrdersByCustomerOutPort findRecentOrdersByCustomerOutPort;

    @Mock
    private transient DetectDuplicateOrderOutPort detectDuplicateOrderOutPort;

    @InjectMocks
    private transient DetectDuplicateOrderUseCase detectDuplicateOrderUseCase;

    @Test
    void shouldShortCircuitToNoneWithoutCallingAiPortWhenNoRecentOrdersExist() {

        final Order order = TestDomainObjectFactory.validOrder();
        when(findRecentOrdersByCustomerOutPort.findRecentOrders(order)).thenReturn(emptyList());

        final DuplicateOrderCheckResult actual = detectDuplicateOrderUseCase.detectDuplicate(order);

        assertThat(actual).isEqualTo(DuplicateOrderCheckResult.none());
        verify(detectDuplicateOrderOutPort, never()).check(any(), any());
    }

    @Test
    void shouldDelegateToAiPortWithCandidatesWhenRecentOrdersExist() {

        final Order order = TestDomainObjectFactory.validOrder();
        final Order recentOrder = Order.builder()
                .remarks("remark")
                .orderNumber("ORD-1002")
                .created(TestDomainObjectFactory.TEST_CREATED)
                .customer(TestDomainObjectFactory.validCustomer())
                .build();
        final List<Order> recentOrders = List.of(recentOrder);
        final DuplicateOrderCheckResult expected = DuplicateOrderCheckResult.builder()
                .duplicate(true)
                .matchedOrderNumber("ORD-1002")
                .similarityScore(0.98)
                .rationale("Near-identical remarks submitted 30 seconds apart")
                .build();
        when(findRecentOrdersByCustomerOutPort.findRecentOrders(order)).thenReturn(recentOrders);
        when(detectDuplicateOrderOutPort.check(order, recentOrders)).thenReturn(expected);

        final DuplicateOrderCheckResult actual = detectDuplicateOrderUseCase.detectDuplicate(order);

        assertThat(actual).isEqualTo(expected);
    }

}
