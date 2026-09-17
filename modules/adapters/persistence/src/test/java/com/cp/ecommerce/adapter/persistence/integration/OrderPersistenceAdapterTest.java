package com.cp.ecommerce.adapter.persistence.integration;

import java.util.Date;

import javax.sql.DataSource;

import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.adapter.persistence.configuration.PersistenceConfiguration;
import com.cp.ecommerce.adapter.persistence.order.SaveOrderAdapter;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.mapper.OrderPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventEntity;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventStatus;
import com.cp.ecommerce.adapter.persistence.utils.CustomerEntityBuilder;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderStatus;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

import static com.cp.ecommerce.adapter.common.constant.CacheConstants.ORDER_CACHE_NAME;
import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.TEST_ORDER_NUMBER;
import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.TEST_REMARKS;

/**
 * Integration test for order - save to db and create cache test.
 */
@DataJpaTest
@ActiveProfiles("persistence-h2-in-memory")
@ContextConfiguration(classes = PersistenceConfiguration.class)
@TestPropertySource(properties = "outbox.publisher.enabled=false")
class OrderPersistenceAdapterTest {

    @Autowired
    public transient EntityManager entityManager;
    @Autowired
    transient DataSource dataSource;
    @Autowired
    transient JdbcTemplate jdbcTemplate;
    @Autowired
    transient OrderPersistenceMapper orderPersistenceMapper;

    @Autowired
    transient OrderEntityRepository orderEntityRepository;

    @Autowired
    transient CacheManager cacheManager;

    @Autowired
    transient OutboxEventEntityRepository outboxEventEntityRepository;

    @Autowired
    transient SaveOrderAdapter adapter;

    @Test
    void springContextShouldLoad(final ApplicationContext context) {
        assertThat(context).isNotNull();
    }

    @Test
    void injectedComponentsShouldNotBeNull() {
        assertThat(dataSource).isNotNull();
        assertThat(jdbcTemplate).isNotNull();
        assertThat(entityManager).isNotNull();
    }

    @Test
    void shouldHaveAutowiredComponents() {
        assertThat(adapter).isNotNull();
        assertThat(orderPersistenceMapper).isNotNull();
        assertThat(orderEntityRepository).isNotNull();
        assertThat(outboxEventEntityRepository).isNotNull();
    }

    @Test
    @DirtiesContext
    void shouldSaveOrderDirectAndCreateCache() {

        final OrderEntity entity = OrderEntity.builder()
                .remarks(TEST_REMARKS)
                .orderNumber(TEST_ORDER_NUMBER)
                .created(new Date())
                .customer(CustomerEntityBuilder.mockContactEntity())
                .status(OrderStatus.CONFIRMED)
                .build();

        orderEntityRepository.save(entity);
        final OrderEntity result = orderEntityRepository.getOrderEntityByOrderNumber(TEST_ORDER_NUMBER);
        final OrderEntity cachedOrder = cacheManager.getCache(ORDER_CACHE_NAME).get(TEST_ORDER_NUMBER, OrderEntity.class);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getRemarks()).isEqualTo(TEST_REMARKS);
        assertThat(result.getOrderNumber()).isEqualTo(TEST_ORDER_NUMBER);
        assertThat(result.getCustomer()).isNotNull();
        assertThat(cachedOrder).isEqualTo(result);
    }

    @Test
    @DirtiesContext
    void shouldSaveOrderFromAdapter() {

        final Order order = OrderBuilder.mockOrder();
        final Order result = adapter.save(order);
        final OutboxEventEntity outboxEvent = outboxEventEntityRepository.findAll().get(0);

        assertThat(result).isNotNull();
        assertThat(result.getOrderNumber()).isEqualTo(TEST_ORDER_NUMBER);
        assertThat(outboxEvent.getOrderNumber()).isEqualTo(TEST_ORDER_NUMBER);
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEvent.getCreatedDate()).isNotNull();
        assertThat(outboxEvent.getSentDate()).isNull();
    }

    @Test
    @DirtiesContext
    void shouldFindPendingOutboxEventsOrderedByCreatedDate() {

        final OutboxEventEntity laterEvent = OutboxEventEntity.builder()
                .orderNumber("5678")
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date(2L))
                .build();
        final OutboxEventEntity earlierEvent = OutboxEventEntity.builder()
                .orderNumber(TEST_ORDER_NUMBER)
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date(1L))
                .build();
        outboxEventEntityRepository.save(laterEvent);
        outboxEventEntityRepository.save(earlierEvent);

        final var pendingEvents = outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING);

        assertThat(pendingEvents).extracting(OutboxEventEntity::getOrderNumber).containsExactly(TEST_ORDER_NUMBER, "5678");
    }

    @Test
    @DirtiesContext
    void shouldRefreshCacheOnSaveWhenOrderNumberIsReused() {

        final OrderEntity original = OrderEntity.builder()
                .remarks("original remark")
                .orderNumber(TEST_ORDER_NUMBER)
                .created(new Date())
                .customer(CustomerEntityBuilder.mockContactEntity())
                .status(OrderStatus.CONFIRMED)
                .build();
        orderEntityRepository.save(original);
        orderEntityRepository.getOrderEntityByOrderNumber(TEST_ORDER_NUMBER);

        final OrderEntity reusedOrderNumber = OrderEntity.builder()
                .remarks("updated remark after order number reuse")
                .orderNumber(TEST_ORDER_NUMBER)
                .created(new Date())
                .customer(CustomerEntityBuilder.mockContactEntity())
                .status(OrderStatus.CONFIRMED)
                .build();
        orderEntityRepository.save(reusedOrderNumber);

        final OrderEntity cachedOrder = cacheManager.getCache(ORDER_CACHE_NAME).get(TEST_ORDER_NUMBER, OrderEntity.class);
        final OrderEntity result = orderEntityRepository.getOrderEntityByOrderNumber(TEST_ORDER_NUMBER);

        assertThat(cachedOrder.getRemarks()).isEqualTo("updated remark after order number reuse");
        assertThat(result.getRemarks()).isEqualTo("updated remark after order number reuse");
    }

}
