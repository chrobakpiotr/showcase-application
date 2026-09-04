package com.cp.ecommerce.adapter.persistence.order.analytics;

import com.cp.ecommerce.domain.order.port.incoming.GenerateOpsDigestInPort;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Drives {@link GenerateOpsDigestInPort} both eagerly, once, right after application start-up, and thereafter on a recurring
 * schedule (see ADR 0022).
 *
 * <p>
 * The eager run exists purely so a fresh {@code docker compose up} - or this application's own e2e/demo environment - always
 * has a digest to display straight away, instead of an empty ops-analytics page until the first cron tick (once a day by
 * default) fires. Unlike {@code OrderPlacementSagaOrchestrator}, this has no outbox event or retry bookkeeping of its own: a
 * failure here is best-effort, logged, and simply tried again on the next scheduled run.
 *
 * <p>
 * Gated by the same {@code outbox.publisher.enabled} property as {@code OrderPlacementSagaOrchestrator} (not a dedicated flag
 * of its own): {@code PersistenceConfiguration}'s {@code @ComponentScan} picks up every {@code @Component} in this module,
 * including narrow {@code @DataJpaTest} slices that set {@code outbox.publisher.enabled=false} precisely to avoid needing a
 * domain-use-case bean like {@link GenerateOpsDigestInPort} in their trimmed-down context - see
 * {@code MicrometerRemarksClassificationSummaryAdapter}'s javadoc for the same gotcha, encountered first there.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "outbox.publisher", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OpsDigestScheduler {

    private final GenerateOpsDigestInPort generateOpsDigestInPort;

    /**
     * Generates one digest immediately once the application context is fully up, so the ops-analytics page never shows "no
     * digest yet" on a normal boot.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void generateEagerlyOnStartup() {

        generate();
    }

    /**
     * Generates a fresh digest on a recurring schedule, daily at 06:00 (server time) by default.
     */
    @Scheduled(cron = "${ops-digest.cron:0 0 6 * * *}")
    public void generateOnSchedule() {

        generate();
    }

    private void generate() {

        try {
            generateOpsDigestInPort.generateDigest();
        } catch (RuntimeException exception) {
            log.warn("Could not generate ops digest (best-effort), will try again on the next scheduled run.", exception);
        }
    }

}
