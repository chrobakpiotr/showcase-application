package com.cp.ecommerce.adapter.persistence.order;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.mapper.OrderPersistenceMapper;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.PageQuery;
import com.cp.ecommerce.domain.order.PagedResult;
import com.cp.ecommerce.domain.order.port.outgoing.FindOrdersOutPort;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link FindOrdersOutPort}.
 *
 * <p>
 * Translates the domain-owned {@link PageQuery}/{@link PagedResult} types to and from Spring Data's {@link Pageable}/
 * {@link Page}, keeping the persistence-technology types confined to this adapter instead of leaking into the domain port.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindOrdersAdapter implements FindOrdersOutPort {

    private final OrderEntityRepository orderEntityRepository;

    private final OrderPersistenceMapper orderPersistenceMapper;

    @Override
    public PagedResult<Order> findAll(final PageQuery pageQuery) {

        final Pageable pageable = PageRequest.of(pageQuery.page(), pageQuery.size(), Sort.by(Sort.Direction.DESC, "created"));
        final Page<OrderEntity> page = orderEntityRepository.findAll(pageable);
        final var content = page.getContent().stream().map(this::mapToDomainObjectOrThrow).toList();
        return new PagedResult<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private Order mapToDomainObjectOrThrow(final OrderEntity entity) {

        return orderPersistenceMapper.mapToDomainObject(entity)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map order entity to domain object for order number: " + entity.getOrderNumber()));
    }

}
