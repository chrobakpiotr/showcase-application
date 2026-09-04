package com.cp.ecommerce.adapter.ai.order;

import java.util.Collections;
import java.util.Date;
import java.util.concurrent.Callable;

import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.RemarksTriageCategory;
import com.cp.ecommerce.domain.order.RemarksTriageResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import static com.cp.ecommerce.adapter.common.utils.CustomerBuilder.mockCustomer;
import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.TEST_ORDER_NUMBER;
import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.mockOrder;

/**
 * Unit tests for {@link OllamaOrderRemarksClassifierAdapter}. Rather than mocking the fluent {@link ChatClient} API directly
 * (its return type per call-site makes that brittle), a real {@link ChatClient} is built around a mocked {@link ChatModel} -
 * the model call itself (the only external boundary) is what's stubbed.
 */
@ExtendWith(MockitoExtension.class)
class OllamaOrderRemarksClassifierAdapterTest {

    @Mock
    transient ChatModel chatModel;

    @Mock
    transient ResilientExecutor resilientExecutor;

    @Test
    void shouldReturnStandardClassificationWithoutCallingModelWhenRemarksAreBlank() {

        final Order order = Order.builder()
                .remarks(" ")
                .orderNumber(TEST_ORDER_NUMBER)
                .created(new Date())
                .customer(mockCustomer())
                .build();
        final OllamaOrderRemarksClassifierAdapter adapter = newAdapter();

        final RemarksTriageResult result = adapter.classify(order);

        assertThat(result.getCategory()).isEqualTo(RemarksTriageCategory.STANDARD);
        verifyNoInteractions(chatModel);
    }

    @Test
    void shouldClassifyRemarksUsingTheModelResponse() {

        respondWith("{\"category\": \"URGENT\", \"rationale\": \"Customer needs delivery by tomorrow.\"}");
        runResilientActionEagerly();
        final Order order = mockOrder();
        final OllamaOrderRemarksClassifierAdapter adapter = newAdapter();

        final RemarksTriageResult result = adapter.classify(order);

        assertThat(result.getCategory()).isEqualTo(RemarksTriageCategory.URGENT);
        assertThat(result.getRationale()).isEqualTo("Customer needs delivery by tomorrow.");
    }

    @Test
    void shouldDefaultToStandardWhenModelReturnsAnUnrecognisedCategory() {

        respondWith("{\"category\": \"NOT_A_REAL_CATEGORY\", \"rationale\": \"unclear\"}");
        runResilientActionEagerly();
        final Order order = mockOrder();
        final OllamaOrderRemarksClassifierAdapter adapter = newAdapter();

        final RemarksTriageResult result = adapter.classify(order);

        assertThat(result.getCategory()).isEqualTo(RemarksTriageCategory.STANDARD);
    }

    @Test
    void shouldWrapAndPropagateResilienceFailures() throws Exception {

        when(resilientExecutor.callResilient(anyString(), any())).thenThrow(new IllegalStateException("circuit open"));
        final Order order = mockOrder();
        final OllamaOrderRemarksClassifierAdapter adapter = newAdapter();

        assertThatThrownBy(() -> adapter.classify(order)).isInstanceOf(IllegalStateException.class);
    }

    private OllamaOrderRemarksClassifierAdapter newAdapter() {

        return new OllamaOrderRemarksClassifierAdapter(ChatClient.builder(chatModel), resilientExecutor);
    }

    private void respondWith(final String content) {

        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(Collections.singletonList(new Generation(new AssistantMessage(content)))));
    }

    @SuppressWarnings("unchecked")
    private void runResilientActionEagerly() {

        try {
            when(resilientExecutor.callResilient(anyString(), any())).thenAnswer(invocation -> {
                final Callable<RemarksTriageResult> action = invocation.getArgument(1);
                return action.call();
            });
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

}
