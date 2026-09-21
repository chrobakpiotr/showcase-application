package com.cp.ecommerce.domain.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationEventKeyTest {

    @Test
    void shouldBuildStableBusinessEventIdentity() {

        final String eventKey = NotificationEventKey.of("order", "ORDER-123", NotificationType.ORDER_CONFIRMED, "placement-v1");

        assertThat(eventKey).isEqualTo("order:ORDER-123:ORDER_CONFIRMED:placement-v1");
    }

    @Test
    void shouldRejectBlankAggregateType() {

        assertThatThrownBy(() -> NotificationEventKey.of(" ", "ORDER-123", NotificationType.ORDER_CONFIRMED, "placement-v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("aggregateType must not be blank");
    }

    @Test
    void shouldRejectNullAggregateType() {

        assertThatThrownBy(() -> NotificationEventKey.of(null, "ORDER-123", NotificationType.ORDER_CONFIRMED, "placement-v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("aggregateType must not be blank");
    }

    @Test
    void shouldRejectBlankAggregateId() {

        assertThatThrownBy(() -> NotificationEventKey.of("order", "", NotificationType.ORDER_CONFIRMED, "placement-v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("aggregateId must not be blank");
    }

    @Test
    void shouldRejectNullAggregateId() {

        assertThatThrownBy(() -> NotificationEventKey.of("order", null, NotificationType.ORDER_CONFIRMED, "placement-v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("aggregateId must not be blank");
    }

    @Test
    void shouldRejectNullEventType() {

        assertThatThrownBy(() -> NotificationEventKey.of("order", "ORDER-123", null, "placement-v1"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("eventType");
    }

    @Test
    void shouldRejectBlankOperationIdentity() {

        assertThatThrownBy(() -> NotificationEventKey.of("order", "ORDER-123", NotificationType.ORDER_CONFIRMED, "\t"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("operationIdentity must not be blank");
    }

    @Test
    void shouldRejectNullOperationIdentity() {

        assertThatThrownBy(() -> NotificationEventKey.of("order", "ORDER-123", NotificationType.ORDER_CONFIRMED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("operationIdentity must not be blank");
    }
}
