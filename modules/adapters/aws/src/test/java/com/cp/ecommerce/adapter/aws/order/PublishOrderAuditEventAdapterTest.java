package com.cp.ecommerce.adapter.aws.order;

import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.order.Order;
import com.google.gson.Gson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.mockOrder;

/**
 * Unit tests for {@link PublishOrderAuditEventAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class PublishOrderAuditEventAdapterTest {

    @Mock
    transient SqsClient sqsClient;

    @Mock
    transient ResilientExecutor resilientExecutor;

    @Mock
    transient Gson gson;

    @Test
    void shouldPublishAuditEventToSqs() {

        final Order order = mockOrder();
        final String queueUrl = "http://localhost:4566/000000000000/ecommerce-order-audit";
        final PublishOrderAuditEventAdapter adapter = new PublishOrderAuditEventAdapter(
                sqsClient,
                queueUrl,
                resilientExecutor,
                gson);
        runResilientActionEagerly();
        given(gson.toJson(any(Object.class))).willReturn("{}");
        given(sqsClient.sendMessage(any(SendMessageRequest.class))).willReturn(SendMessageResponse.builder().build());

        adapter.publish(order);

        verify(sqsClient).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void shouldUseCorrectQueueUrl() {

        final Order order = mockOrder();
        final String queueUrl = "http://localhost:4566/000000000000/ecommerce-order-audit";
        final PublishOrderAuditEventAdapter adapter = new PublishOrderAuditEventAdapter(
                sqsClient,
                queueUrl,
                resilientExecutor,
                gson);
        runResilientActionEagerly();
        given(gson.toJson(any(Object.class))).willReturn("{}");
        given(sqsClient.sendMessage(any(SendMessageRequest.class))).willReturn(SendMessageResponse.builder().build());

        adapter.publish(order);

        verify(sqsClient).sendMessage((SendMessageRequest) org.mockito.ArgumentMatchers.argThat(req -> {
            final SendMessageRequest r = (SendMessageRequest) req;
            return queueUrl.equals(r.queueUrl()) && r.messageBody() != null;
        }));
    }

    private void runResilientActionEagerly() {

        doAnswer(invocation -> {
            final Runnable action = invocation.getArgument(1);
            action.run();
            return null;
        }).when(resilientExecutor).runResilient(anyString(), any(Runnable.class));
    }

}
