package com.cp.ecommerce.adapter.mail.message;

import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.domain.order.Order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.mail.MessagingException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Customer message creation test with mocked customer details.
 */
@ExtendWith(MockitoExtension.class)
class CustomerMessageMockTest {

    @InjectMocks
    private transient EmailMessageFactory emailMessageFactory;

    @Mock
    private transient CustomerMessageCreator customerMessageCreator;

    @Test
    void shouldPrepareEmailForCustomer() throws MessagingException {

        final Order order = OrderBuilder.mockOrder();
        given(customerMessageCreator.createMessage(order)).willReturn(any());
        emailMessageFactory.createEmailMessage(order);
        verify(customerMessageCreator, times(1)).createMessage(any());
    }

}
