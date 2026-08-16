package com.cp.ecommerce.adapter.persistence.order.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.PersistenceMapper;
import com.cp.ecommerce.adapter.persistence.customer.mapper.CustomerPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;
import com.cp.ecommerce.domain.order.Order;

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
                        .status(entity.getStatus())
                        .build());
    }

}
