package com.cp.ecommerce.adapter.persistence.order;

import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.CustomerBuilder;
import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.mapper.OrderPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.OrderEntityBuilder;
import com.cp.ecommerce.domain.order.Order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Test class for {@link FindRecentOrdersByCustomerAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class FindRecentOrdersByCustomerAdapterTest {

    @InjectMocks
    private transient FindRecentOrdersByCustomerAdapter findRecentOrdersByCustomerAdapter;

    @Mock
    private transient OrderEntityRepository orderEntityRepository;

    @Mock
    private transient OrderPersistenceMapper orderPersistenceMapper;

    @Test
    void shouldQueryByCustomerEmailAndExcludeTheOrderItself() {

        final Order order = OrderBuilder.mockOrder();
        given(
                orderEntityRepository.findTop5ByCustomerEmailAndCreatedAfterAndOrderNumberNotOrderByCreatedDesc(
                        anyString(),
                        any(),
                        anyString()))
                .willReturn(List.of());

        findRecentOrdersByCustomerAdapter.findRecentOrders(order);

        final ArgumentCaptor<String> orderNumberCaptor = ArgumentCaptor.forClass(String.class);
        verify(orderEntityRepository).findTop5ByCustomerEmailAndCreatedAfterAndOrderNumberNotOrderByCreatedDesc(
                eq(CustomerBuilder.TEST_EMAIL),
                any(),
                orderNumberCaptor.capture());
        assertThat(orderNumberCaptor.getValue()).isEqualTo(order.getOrderNumber());
    }

    @Test
    void shouldMapEntitiesToDomainObjects() {

        final Order order = OrderBuilder.mockOrder();
        final OrderEntity candidateEntity = OrderEntityBuilder.mockOrderEntity();
        final Order candidateOrder = OrderBuilder.mockOrder();
        given(
                orderEntityRepository.findTop5ByCustomerEmailAndCreatedAfterAndOrderNumberNotOrderByCreatedDesc(
                        anyString(),
                        any(),
                        anyString()))
                .willReturn(List.of(candidateEntity));
        given(orderPersistenceMapper.mapToDomainObject(candidateEntity)).willReturn(Optional.of(candidateOrder));

        final List<Order> result = findRecentOrdersByCustomerAdapter.findRecentOrders(order);

        assertThat(result).containsExactly(candidateOrder);
    }

    @Test
    void shouldThrowExceptionWhenMappingFails() {

        final Order order = OrderBuilder.mockOrder();
        final OrderEntity candidateEntity = OrderEntityBuilder.mockOrderEntity();
        given(
                orderEntityRepository.findTop5ByCustomerEmailAndCreatedAfterAndOrderNumberNotOrderByCreatedDesc(
                        anyString(),
                        any(),
                        anyString()))
                .willReturn(List.of(candidateEntity));
        given(orderPersistenceMapper.mapToDomainObject(candidateEntity)).willReturn(Optional.empty());

        assertThatIllegalStateException().isThrownBy(() -> findRecentOrdersByCustomerAdapter.findRecentOrders(order));
    }

}
