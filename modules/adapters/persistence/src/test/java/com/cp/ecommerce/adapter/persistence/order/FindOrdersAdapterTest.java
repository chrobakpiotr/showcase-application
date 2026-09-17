package com.cp.ecommerce.adapter.persistence.order;

import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.mapper.OrderPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.OrderEntityBuilder;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.PageQuery;
import com.cp.ecommerce.domain.order.PagedResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Test class for {@link FindOrdersAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class FindOrdersAdapterTest {

    @InjectMocks
    private transient FindOrdersAdapter findOrdersAdapter;

    @Mock
    private transient OrderEntityRepository orderEntityRepository;

    @Mock
    private transient OrderPersistenceMapper orderPersistenceMapper;

    @Test
    void shouldMapPageOfEntitiesToPagedResultOfDomainObjects() {

        final OrderEntity entity = OrderEntityBuilder.mockOrderEntity();
        final Order order = OrderBuilder.mockOrder();
        final Page<OrderEntity> page = new PageImpl<>(List.of(entity), Pageable.ofSize(20), 1);
        given(orderEntityRepository.findAll(any(Pageable.class))).willReturn(page);
        given(orderPersistenceMapper.mapToDomainObject(entity)).willReturn(Optional.of(order));

        final PagedResult<Order> result = findOrdersAdapter.findAll(new PageQuery(0, 20));

        assertThat(result.content()).containsExactly(order);
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    void shouldRequestPageableSortedByCreatedDescending() {

        given(orderEntityRepository.findAll(any(Pageable.class))).willReturn(Page.empty());

        findOrdersAdapter.findAll(new PageQuery(2, 10));

        final ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderEntityRepository).findAll(pageableCaptor.capture());
        final Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "created"));
    }

    @Test
    void shouldThrowExceptionWhenMappingFails() {

        final OrderEntity entity = OrderEntityBuilder.mockOrderEntity();
        final Page<OrderEntity> page = new PageImpl<>(List.of(entity));
        given(orderEntityRepository.findAll(any(Pageable.class))).willReturn(page);
        given(orderPersistenceMapper.mapToDomainObject(entity)).willReturn(Optional.empty());

        assertThatIllegalStateException().isThrownBy(() -> findOrdersAdapter.findAll(new PageQuery(0, 20)));
    }

}
