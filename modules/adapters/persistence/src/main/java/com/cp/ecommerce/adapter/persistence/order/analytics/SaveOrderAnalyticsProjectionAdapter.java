package com.cp.ecommerce.adapter.persistence.order.analytics;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.order.analytics.mapper.OrderAnalyticsProjectionPersistenceMapper;
import com.cp.ecommerce.domain.order.OrderAnalyticsProjection;
import com.cp.ecommerce.domain.order.port.outgoing.SaveOrderAnalyticsProjectionOutPort;

import org.springframework.dao.DataIntegrityViolationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link SaveOrderAnalyticsProjectionOutPort}.
 *
 * <p>
 * {@code OrderAnalyticsEventConsumer} is an at-least-once Kafka consumer (redelivery happens on rebalance, container restart,
 * or the {@code DefaultErrorHandler}'s own retry-then-recover cycle), so the same order-analytics event can legitimately be
 * consumed more than once. Rather than deduplicating in application code, this mirrors {@code IdempotencyKeyAdapter}'s
 * approach: attempt the insert and let the database's own unique constraint on {@code ORDER_ANALYTICS_PROJECTION.ORDER_NUMBER}
 * arbitrate, catching {@link DataIntegrityViolationException} and treating it as "already recorded" rather than an error.
 */
@PersistenceAdapter
@Slf4j
@RequiredArgsConstructor
class SaveOrderAnalyticsProjectionAdapter implements SaveOrderAnalyticsProjectionOutPort {

    private final OrderAnalyticsProjectionEntityRepository orderAnalyticsProjectionEntityRepository;

    private final OrderAnalyticsProjectionPersistenceMapper orderAnalyticsProjectionPersistenceMapper;

    @Override
    public void save(final OrderAnalyticsProjection projection) {

        orderAnalyticsProjectionPersistenceMapper.mapToEntity(projection).ifPresent(this::saveIgnoringDuplicates);
    }

    private void saveIgnoringDuplicates(final OrderAnalyticsProjectionEntity entity) {

        try {

            orderAnalyticsProjectionEntityRepository.saveAndFlush(entity);
        } catch (final DataIntegrityViolationException exception) {

            log.info(
                    "Order analytics projection for order '{}' was already recorded, ignoring this redelivery.",
                    entity.getOrderNumber());
        }
    }

}
