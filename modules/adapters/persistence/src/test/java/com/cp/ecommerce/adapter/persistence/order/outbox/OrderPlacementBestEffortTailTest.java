package com.cp.ecommerce.adapter.persistence.order.outbox;

import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderPlacementBestEffortTailTest {

    @Mock
    private OrderPlacementDispatchManager orderPlacementDispatchManager;

    @Mock
    private ExportOrderInPort exportOrderInPort;

    @Mock
    private PublishOrderAuditEventInPort publishOrderAuditEventInPort;

    @Mock
    private PublishOrderAnalyticsEventInPort publishOrderAnalyticsEventInPort;

    @Mock
    private ClassifyOrderRemarksInPort classifyOrderRemarksInPort;

    @Mock
    private DetectDuplicateOrderInPort detectDuplicateOrderInPort;

    @Mock
    private SagaMetrics sagaMetrics;

    private OrderPlacementBestEffortTail tail;

    @BeforeEach
    void setUp() {

        tail = new OrderPlacementBestEffortTail(
                orderPlacementDispatchManager,
                exportOrderInPort,
                publishOrderAuditEventInPort,
                publishOrderAnalyticsEventInPort,
                classifyOrderRemarksInPort,
                detectDuplicateOrderInPort,
                sagaMetrics);
        org.mockito.Mockito.lenient()
                .when(classifyOrderRemarksInPort.classifyRemarks(any()))
                .thenReturn(RemarksTriageResult.standard("No remarks to classify."));
        org.mockito.Mockito.lenient()
                .when(detectDuplicateOrderInPort.detectDuplicate(any()))
                .thenReturn(DuplicateOrderCheckResult.none());
    }

    @Test
    void shouldRunEveryIndependentTailStep() {

        final Order order = OrderBuilder.mockOrder();

        tail.run(order);

        verify(orderPlacementDispatchManager).enqueue(order);
        verify(exportOrderInPort).exportOrder(order);
        verify(publishOrderAuditEventInPort).publishAuditEvent(order);
        verify(publishOrderAnalyticsEventInPort).publishAnalyticsEvent(order);
        verify(classifyOrderRemarksInPort).classifyRemarks(order);
        verify(detectDuplicateOrderInPort).detectDuplicate(order);
        verify(sagaMetrics).recordRemarksClassification(RemarksTriageCategory.STANDARD);
        verify(sagaMetrics).recordDuplicateOrderDetection(false);
    }

    @Test
    void shouldContainEverySimpleIntegrationFailure() {

        final Order order = OrderBuilder.mockOrder();
        doThrow(new IllegalStateException("s3 unavailable")).when(exportOrderInPort).exportOrder(order);
        doThrow(new IllegalStateException("sqs unavailable")).when(publishOrderAuditEventInPort).publishAuditEvent(order);
        doThrow(new IllegalStateException("kafka unavailable")).when(publishOrderAnalyticsEventInPort)
                .publishAnalyticsEvent(order);

        assertDoesNotThrow(() -> tail.run(order));

        verify(sagaMetrics).recordStepDuration(eq("s3-export"), any(), eq(false));
        verify(sagaMetrics).recordStepDuration(eq("sqs-audit"), any(), eq(false));
        verify(sagaMetrics).recordStepDuration(eq("kafka-analytics"), any(), eq(false));
    }

    @Test
    void shouldPropagateDurableDispatchEnqueueFailure() {

        final Order order = OrderBuilder.mockOrder();
        doThrow(new IllegalStateException("dispatch persistence unavailable")).when(orderPlacementDispatchManager)
                .enqueue(order);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tail.run(order))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dispatch persistence unavailable");
    }

    @Test
    void shouldContainRemarksFailureAndRecordSuspiciousResult() {

        final Order order = OrderBuilder.mockOrder();
        doThrow(new IllegalStateException("AI unavailable"))
                .doReturn(
                        RemarksTriageResult.builder()
                                .category(RemarksTriageCategory.SUSPICIOUS)
                                .rationale("Requests shipping to an address different from billing.")
                                .build())
                .when(classifyOrderRemarksInPort)
                .classifyRemarks(order);

        assertDoesNotThrow(() -> tail.run(order));
        tail.run(order);

        verify(sagaMetrics).recordStepDuration(eq("ai-remarks-triage"), any(), eq(false));
        verify(sagaMetrics).recordRemarksClassification(RemarksTriageCategory.SUSPICIOUS);
    }

    @Test
    void shouldContainDuplicateFailureAndRecordDuplicateResult() {

        final Order order = OrderBuilder.mockOrder();
        doThrow(new IllegalStateException("AI unavailable"))
                .doReturn(
                        DuplicateOrderCheckResult.builder()
                                .duplicate(true)
                                .matchedOrderNumber("PRE-EXISTING-1")
                                .similarityScore(0.99)
                                .rationale("Remarks nearly identical to a recent order from the same customer.")
                                .build())
                .when(detectDuplicateOrderInPort)
                .detectDuplicate(order);

        assertDoesNotThrow(() -> tail.run(order));
        tail.run(order);

        verify(sagaMetrics).recordStepDuration(eq("ai-duplicate-order-detection"), any(), eq(false));
        verify(sagaMetrics).recordDuplicateOrderDetection(true);
    }
}
