package com.cp.ecommerce.adapter.persistence.shipment;

import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.foundation.exception.TechnicalProblemException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

/**
 * Test class for {@link MockTrackingNumberGeneratorAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class MockTrackingNumberGeneratorAdapterTest {

    @Mock
    private transient ResilientExecutor resilientExecutor;

    @InjectMocks
    private transient MockTrackingNumberGeneratorAdapter mockTrackingNumberGeneratorAdapter;

    @Test
    void shouldGenerateTrackingNumber() throws Exception {

        org.mockito.Mockito.when(
                resilientExecutor.callResilientOrElse(
                        anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    final java.util.function.Supplier<String> action = invocation.getArgument(1);
                    return action.get();
                });

        final String result = mockTrackingNumberGeneratorAdapter.generate("DHL");

        assertThat(result).startsWith("DHL-");
        verify(resilientExecutor)
                .callResilientOrElse(anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldDefaultCarrierCodeWhenCarrierIsNull() throws Exception {

        org.mockito.Mockito.when(
                resilientExecutor.callResilientOrElse(
                        anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    final java.util.function.Supplier<String> action = invocation.getArgument(1);
                    return action.get();
                });

        final String result = mockTrackingNumberGeneratorAdapter.generate(null);

        assertThat(result).startsWith("CARRIER-");
    }

    @Test
    void shouldDefaultCarrierCodeWhenSanitizedCarrierIsBlank() throws Exception {

        org.mockito.Mockito.when(
                resilientExecutor.callResilientOrElse(
                        anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    final java.util.function.Supplier<String> action = invocation.getArgument(1);
                    return action.get();
                });

        final String result = mockTrackingNumberGeneratorAdapter.generate("***");

        assertThat(result).startsWith("CARRIER-");
    }

    @Test
    void shouldWrapUnexpectedGeneratorFailureAsTechnicalProblem() throws Exception {

        org.mockito.Mockito.when(
                resilientExecutor.callResilientOrElse(
                        anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    final java.util.function.Function<RuntimeException, ?> fallback = invocation.getArgument(2);
                    return fallback.apply(new IllegalStateException("generator timeout"));
                });

        assertThatThrownBy(() -> mockTrackingNumberGeneratorAdapter.generate("DHL"))
                .isInstanceOf(TechnicalProblemException.class);
    }

}
