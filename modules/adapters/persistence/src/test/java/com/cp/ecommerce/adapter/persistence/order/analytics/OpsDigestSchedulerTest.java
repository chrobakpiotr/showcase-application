package com.cp.ecommerce.adapter.persistence.order.analytics;

import com.cp.ecommerce.domain.order.port.incoming.GenerateOpsDigestInPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

/**
 * Test class for {@link OpsDigestScheduler}.
 */
@ExtendWith(MockitoExtension.class)
class OpsDigestSchedulerTest {

    @InjectMocks
    private transient OpsDigestScheduler opsDigestScheduler;

    @Mock
    private transient GenerateOpsDigestInPort generateOpsDigestInPort;

    @Test
    void shouldGenerateDigestEagerlyOnStartup() {

        opsDigestScheduler.generateEagerlyOnStartup();

        then(generateOpsDigestInPort).should().generateDigest();
    }

    @Test
    void shouldGenerateDigestOnSchedule() {

        opsDigestScheduler.generateOnSchedule();

        then(generateOpsDigestInPort).should().generateDigest();
    }

    @Test
    void shouldLogAndContinueWhenGenerationFails() {

        willThrow(new IllegalStateException("model unavailable")).given(generateOpsDigestInPort).generateDigest();

        assertThatCode(() -> opsDigestScheduler.generateOnSchedule()).doesNotThrowAnyException();
    }

    @Test
    void shouldNotPropagateFailuresFromEagerStartupRunEither() {

        given(generateOpsDigestInPort.generateDigest()).willThrow(new IllegalStateException("model unavailable"));

        assertThatCode(() -> opsDigestScheduler.generateEagerlyOnStartup()).doesNotThrowAnyException();
    }

}
