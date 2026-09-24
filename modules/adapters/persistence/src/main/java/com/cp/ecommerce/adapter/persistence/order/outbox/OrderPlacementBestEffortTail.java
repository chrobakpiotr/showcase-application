package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.cp.ecommerce.adapter.persistence.order.dispatch.OrderPlacementDispatchManager;
import com.cp.ecommerce.adapter.persistence.order.outbox.metrics.SagaMetrics;
import com.cp.ecommerce.domain.order.DuplicateOrderCheckResult;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.RemarksTriageCategory;
import com.cp.ecommerce.domain.order.RemarksTriageResult;
import com.cp.ecommerce.domain.order.port.incoming.ClassifyOrderRemarksInPort;
import com.cp.ecommerce.domain.order.port.incoming.DetectDuplicateOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.ExportOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.PublishOrderAnalyticsEventInPort;
import com.cp.ecommerce.domain.order.port.incoming.PublishOrderAuditEventInPort;
import com.cp.ecommerce.foundation.function.RuntimeFailureBoundary;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs independent post-fulfillment side effects.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "outbox.publisher", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OrderPlacementBestEffortTail {

    private final OrderPlacementDispatchManager orderPlacementDispatchManager;
    private final ExportOrderInPort exportOrderInPort;
    private final PublishOrderAuditEventInPort publishOrderAuditEventInPort;
    private final PublishOrderAnalyticsEventInPort publishOrderAnalyticsEventInPort;
    private final ClassifyOrderRemarksInPort classifyOrderRemarksInPort;
    private final DetectDuplicateOrderInPort detectDuplicateOrderInPort;
    private final SagaMetrics sagaMetrics;

    void run(final Order order) {

        orderPlacementDispatchManager.enqueue(order);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.execute(
                    () -> runSimple(
                            "s3-export",
                            order,
                            exportOrderInPort::exportOrder,
                            "Could not export order to S3 (best-effort): {}"));
            executor.execute(
                    () -> runSimple(
                            "sqs-audit",
                            order,
                            publishOrderAuditEventInPort::publishAuditEvent,
                            "Could not publish SQS audit event (best-effort): {}"));
            executor.execute(
                    () -> runSimple(
                            "kafka-analytics",
                            order,
                            publishOrderAnalyticsEventInPort::publishAnalyticsEvent,
                            "Could not publish Kafka analytics event (best-effort): {}"));
            executor.execute(() -> classifyRemarks(order));
            executor.execute(() -> detectDuplicateOrder(order));
        }
    }

    private void classifyRemarks(final Order order) {

        final long startNanos = System.nanoTime();
        RuntimeFailureBoundary.run(() -> {
            final RemarksTriageResult result = classifyOrderRemarksInPort.classifyRemarks(order);
            sagaMetrics.recordStepDuration("ai-remarks-triage", elapsedSince(startNanos), true);
            sagaMetrics.recordRemarksClassification(result.getCategory());
            if (result.getCategory() == RemarksTriageCategory.SUSPICIOUS) {
                log.warn(
                        "Order remarks flagged as SUSPICIOUS by AI triage (human review recommended): orderNumber={}, rationale={}",
                        order.getOrderNumber(),
                        result.getRationale());
            }
        }, exception -> {
            sagaMetrics.recordStepDuration("ai-remarks-triage", elapsedSince(startNanos), false);
            log.warn("Could not classify order remarks (best-effort): {}", order.getOrderNumber(), exception);
        });
    }

    private void detectDuplicateOrder(final Order order) {

        final long startNanos = System.nanoTime();
        RuntimeFailureBoundary.run(() -> {
            final DuplicateOrderCheckResult result = detectDuplicateOrderInPort.detectDuplicate(order);
            sagaMetrics.recordStepDuration("ai-duplicate-order-detection", elapsedSince(startNanos), true);
            sagaMetrics.recordDuplicateOrderDetection(result.isDuplicate());
            if (result.isDuplicate()) {
                log.warn(
                        "Order flagged as a likely duplicate by AI similarity check (human review recommended): "
                                + "orderNumber={}, matchedOrderNumber={}, similarityScore={}, rationale={}",
                        order.getOrderNumber(),
                        result.getMatchedOrderNumber(),
                        result.getSimilarityScore(),
                        result.getRationale());
            }
        }, exception -> {
            sagaMetrics.recordStepDuration("ai-duplicate-order-detection", elapsedSince(startNanos), false);
            log.warn("Could not run AI duplicate-order detection (best-effort): {}", order.getOrderNumber(), exception);
        });
    }

    private void runSimple(final String step, final Order order, final Consumer<Order> action, final String failureLogMessage) {

        final long startNanos = System.nanoTime();
        RuntimeFailureBoundary.run(() -> {
            action.accept(order);
            sagaMetrics.recordStepDuration(step, elapsedSince(startNanos), true);
        }, exception -> {
            sagaMetrics.recordStepDuration(step, elapsedSince(startNanos), false);
            log.warn(failureLogMessage, order.getOrderNumber(), exception);
        });
    }

    private static Duration elapsedSince(final long startNanos) {

        return Duration.ofNanos(System.nanoTime() - startNanos);
    }
}
