package com.cp.ecommerce.adapter.persistence.order.dispatch;

import java.time.Clock;

import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.RouteOrderNotificationInPort;
import com.cp.ecommerce.domain.order.port.incoming.SendOrderConfirmationEmailInPort;

import org.junit.jupiter.api.Test;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;
import org.springframework.transaction.support.TransactionOperations;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OrderPlacementDispatchConfigurationTest {

    @Test
    void shouldStartBothEnabledAndManagerOnly() {
        for (final boolean schedulerEnabled : new boolean[] { true, false }) {
            try (var context = context(true, schedulerEnabled, null)) {
                context.refresh();
                assertThat(context.getBeansOfType(OrderPlacementDispatchManager.class)).hasSize(1);
                assertThat(context.getBeansOfType(OrderPlacementDispatchScheduler.class))
                        .hasSize(schedulerEnabled ? 1 : 0);
                assertThat(context.getBean(OrderPlacementDispatchManager.class).leaseMillis).isEqualTo(120_000);
            }
        }
    }

    @Test
    void shouldStartBothDisabledWithoutValidatingUnusedManagerLease() {
        try (var context = context(false, false, 1L)) {
            context.refresh();
            assertThat(context.getBeansOfType(OrderPlacementDispatchManager.class)).isEmpty();
            assertThat(context.getBeansOfType(OrderPlacementDispatchScheduler.class)).isEmpty();
        }
    }

    @Test
    void shouldRejectEnabledSchedulerWithDisabledManagerClearly() {
        try (var context = context(false, true, null)) {
            assertThatThrownBy(context::refresh)
                    .hasStackTraceContaining("order-placement.dispatch.enabled requires order-placement.dispatch.manager-enabled");
        }
    }

    @Test
    void shouldRejectLeaseBelowMinimumAndAllowMinimumOrLonger() {
        try (var context = context(true, false, 119_999L)) {
            assertThatThrownBy(context::refresh)
                    .hasStackTraceContaining("order-placement.dispatch.lease-ms must be at least 120000");
        }
        for (final long lease : new long[] { 120_000, 180_000 }) {
            try (var context = context(true, false, lease)) {
                context.refresh();
                assertThat(context.getBean(OrderPlacementDispatchManager.class).leaseMillis).isEqualTo(lease);
            }
        }
    }

    private static AnnotationConfigApplicationContext context(final boolean managerEnabled,
            final boolean schedulerEnabled, final Long lease) {
        final var context = new AnnotationConfigApplicationContext();
        final java.util.Map<String, Object> properties = new java.util.HashMap<>();
        properties.put("order-placement.dispatch.manager-enabled", managerEnabled);
        properties.put("order-placement.dispatch.enabled", schedulerEnabled);
        if (lease != null) {
            properties.put("order-placement.dispatch.lease-ms", lease);
        }
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("dispatch-test", properties));
        context.registerBean(PropertySourcesPlaceholderConfigurer.class);
        context.registerBean(OrderPlacementDispatchEntityRepository.class, () -> mock(OrderPlacementDispatchEntityRepository.class));
        context.registerBean(EntityManager.class, () -> mock(EntityManager.class));
        context.registerBean(ManageOrderInPort.class, () -> mock(ManageOrderInPort.class));
        context.registerBean(SendOrderConfirmationEmailInPort.class, () -> mock(SendOrderConfirmationEmailInPort.class));
        context.registerBean(RouteOrderNotificationInPort.class, () -> mock(RouteOrderNotificationInPort.class));
        context.registerBean(TransactionOperations.class, () -> mock(TransactionOperations.class));
        context.registerBean(Clock.class, Clock::systemUTC);
        context.register(OrderPlacementDispatchManager.class, OrderPlacementDispatchScheduler.class);
        return context;
    }
}
