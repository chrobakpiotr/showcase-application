package com.cp.ecommerce.domain.order.usecase;

import java.time.Instant;
import java.util.List;

import com.cp.ecommerce.domain.order.port.outgoing.FindOrderRecoveryTimelineOutPort;
import com.cp.ecommerce.domain.order.recovery.OrderRecoveryTimelineEntry;
import com.cp.ecommerce.domain.order.recovery.OrderRecoveryTimelineState;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FindOrderRecoveryTimelineUseCaseTest {

    private static final String ORDER_NUMBER = "ORD-1001";

    @Mock
    private FindOrderRecoveryTimelineOutPort outPort;

    @InjectMocks
    private FindOrderRecoveryTimelineUseCase useCase;

    @Test
    void shouldDelegateBoundedTimelineRead() {

        final List<OrderRecoveryTimelineEntry> expected = List.of(
                new OrderRecoveryTimelineEntry(
                        "FULFILLMENT",
                        "RABBITMQ",
                        OrderRecoveryTimelineState.COMPLETED,
                        Instant.parse("2026-09-24T12:00:00Z"),
                        "ORDER-FULFILLMENT:ORD-1001",
                        "FULFILLMENT RABBITMQ state RECEIVED"));

        given(outPort.find(ORDER_NUMBER, 2, 25)).willReturn(expected);

        assertThat(useCase.find(ORDER_NUMBER, 2, 25)).isSameAs(expected);
        verify(outPort).find(ORDER_NUMBER, 2, 25);
    }
}
