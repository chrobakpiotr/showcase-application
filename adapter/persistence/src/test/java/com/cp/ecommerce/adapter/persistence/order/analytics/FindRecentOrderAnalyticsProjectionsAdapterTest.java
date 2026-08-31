package com.cp.ecommerce.adapter.persistence.order.analytics;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.order.analytics.mapper.OrderAnalyticsProjectionPersistenceMapper;
import com.cp.ecommerce.domain.order.OrderAnalyticsProjection;

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
 * Test class for {@link FindRecentOrderAnalyticsProjectionsAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class FindRecentOrderAnalyticsProjectionsAdapterTest {

    @InjectMocks
    private transient FindRecentOrderAnalyticsProjectionsAdapter findRecentOrderAnalyticsProjectionsAdapter;

    @Mock
    private transient OrderAnalyticsProjectionEntityRepository orderAnalyticsProjectionEntityRepository;

    @Mock
    private transient OrderAnalyticsProjectionPersistenceMapper orderAnalyticsProjectionPersistenceMapper;

    @Test
    void shouldMapPageOfEntitiesToListOfDomainObjects() {

        final OrderAnalyticsProjectionEntity entity = OrderAnalyticsProjectionEntity.builder()
                .id(1L)
                .orderNumber("ORDER-1")
                .customerId(1L)
                .orderPlacedDate(new Date())
                .consumedDate(new Date())
                .build();
        final OrderAnalyticsProjection projection = new OrderAnalyticsProjection(
                "ORDER-1",
                1L,
                entity.getOrderPlacedDate(),
                entity.getConsumedDate());
        final Page<OrderAnalyticsProjectionEntity> page = new PageImpl<>(List.of(entity));
        given(orderAnalyticsProjectionEntityRepository.findAllByOrderByConsumedDateDesc(any(Pageable.class))).willReturn(page);
        given(orderAnalyticsProjectionPersistenceMapper.mapToDomainObject(entity)).willReturn(Optional.of(projection));

        final List<OrderAnalyticsProjection> result = findRecentOrderAnalyticsProjectionsAdapter.findRecent(20);

        assertThat(result).containsExactly(projection);
    }

    @Test
    void shouldRequestPageableSortedByConsumedDateDescending() {

        given(orderAnalyticsProjectionEntityRepository.findAllByOrderByConsumedDateDesc(any(Pageable.class)))
                .willReturn(Page.empty());

        findRecentOrderAnalyticsProjectionsAdapter.findRecent(15);

        final ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderAnalyticsProjectionEntityRepository).findAllByOrderByConsumedDateDesc(pageableCaptor.capture());
        final Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(15);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "consumedDate"));
    }

    @Test
    void shouldThrowExceptionWhenMappingFails() {

        final OrderAnalyticsProjectionEntity entity = OrderAnalyticsProjectionEntity.builder().orderNumber("ORDER-1").build();
        final Page<OrderAnalyticsProjectionEntity> page = new PageImpl<>(List.of(entity));
        given(orderAnalyticsProjectionEntityRepository.findAllByOrderByConsumedDateDesc(any(Pageable.class))).willReturn(page);
        given(orderAnalyticsProjectionPersistenceMapper.mapToDomainObject(entity)).willReturn(Optional.empty());

        assertThatIllegalStateException().isThrownBy(() -> findRecentOrderAnalyticsProjectionsAdapter.findRecent(20));
    }

}
