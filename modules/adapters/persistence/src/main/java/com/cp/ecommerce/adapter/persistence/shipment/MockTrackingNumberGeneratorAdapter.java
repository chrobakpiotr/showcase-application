package com.cp.ecommerce.adapter.persistence.shipment;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.shipment.port.outgoing.GenerateTrackingNumberOutPort;
import com.cp.ecommerce.foundation.exception.TechnicalProblemException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Mock/simulated tracking-number generator used by the shipment bounded context.
 */
@Slf4j
@PersistenceAdapter
@RequiredArgsConstructor
class MockTrackingNumberGeneratorAdapter implements GenerateTrackingNumberOutPort {

    private static final String GENERATE_TRACKING_RESILIENCE_INSTANCE_NAME = "generateTrackingNumber";

    private final ResilientExecutor resilientExecutor;

    @Override
    public String generate(final String carrier) {

        try {
            final String trackingNumber = resilientExecutor.callResilient(
                    GENERATE_TRACKING_RESILIENCE_INSTANCE_NAME,
                    (Callable<String>) () -> carrierCode(carrier) + "-"
                            + UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
            log.info("Mock tracking number {} generated for carrier {}", trackingNumber, carrier);
            return trackingNumber;
        } catch (final Exception exception) {
            throw new TechnicalProblemException("Could not generate tracking number for carrier: " + carrier, exception);
        }
    }

    private String carrierCode(final String carrier) {

        final String sanitized = carrier == null ? "CARRIER" : carrier.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (sanitized.isBlank()) {
            return "CARRIER";
        }
        return sanitized.substring(0, Math.min(sanitized.length(), 12));
    }

}
