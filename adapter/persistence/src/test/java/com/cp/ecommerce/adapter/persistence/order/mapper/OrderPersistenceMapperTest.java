package com.cp.ecommerce.adapter.persistence.order.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.adapter.persistence.customer.mapper.CustomerPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;
import com.cp.ecommerce.adapter.persistence.utils.OrderEntityBuilder;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.TEST_ORDER_NUMBER;
import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.TEST_REMARKS;

/**
 * {@link OrderPersistenceMapper} mapper test.
 */
@ExtendWith(MockitoExtension.class)
class OrderPersistenceMapperTest {

    @Mock
    private transient CustomerPersistenceMapper customerPersistenceMapper;

    @InjectMocks
    private transient OrderPersistenceMapper orderPersistenceMapper;

    @Test
    void shouldMapDomainToEntity() {

        final Order order = OrderBuilder.mockOrder();
        final Optional<OrderEntity> result = orderPersistenceMapper.mapToEntity(order);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isNull();
        assertThat(result.get().getRemarks()).isEqualTo(TEST_REMARKS);
        assertThat(result.get().getOrderNumber()).isEqualTo(TEST_ORDER_NUMBER);
        assertThat(result.get().getCreated()).isNotNull();
        assertThat(result.get().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.get().getItems()).hasSize(1);
        assertThat(result.get().getItems().get(0).getSku()).isEqualTo(OrderBuilder.TEST_ORDER_LINE_ITEM_SKU);
        assertThat(result.get().getItems().get(0).getProductName()).isEqualTo(OrderBuilder.TEST_ORDER_LINE_ITEM_PRODUCT_NAME);
        assertThat(result.get().getItems().get(0).getUnitPrice()).isEqualTo(OrderBuilder.TEST_ORDER_LINE_ITEM_UNIT_PRICE);
        assertThat(result.get().getItems().get(0).getQuantity()).isEqualTo(OrderBuilder.TEST_ORDER_LINE_ITEM_QUANTITY);
    }

    @Test
    void shouldMapEntityToDomain() {

        final OrderEntity entity = OrderEntityBuilder.mockOrderEntity();
        final Optional<Order> result = orderPersistenceMapper.mapToDomainObject(entity);

        assertThat(result).isPresent();
        assertThat(result.get().getRemarks()).isEqualTo(TEST_REMARKS);
        assertThat(result.get().getOrderNumber()).isEqualTo(TEST_ORDER_NUMBER);
        assertThat(result.get().getCreated()).isNotNull();
        assertThat(result.get().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.get().getItems()).hasSize(1);
        assertThat(result.get().getItems().get(0).getSku()).isEqualTo(OrderBuilder.TEST_ORDER_LINE_ITEM_SKU);
    }

    @Test
    void shouldMapToDomainObject() {

        final Optional<Order> obj = orderPersistenceMapper.mapToDomainObject(null);
        assertTrue(obj.isEmpty());
    }

}
