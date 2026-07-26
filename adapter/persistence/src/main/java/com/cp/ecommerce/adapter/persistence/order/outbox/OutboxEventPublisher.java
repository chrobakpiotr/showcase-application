package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.util.Date;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.incoming.ExportOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.PublishOrderAuditEventInPort;
import com.cp.ecommerce.domain.order.port.incoming.SendMessageInPort;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Polls pending outbox events and publishes them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "outbox.publisher", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxEventPublisher {

    private final OutboxEventEntityRepository outboxEventEntityRepository;

    private final ManageOrderInPort manageOrderInPort;

    private final SendMessageInPort sendMessageInPort;

    private final ExportOrderInPort exportOrderInPort;

    private final PublishOrderAuditEventInPort publishOrderAuditEventInPort;

    private final TransactionOperations transactionOperations;

    /**
     * Publish all pending outbox events.
     */
    @Scheduled(fixedDelayString = "${outbox.publisher.poll-interval-ms:5000}")
    public void publishPendingEvents() {

        outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING)
                .forEach(this::publishPendingEvent);
    }

    private void publishPendingEvent(final OutboxEventEntity outboxEventEntity) {

        try {
            transactionOperations.executeWithoutResult(status -> processPendingEvent(outboxEventEntity));
        } catch (RuntimeException exception) {
            log.warn("Could not publish outbox event for order: {}", outboxEventEntity.getOrderNumber(), exception);
        }
    }

    private void processPendingEvent(final OutboxEventEntity outboxEventEntity) {

        final Order order = manageOrderInPort.findOrder(outboxEventEntity.getOrderNumber());
        sendMessageInPort.sendMessage(order);
        exportOrderBestEffort(order);
        publishAuditEventBestEffort(order);
        outboxEventEntity.setStatus(OutboxEventStatus.SENT);
        outboxEventEntity.setSentDate(new Date());
        outboxEventEntityRepository.save(outboxEventEntity);
    }

    private void exportOrderBestEffort(final Order order) {

        try {
            exportOrderInPort.exportOrder(order);
        } catch (RuntimeException exception) {
            log.warn("Could not export order to S3 (best-effort): {}", order.getOrderNumber(), exception);
        }
    }

    private void publishAuditEventBestEffort(final Order order) {

        try {
            publishOrderAuditEventInPort.publishAuditEvent(order);
        } catch (RuntimeException exception) {
            log.warn("Could not publish SQS audit event (best-effort): {}", order.getOrderNumber(), exception);
        }
    }

}
