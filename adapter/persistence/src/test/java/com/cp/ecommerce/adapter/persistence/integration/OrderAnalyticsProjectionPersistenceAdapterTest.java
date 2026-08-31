package com.cp.ecommerce.adapter.persistence.integration;

import java.util.Date;
import java.util.List;

import com.cp.ecommerce.adapter.persistence.configuration.PersistenceConfiguration;
import com.cp.ecommerce.adapter.persistence.order.analytics.OrderAnalyticsProjectionEntity;
import com.cp.ecommerce.adapter.persistence.order.analytics.OrderAnalyticsProjectionEntityRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link OrderAnalyticsProjectionEntityRepository}, verifying the entity mapping and derived query method
 * against the real (Liquibase-migrated) H2 schema rather than a mocked repository.
 */
@DataJpaTest
@ActiveProfiles("persistence-h2-in-memory")
@ContextConfiguration(classes = PersistenceConfiguration.class)
@TestPropertySource(properties = "outbox.publisher.enabled=false")
class OrderAnalyticsProjectionPersistenceAdapterTest {

    @Autowired
    transient OrderAnalyticsProjectionEntityRepository orderAnalyticsProjectionEntityRepository;

    @Test
    @DirtiesContext
    void shouldSaveAndGenerateId() {

        final OrderAnalyticsProjectionEntity entity = OrderAnalyticsProjectionEntity.builder()
                .orderNumber("ORDER-1")
                .customerId(1L)
                .orderPlacedDate(new Date())
                .consumedDate(new Date())
                .build();

        final OrderAnalyticsProjectionEntity saved = orderAnalyticsProjectionEntityRepository.save(entity);

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @DirtiesContext
    void shouldFindRecentOrderedByConsumedDateDescending() {

        final OrderAnalyticsProjectionEntity earlier = OrderAnalyticsProjectionEntity.builder()
                .orderNumber("ORDER-EARLIER")
                .customerId(1L)
                .orderPlacedDate(new Date(1L))
                .consumedDate(new Date(1L))
                .build();
        final OrderAnalyticsProjectionEntity later = OrderAnalyticsProjectionEntity.builder()
                .orderNumber("ORDER-LATER")
                .customerId(2L)
                .orderPlacedDate(new Date(2L))
                .consumedDate(new Date(2L))
                .build();
        orderAnalyticsProjectionEntityRepository.save(earlier);
        orderAnalyticsProjectionEntityRepository.save(later);

        final Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "consumedDate"));
        final List<OrderAnalyticsProjectionEntity> result = orderAnalyticsProjectionEntityRepository
                .findAllByOrderByConsumedDateDesc(pageable)
                .getContent();

        assertThat(result).extracting(OrderAnalyticsProjectionEntity::getOrderNumber)
                .containsExactly("ORDER-LATER", "ORDER-EARLIER");
    }

    @Test
    @DirtiesContext
    void shouldCapResultsToRequestedLimit() {

        for (int i = 0; i < 5; i++) {

            orderAnalyticsProjectionEntityRepository.save(
                    OrderAnalyticsProjectionEntity.builder()
                            .orderNumber("ORDER-" + i)
                            .customerId(1L)
                            .orderPlacedDate(new Date())
                            .consumedDate(new Date())
                            .build());
        }

        final Pageable pageable = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "consumedDate"));
        final List<OrderAnalyticsProjectionEntity> result = orderAnalyticsProjectionEntityRepository
                .findAllByOrderByConsumedDateDesc(pageable)
                .getContent();

        assertThat(result).hasSize(2);
    }

}
