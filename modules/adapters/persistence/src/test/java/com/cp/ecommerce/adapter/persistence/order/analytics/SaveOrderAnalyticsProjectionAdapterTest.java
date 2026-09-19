package com.cp.ecommerce.adapter.persistence.order.analytics;

import java.time.Instant;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.order.analytics.mapper.OrderAnalyticsProjectionPersistenceMapper;
import com.cp.ecommerce.domain.order.OrderAnalyticsProjection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

/**
 * Test class for {@link SaveOrderAnalyticsProjectionAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class SaveOrderAnalyticsProjectionAdapterTest {

    private static final String ORDER_NUMBER = "ORDER-1";

    @InjectMocks
    private transient SaveOrderAnalyticsProjectionAdapter saveOrderAnalyticsProjectionAdapter;

    @Mock
    private transient OrderAnalyticsProjectionEntityRepository orderAnalyticsProjectionEntityRepository;

    @Mock
    private transient OrderAnalyticsProjectionPersistenceMapper orderAnalyticsProjectionPersistenceMapper;

    @Test
    void shouldMapAndSaveProjection() {

        final OrderAnalyticsProjection projection = new OrderAnalyticsProjection(
                ORDER_NUMBER,
                1L,
                Instant.ofEpochMilli(Instant.now().toEpochMilli()),
                Instant.ofEpochMilli(Instant.now().toEpochMilli()));
        final OrderAnalyticsProjectionEntity entity = OrderAnalyticsProjectionEntity.builder()
                .orderNumber(ORDER_NUMBER)
                .build();
        given(orderAnalyticsProjectionPersistenceMapper.mapToEntity(projection)).willReturn(Optional.of(entity));

        saveOrderAnalyticsProjectionAdapter.save(projection);

        final ArgumentCaptor<OrderAnalyticsProjectionEntity> captor = ArgumentCaptor
                .forClass(OrderAnalyticsProjectionEntity.class);
        then(orderAnalyticsProjectionEntityRepository).should().saveAndFlush(captor.capture());
        assertThat(captor.getValue()).isSameAs(entity);
    }

    @Test
    void shouldNotSaveWhenMappingFails() {

        final OrderAnalyticsProjection projection = new OrderAnalyticsProjection(
                ORDER_NUMBER,
                1L,
                Instant.ofEpochMilli(Instant.now().toEpochMilli()),
                Instant.ofEpochMilli(Instant.now().toEpochMilli()));
        given(orderAnalyticsProjectionPersistenceMapper.mapToEntity(projection)).willReturn(Optional.empty());

        saveOrderAnalyticsProjectionAdapter.save(projection);

        then(orderAnalyticsProjectionEntityRepository).should(never()).saveAndFlush(any());
    }

    @Test
    void shouldIgnoreDuplicateRedeliveryInsteadOfPropagatingException() {

        final OrderAnalyticsProjection projection = new OrderAnalyticsProjection(
                ORDER_NUMBER,
                1L,
                Instant.ofEpochMilli(Instant.now().toEpochMilli()),
                Instant.ofEpochMilli(Instant.now().toEpochMilli()));
        final OrderAnalyticsProjectionEntity entity = OrderAnalyticsProjectionEntity.builder()
                .orderNumber(ORDER_NUMBER)
                .build();
        given(orderAnalyticsProjectionPersistenceMapper.mapToEntity(projection)).willReturn(Optional.of(entity));
        willThrow(new DataIntegrityViolationException("duplicate key")).given(orderAnalyticsProjectionEntityRepository)
                .saveAndFlush(entity);

        assertThatCode(() -> saveOrderAnalyticsProjectionAdapter.save(projection)).doesNotThrowAnyException();
    }

}
