package com.cp.ecommerce.adapter.persistence.order.mapper;

import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.PersistenceMapper;
import com.cp.ecommerce.adapter.persistence.customer.mapper.CustomerPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderLineItemEmbeddable;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderLineItem;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import static java.util.Optional.ofNullable;

/**
 * Mapper responsible for changing {@link Order} object into/from entity object.
 */
@Component
@RequiredArgsConstructor
public class OrderPersistenceMapper implements PersistenceMapper<Order, OrderEntity> {

    private final CustomerPersistenceMapper customerEntityMapper;

    @Override
    public Optional<OrderEntity> mapToEntity(final Order order) {

        return ofNullable(order).map(
                domain -> OrderEntity.builder()
                        .remarks(domain.getRemarks())
                        .orderNumber(domain.getOrderNumber())
                        .created(domain.getCreated())
                        .customer(customerEntityMapper.mapToEntity(order.getCustomer()).orElse(null))
                        .items(domain.getItems().stream().map(this::mapItemToEmbeddable).toList())
                        .status(domain.getStatus())
                        .build());
    }

    @Override
    public Optional<Order> mapToDomainObject(final OrderEntity order) {

        return ofNullable(order).map(
                entity -> Order.builder()
                        .remarks(entity.getRemarks())
                        .orderNumber(entity.getOrderNumber())
                        .created(entity.getCreated())
                        .customer(customerEntityMapper.mapToDomainObject(entity.getCustomer()).orElse(null))
                        .items(mapItemsToDomainObjects(entity.getItems()))
                        .status(entity.getStatus())
                        .build());
    }

    private OrderLineItemEmbeddable mapItemToEmbeddable(final OrderLineItem item) {

        return OrderLineItemEmbeddable.builder()
                .sku(item.getSku())
                .productName(item.getProductName())
                .unitPrice(item.getUnitPrice())
                .quantity(item.getQuantity())
                .build();
    }

    private List<OrderLineItem> mapItemsToDomainObjects(final List<OrderLineItemEmbeddable> items) {

        return items.stream()
                .map(
                        item -> OrderLineItem.builder()
                                .sku(item.getSku())
                                .productName(item.getProductName())
                                .unitPrice(item.getUnitPrice())
                                .quantity(item.getQuantity())
                                .build())
                .toList();
    }

}
