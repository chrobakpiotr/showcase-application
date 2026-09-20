package com.cp.ecommerce.adapter.persistence.shipment;

import java.util.concurrent.Callable;

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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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

        doAnswer(invocation -> {
            final Callable<String> action = invocation.getArgument(1);
            return action.call();
        }).when(resilientExecutor).callResilient(anyString(), org.mockito.ArgumentMatchers.<Callable<String>> any());

        final String result = mockTrackingNumberGeneratorAdapter.generate("DHL");

        assertThat(result).startsWith("DHL-");
        verify(resilientExecutor).callResilient(anyString(), org.mockito.ArgumentMatchers.<Callable<String>> any());
    }

    @Test
    void shouldDefaultCarrierCodeWhenCarrierIsNull() throws Exception {

        doAnswer(invocation -> {
            final Callable<String> action = invocation.getArgument(1);
            return action.call();
        }).when(resilientExecutor).callResilient(anyString(), org.mockito.ArgumentMatchers.<Callable<String>> any());

        final String result = mockTrackingNumberGeneratorAdapter.generate(null);

        assertThat(result).startsWith("CARRIER-");
    }

    @Test
    void shouldDefaultCarrierCodeWhenSanitizedCarrierIsBlank() throws Exception {

        doAnswer(invocation -> {
            final Callable<String> action = invocation.getArgument(1);
            return action.call();
        }).when(resilientExecutor).callResilient(anyString(), org.mockito.ArgumentMatchers.<Callable<String>> any());

        final String result = mockTrackingNumberGeneratorAdapter.generate("***");

        assertThat(result).startsWith("CARRIER-");
    }

    @Test
    void shouldWrapUnexpectedGeneratorFailureAsTechnicalProblem() throws Exception {

        doThrow(new RuntimeException("generator timeout")).when(resilientExecutor)
                .callResilient(anyString(), org.mockito.ArgumentMatchers.<Callable<String>> any());

        assertThatThrownBy(() -> mockTrackingNumberGeneratorAdapter.generate("DHL"))
                .isInstanceOf(TechnicalProblemException.class);
    }

}
