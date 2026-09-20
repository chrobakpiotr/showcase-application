package com.cp.ecommerce.domain.order.port.outgoing;

import com.cp.ecommerce.domain.order.IdempotencyReservation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyKeyOutPortMutationWave2Test {

    @Test
    void threeArgumentCompatibilityMethodShouldUseCurrentFingerprintAndReturnDelegateResult() {
        final RecordingPort port = new RecordingPort();

        final IdempotencyReservation result = port.reserve("KEY-1", "current-fingerprint", "legacy-fingerprint");

        assertThat(result).isSameAs(port.result);
        assertThat(port.key).isEqualTo("KEY-1");
        assertThat(port.fingerprint).isEqualTo("current-fingerprint");
    }

    private static final class RecordingPort implements IdempotencyKeyOutPort {

        private final IdempotencyReservation result = IdempotencyReservation.reserved();
        private String key;
        private String fingerprint;

        @Override
        public IdempotencyReservation reserve(final String key, final String fingerprint) {
            this.key = key;
            this.fingerprint = fingerprint;
            return result;
        }

        @Override
        public void complete(final String key, final String orderNumber) {
            // Not used by this compatibility-contract test.
        }
    }
}
