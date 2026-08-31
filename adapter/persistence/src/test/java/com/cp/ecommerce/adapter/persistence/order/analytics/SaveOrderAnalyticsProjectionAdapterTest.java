package com.cp.ecommerce.adapter.persistence.order.analytics;

import java.util.Date;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.order.analytics.mapper.OrderAnalyticsProjectionPersistenceMapper;
import com.cp.ecommerce.domain.order.OrderAnalyticsProjection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Test class for {@link SaveOrderAnalyticsProjectionAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class SaveOrderAnalyticsProjectionAdapterTest {

    @InjectMocks
    private transient SaveOrderAnalyticsProjectionAdapter saveOrderAnalyticsProjectionAdapter;

    @Mock
    private transient OrderAnalyticsProjectionEntityRepository orderAnalyticsProjectionEntityRepository;

    @Mock
    private transient OrderAnalyticsProjectionPersistenceMapper orderAnalyticsProjectionPersistenceMapper;

    @Test
    void shouldMapAndSaveProjection() {

        final OrderAnalyticsProjection projection = new OrderAnalyticsProjection("ORDER-1", 1L, new Date(), new Date());
        final OrderAnalyticsProjectionEntity entity = OrderAnalyticsProjectionEntity.builder().orderNumber("ORDER-1").build();
        given(orderAnalyticsProjectionPersistenceMapper.mapToEntity(projection)).willReturn(Optional.of(entity));

        saveOrderAnalyticsProjectionAdapter.save(projection);

        final ArgumentCaptor<OrderAnalyticsProjectionEntity> captor = ArgumentCaptor
                .forClass(OrderAnalyticsProjectionEntity.class);
        then(orderAnalyticsProjectionEntityRepository).should().save(captor.capture());
        assertThat(captor.getValue()).isSameAs(entity);
    }

    @Test
    void shouldNotSaveWhenMappingFails() {

        final OrderAnalyticsProjection projection = new OrderAnalyticsProjection("ORDER-1", 1L, new Date(), new Date());
        given(orderAnalyticsProjectionPersistenceMapper.mapToEntity(projection)).willReturn(Optional.empty());

        saveOrderAnalyticsProjectionAdapter.save(projection);

        then(orderAnalyticsProjectionEntityRepository).should(never()).save(any());
    }

}
