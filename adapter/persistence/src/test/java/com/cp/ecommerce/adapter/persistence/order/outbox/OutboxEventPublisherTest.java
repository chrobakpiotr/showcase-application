package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.util.Date;
import java.util.List;

import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.incoming.ExportOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.PublishOrderAnalyticsEventInPort;
import com.cp.ecommerce.domain.order.port.incoming.PublishOrderAuditEventInPort;
import com.cp.ecommerce.domain.order.port.incoming.RouteOrderNotificationInPort;
import com.cp.ecommerce.domain.order.port.incoming.SendMessageInPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test class for {@link OutboxEventPublisher}.
 */
@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

    @Mock
    private transient OutboxEventEntityRepository outboxEventEntityRepository;

    @Mock
    private transient ManageOrderInPort manageOrderInPort;

    @Mock
    private transient SendMessageInPort sendMessageInPort;

    @Mock
    private transient ExportOrderInPort exportOrderInPort;

    @Mock
    private transient PublishOrderAuditEventInPort publishOrderAuditEventInPort;

    @Mock
    private transient PublishOrderAnalyticsEventInPort publishOrderAnalyticsEventInPort;

    @Mock
    private transient RouteOrderNotificationInPort routeOrderNotificationInPort;

    @Test
    void shouldPublishPendingOutboxEvents() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity outboxEventEntity = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date())
                .build();
        final OutboxEventPublisher outboxEventPublisher = new OutboxEventPublisher(
                outboxEventEntityRepository,
                manageOrderInPort,
                sendMessageInPort,
                exportOrderInPort,
                publishOrderAuditEventInPort,
                publishOrderAnalyticsEventInPort,
                routeOrderNotificationInPort,
                executeInSimpleTransaction());
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(outboxEventEntity));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);

        outboxEventPublisher.publishPendingEvents();

        final ArgumentCaptor<OutboxEventEntity> outboxEventEntityCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(sendMessageInPort, times(1)).sendMessage(order);
        verify(exportOrderInPort, times(1)).exportOrder(order);
        verify(publishOrderAuditEventInPort, times(1)).publishAuditEvent(order);
        verify(publishOrderAnalyticsEventInPort, times(1)).publishAnalyticsEvent(order);
        verify(routeOrderNotificationInPort, times(1)).routeNotification(order);
        verify(outboxEventEntityRepository, times(1)).save(outboxEventEntityCaptor.capture());
        assertThat(outboxEventEntityCaptor.getValue().getStatus()).isEqualTo(OutboxEventStatus.SENT);
        assertThat(outboxEventEntityCaptor.getValue().getSentDate()).isNotNull();
    }

    @Test
    void shouldLeaveEventPendingAndContinueWhenPublishingFails() {

        final Order failedOrder = OrderBuilder.mockOrder();
        final Order successfulOrder = Order.builder()
                .remarks(failedOrder.getRemarks())
                .orderNumber("5678")
                .created(failedOrder.getCreated())
                .customer(failedOrder.getCustomer())
                .build();
        final OutboxEventEntity failedEvent = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(failedOrder.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date(1L))
                .build();
        final OutboxEventEntity successfulEvent = OutboxEventEntity.builder()
                .id(2L)
                .orderNumber(successfulOrder.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date(2L))
                .build();
        final OutboxEventPublisher outboxEventPublisher = new OutboxEventPublisher(
                outboxEventEntityRepository,
                manageOrderInPort,
                sendMessageInPort,
                exportOrderInPort,
                publishOrderAuditEventInPort,
                publishOrderAnalyticsEventInPort,
                routeOrderNotificationInPort,
                executeInSimpleTransaction());
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(failedEvent, successfulEvent));
        when(manageOrderInPort.findOrder(failedOrder.getOrderNumber())).thenReturn(failedOrder);
        when(manageOrderInPort.findOrder(successfulOrder.getOrderNumber())).thenReturn(successfulOrder);
        doThrow(new IllegalStateException("RabbitMQ unavailable")).when(sendMessageInPort).sendMessage(failedOrder);

        assertDoesNotThrow(outboxEventPublisher::publishPendingEvents);

        verify(sendMessageInPort, times(1)).sendMessage(failedOrder);
        verify(sendMessageInPort, times(1)).sendMessage(successfulOrder);
        verify(outboxEventEntityRepository, times(1)).save(successfulEvent);
        verify(outboxEventEntityRepository, never()).save(failedEvent);
        assertThat(failedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(failedEvent.getSentDate()).isNull();
        assertThat(successfulEvent.getStatus()).isEqualTo(OutboxEventStatus.SENT);
        assertThat(successfulEvent.getSentDate()).isNotNull();
    }

    @Test
    void shouldStillMarkEventSentWhenS3ExportFails() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity outboxEventEntity = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date())
                .build();
        final OutboxEventPublisher outboxEventPublisher = new OutboxEventPublisher(
                outboxEventEntityRepository,
                manageOrderInPort,
                sendMessageInPort,
                exportOrderInPort,
                publishOrderAuditEventInPort,
                publishOrderAnalyticsEventInPort,
                routeOrderNotificationInPort,
                executeInSimpleTransaction());
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(outboxEventEntity));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        doThrow(new RuntimeException("S3 unavailable")).when(exportOrderInPort).exportOrder(order);

        assertDoesNotThrow(outboxEventPublisher::publishPendingEvents);

        verify(outboxEventEntityRepository, times(1)).save(outboxEventEntity);
        assertThat(outboxEventEntity.getStatus()).isEqualTo(OutboxEventStatus.SENT);
    }

    @Test
    void shouldStillMarkEventSentWhenSqsAuditFails() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity outboxEventEntity = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date())
                .build();
        final OutboxEventPublisher outboxEventPublisher = new OutboxEventPublisher(
                outboxEventEntityRepository,
                manageOrderInPort,
                sendMessageInPort,
                exportOrderInPort,
                publishOrderAuditEventInPort,
                publishOrderAnalyticsEventInPort,
                routeOrderNotificationInPort,
                executeInSimpleTransaction());
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(outboxEventEntity));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        doThrow(new RuntimeException("SQS unavailable")).when(publishOrderAuditEventInPort).publishAuditEvent(order);

        assertDoesNotThrow(outboxEventPublisher::publishPendingEvents);

        verify(outboxEventEntityRepository, times(1)).save(outboxEventEntity);
        assertThat(outboxEventEntity.getStatus()).isEqualTo(OutboxEventStatus.SENT);
    }

    @Test
    void shouldStillMarkEventSentWhenKafkaAnalyticsPublishFails() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity outboxEventEntity = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date())
                .build();
        final OutboxEventPublisher outboxEventPublisher = new OutboxEventPublisher(
                outboxEventEntityRepository,
                manageOrderInPort,
                sendMessageInPort,
                exportOrderInPort,
                publishOrderAuditEventInPort,
                publishOrderAnalyticsEventInPort,
                routeOrderNotificationInPort,
                executeInSimpleTransaction());
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(outboxEventEntity));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        doThrow(new RuntimeException("Kafka unavailable")).when(publishOrderAnalyticsEventInPort).publishAnalyticsEvent(order);

        assertDoesNotThrow(outboxEventPublisher::publishPendingEvents);

        verify(outboxEventEntityRepository, times(1)).save(outboxEventEntity);
        assertThat(outboxEventEntity.getStatus()).isEqualTo(OutboxEventStatus.SENT);
    }

    @Test
    void shouldStillMarkEventSentWhenCamelRoutingFails() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity outboxEventEntity = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date())
                .build();
        final OutboxEventPublisher outboxEventPublisher = new OutboxEventPublisher(
                outboxEventEntityRepository,
                manageOrderInPort,
                sendMessageInPort,
                exportOrderInPort,
                publishOrderAuditEventInPort,
                publishOrderAnalyticsEventInPort,
                routeOrderNotificationInPort,
                executeInSimpleTransaction());
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(outboxEventEntity));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        doThrow(new RuntimeException("Camel routing unavailable")).when(routeOrderNotificationInPort).routeNotification(order);

        assertDoesNotThrow(outboxEventPublisher::publishPendingEvents);

        verify(outboxEventEntityRepository, times(1)).save(outboxEventEntity);
        assertThat(outboxEventEntity.getStatus()).isEqualTo(OutboxEventStatus.SENT);
    }

    private TransactionOperations executeInSimpleTransaction() {

        return new TransactionOperations() {

            @Override
            public <T> T execute(final TransactionCallback<T> action) {

                return action.doInTransaction(newTransactionStatus());
            }
        };
    }

    private TransactionStatus newTransactionStatus() {

        return new SimpleTransactionStatus();
    }

}
