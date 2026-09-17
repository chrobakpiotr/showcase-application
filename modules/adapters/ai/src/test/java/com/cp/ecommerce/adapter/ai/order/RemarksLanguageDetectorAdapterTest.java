package com.cp.ecommerce.adapter.ai.order;

import java.util.Collections;
import java.util.concurrent.Callable;

import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.order.SupportedLocale;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RemarksLanguageDetectorAdapter}. Mirrors {@code OrderRemarksClassifierAdapterTest}'s approach: a real
 * {@link ChatClient} built around a mocked {@link ChatModel}, so only the model call itself is stubbed.
 */
@ExtendWith(MockitoExtension.class)
class RemarksLanguageDetectorAdapterTest {

    @Mock
    transient ChatModel chatModel;

    @Mock
    transient ResilientExecutor resilientExecutor;

    @Test
    void shouldReturnEnglishWithoutCallingModelWhenRemarksAreBlank() {

        final RemarksLanguageDetectorAdapter adapter = newAdapter();

        final SupportedLocale locale = adapter.detectLanguage(" ");

        assertThat(locale).isEqualTo(SupportedLocale.ENGLISH);
        verifyNoInteractions(chatModel);
    }

    @Test
    void shouldReturnEnglishWithoutCallingModelWhenRemarksAreNull() {

        final RemarksLanguageDetectorAdapter adapter = newAdapter();

        final SupportedLocale locale = adapter.detectLanguage(null);

        assertThat(locale).isEqualTo(SupportedLocale.ENGLISH);
        verifyNoInteractions(chatModel);
    }

    @Test
    void shouldDetectPolishUsingTheModelResponse() {

        respondWith("{\"language\": \"POLISH\"}");
        runResilientActionEagerly();
        final RemarksLanguageDetectorAdapter adapter = newAdapter();

        final SupportedLocale locale = adapter.detectLanguage("Proszę dostarczyć jutro.");

        assertThat(locale).isEqualTo(SupportedLocale.POLISH);
    }

    @Test
    void shouldDefaultToEnglishWhenModelReturnsAnUnrecognisedLanguage() {

        respondWith("{\"language\": \"GERMAN\"}");
        runResilientActionEagerly();
        final RemarksLanguageDetectorAdapter adapter = newAdapter();

        final SupportedLocale locale = adapter.detectLanguage("Bitte liefern Sie morgen.");

        assertThat(locale).isEqualTo(SupportedLocale.ENGLISH);
    }

    @Test
    void shouldDefaultToEnglishWhenResilienceFails() throws Exception {

        when(resilientExecutor.callResilient(anyString(), any())).thenThrow(new IllegalStateException("circuit open"));
        final RemarksLanguageDetectorAdapter adapter = newAdapter();

        final SupportedLocale locale = adapter.detectLanguage("Please deliver tomorrow.");

        assertThat(locale).isEqualTo(SupportedLocale.ENGLISH);
    }

    private RemarksLanguageDetectorAdapter newAdapter() {

        return new RemarksLanguageDetectorAdapter(ChatClient.builder(chatModel), resilientExecutor);
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
                final Callable<SupportedLocale> action = invocation.getArgument(1);
                return action.call();
            });
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

}
