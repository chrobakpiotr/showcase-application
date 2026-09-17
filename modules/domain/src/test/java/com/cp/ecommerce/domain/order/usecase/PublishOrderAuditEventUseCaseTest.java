package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.PublishOrderAuditEventOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

/**
 * Tests for {@link PublishOrderAuditEventUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class PublishOrderAuditEventUseCaseTest {

    @Mock
    private transient PublishOrderAuditEventOutPort publishOrderAuditEventOutPort;

    @InjectMocks
    private transient PublishOrderAuditEventUseCase publishOrderAuditEventUseCase;

    @Test
    void shouldDelegateAuditEventPublishing() {

        final Order order = TestDomainObjectFactory.validOrder();

        publishOrderAuditEventUseCase.publishAuditEvent(order);

        verify(publishOrderAuditEventOutPort).publish(order);
    }

}
