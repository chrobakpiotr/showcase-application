package com.cp.ecommerce.adapter.kafka.order;

import java.util.concurrent.CompletableFuture;

import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.order.Order;
import com.google.gson.Gson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.mockOrder;
import static com.cp.ecommerce.adapter.kafka.configuration.KafkaTopicConfiguration.ORDER_ANALYTICS_TOPIC_NAME;

/**
 * Unit tests for {@link PublishOrderAnalyticsEventAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class PublishOrderAnalyticsEventAdapterTest {

    @Mock
    transient KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    transient ResilientExecutor resilientExecutor;

    @Mock
    transient Gson gson;

    @Test
    void shouldPublishAnalyticsEventToKafka() {

        final Order order = mockOrder();
        final PublishOrderAnalyticsEventAdapter adapter = new PublishOrderAnalyticsEventAdapter(
                kafkaTemplate,
                resilientExecutor,
                gson);
        runResilientActionEagerly();
        given(gson.toJson(any(Object.class))).willReturn("{}");
        given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .willReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        adapter.publish(order);

        verify(kafkaTemplate).send(eq(ORDER_ANALYTICS_TOPIC_NAME), eq(order.getOrderNumber()), any(String.class));
    }

    private void runResilientActionEagerly() {

        doAnswer(invocation -> {
            final Runnable action = invocation.getArgument(1);
            action.run();
            return null;
        }).when(resilientExecutor).runResilient(anyString(), any(Runnable.class));
    }

}
