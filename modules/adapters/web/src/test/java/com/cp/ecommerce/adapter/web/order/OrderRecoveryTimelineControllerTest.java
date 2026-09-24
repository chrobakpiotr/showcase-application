package com.cp.ecommerce.adapter.web.order;

import java.time.Instant;
import java.util.List;

import com.cp.ecommerce.domain.order.recovery.OrderRecoveryTimelineEntry;
import com.cp.ecommerce.domain.order.recovery.OrderRecoveryTimelineState;
import com.cp.ecommerce.domain.order.usecase.FindOrderRecoveryTimelineUseCase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderRecoveryTimelineControllerTest {

    private static final String ORDER_NUMBER = "ORD-1001";

    @Mock
    private FindOrderRecoveryTimelineUseCase useCase;

    @InjectMocks
    private OrderRecoveryTimelineController controller;

    @Test
    void shouldReturnSafeReadOnlyProjection() {

        given(useCase.find(ORDER_NUMBER, 0, 20)).willReturn(
                List.of(
                        new OrderRecoveryTimelineEntry(
                                "FULFILLMENT",
                                "RABBITMQ",
                                OrderRecoveryTimelineState.COMPLETED,
                                Instant.parse("2026-09-24T12:00:00Z"),
                                "ORDER-FULFILLMENT:ORD-1001",
                                "FULFILLMENT RABBITMQ state RECEIVED")));

        final var result = controller.find(ORDER_NUMBER, 0, 20);

        assertThat(result.orderNumber()).isEqualTo(ORDER_NUMBER);
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().state()).isEqualTo(OrderRecoveryTimelineState.COMPLETED);
        verify(useCase).find(ORDER_NUMBER, 0, 20);
    }

    @Test
    void shouldRejectUnboundedPagination() {

        assertThatThrownBy(() -> controller.find(ORDER_NUMBER, -1, 20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.find(ORDER_NUMBER, 0, 101)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.find(ORDER_NUMBER, 10_001, 20)).isInstanceOf(ResponseStatusException.class);
    }
}
