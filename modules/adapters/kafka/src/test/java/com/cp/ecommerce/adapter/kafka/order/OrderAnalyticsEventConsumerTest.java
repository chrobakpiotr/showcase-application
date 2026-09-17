package com.cp.ecommerce.adapter.kafka.order;

import java.time.Instant;
import java.util.Date;

import com.cp.ecommerce.adapter.kafka.order.metrics.OrderAnalyticsConsumerMetrics;
import com.cp.ecommerce.domain.order.OrderAnalyticsProjection;
import com.cp.ecommerce.domain.order.port.incoming.RecordOrderAnalyticsProjectionInPort;
import com.google.gson.Gson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;

/**
 * Unit tests for {@link OrderAnalyticsEventConsumer}.
 *
 * <p>
 * Uses a real {@link Gson} instance with hand-built JSON matching the documented wire schema (see
 * {@code OrderAnalyticsEventContractTest}) rather than an embedded/Testcontainers Kafka broker, since only the consumer's own
 * deserialize-map-delegate logic is under test here, not Kafka itself.
 */
@ExtendWith(MockitoExtension.class)
class OrderAnalyticsEventConsumerTest {

    @Mock
    private transient RecordOrderAnalyticsProjectionInPort recordOrderAnalyticsProjectionInPort;

    @Mock
    private transient OrderAnalyticsConsumerMetrics orderAnalyticsConsumerMetrics;

    @Spy
    private transient Gson gson = new Gson();

    @InjectMocks
    private transient OrderAnalyticsEventConsumer orderAnalyticsEventConsumer;

    @Test
    void shouldRecordProjectionForConsumedEvent() {

        final String payload = "{\"schemaVersion\":\"1.0\",\"orderNumber\":\"ORD-1001\",\"customerId\":1001,"
                + "\"eventType\":\"ORDER_PLACED\",\"timestamp\":\"2024-01-01T00:00:00Z\"}";

        orderAnalyticsEventConsumer.consume(payload);

        final ArgumentCaptor<OrderAnalyticsProjection> captor = ArgumentCaptor.forClass(OrderAnalyticsProjection.class);
        then(recordOrderAnalyticsProjectionInPort).should().recordProjection(captor.capture());
        final OrderAnalyticsProjection projection = captor.getValue();
        assertThat(projection.orderNumber()).isEqualTo("ORD-1001");
        assertThat(projection.customerId()).isEqualTo(1001L);
        assertThat(projection.orderPlacedDate()).isEqualTo(Date.from(Instant.parse("2024-01-01T00:00:00Z")));
        assertThat(projection.consumedDate()).isNotNull();
        then(orderAnalyticsConsumerMetrics).should().recordConsumed();
    }

    @Test
    void shouldStillRecordProjectionWhenSchemaVersionIsUnexpected() {

        final String payload = "{\"schemaVersion\":\"2.0\",\"orderNumber\":\"ORD-1002\",\"customerId\":1002,"
                + "\"eventType\":\"ORDER_PLACED\",\"timestamp\":\"2024-01-01T00:00:00Z\"}";

        orderAnalyticsEventConsumer.consume(payload);

        then(recordOrderAnalyticsProjectionInPort).should().recordProjection(any());
        then(orderAnalyticsConsumerMetrics).should().recordConsumed();
    }

}
