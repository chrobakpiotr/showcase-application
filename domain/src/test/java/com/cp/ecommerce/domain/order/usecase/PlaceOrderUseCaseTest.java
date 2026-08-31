package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.adapter.common.exception.IdempotencyKeyConflictException;
import com.cp.ecommerce.domain.order.IdempotencyReservation;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.PlaceOrderResult;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.outgoing.IdempotencyKeyOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.LogOrderOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link PlaceOrderUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class PlaceOrderUseCaseTest {

    private static final String IDEMPOTENCY_KEY = "a-client-generated-key";

    @Mock
    private transient ManageOrderInPort manageOrderInPort;

    @Mock
    private transient LogOrderOutPort logOrderOutPort;

    @Mock
    private transient IdempotencyKeyOutPort idempotencyKeyOutPort;

    @InjectMocks
    private transient PlaceOrderUseCase placeOrderUseCase;

    @Test
    void shouldSaveAndLogOrderWithoutIdempotencyKey() {

        final Order order = TestDomainObjectFactory.validOrder();
        final Order savedOrder = TestDomainObjectFactory.validOrder();
        when(manageOrderInPort.saveOrder(any(Order.class))).thenReturn(savedOrder);

        final PlaceOrderResult result = placeOrderUseCase.placeOrder(order, null);

        verify(manageOrderInPort).saveOrder(order);
        verify(logOrderOutPort).log(savedOrder);
        verifyNoInteractions(idempotencyKeyOutPort);
        assertEquals(savedOrder.getOrderNumber(), result.orderNumber());
        assertTrue(result.newlyPlaced());
    }

    @Test
    void shouldPlaceAnotherOrderForACustomerEmailThatAlreadyPlacedOne() {

        // A returning customer must always be able to place further orders; nothing should key order placement off
        // whether their email was already used before (that used to silently drop every order but the first ever
        // placed for a given email - see git history of PlaceOrderUseCase for the bug this guards against).
        final Order firstOrder = TestDomainObjectFactory.validOrder();
        // Deliberately distinct from firstOrder (different order number/remarks) but for the same customer e-mail, so
        // the two stubs/verifications below can't collapse into each other via equals()-based Mockito matching.
        final Order secondOrder = Order.builder()
                .remarks("a later order from the same returning customer")
                .orderNumber("ORD-1002")
                .created(TestDomainObjectFactory.TEST_CREATED)
                .customer(TestDomainObjectFactory.validCustomer())
                .build();
        when(manageOrderInPort.saveOrder(firstOrder)).thenReturn(firstOrder);
        when(manageOrderInPort.saveOrder(secondOrder)).thenReturn(secondOrder);

        final PlaceOrderResult firstResult = placeOrderUseCase.placeOrder(firstOrder, null);
        final PlaceOrderResult secondResult = placeOrderUseCase.placeOrder(secondOrder, null);

        assertTrue(firstResult.newlyPlaced());
        assertTrue(secondResult.newlyPlaced());
        assertEquals(firstOrder.getOrderNumber(), firstResult.orderNumber());
        assertEquals(secondOrder.getOrderNumber(), secondResult.orderNumber());
        verify(manageOrderInPort).saveOrder(firstOrder);
        verify(manageOrderInPort).saveOrder(secondOrder);
    }

    @Test
    void shouldReserveAndCompleteIdempotencyKeyWhenRequestIsNew() {

        final Order order = TestDomainObjectFactory.validOrder();
        final Order savedOrder = TestDomainObjectFactory.validOrder();
        when(idempotencyKeyOutPort.reserve(eq(IDEMPOTENCY_KEY), any())).thenReturn(IdempotencyReservation.reserved());
        when(manageOrderInPort.saveOrder(any(Order.class))).thenReturn(savedOrder);

        final PlaceOrderResult result = placeOrderUseCase.placeOrder(order, IDEMPOTENCY_KEY);

        assertEquals(savedOrder.getOrderNumber(), result.orderNumber());
        assertTrue(result.newlyPlaced());
        verify(idempotencyKeyOutPort).complete(IDEMPOTENCY_KEY, savedOrder.getOrderNumber());
    }

    @Test
    void shouldReplayStoredOrderNumberWhenIdempotencyKeyIsDuplicate() {

        final Order order = TestDomainObjectFactory.validOrder();
        when(idempotencyKeyOutPort.reserve(eq(IDEMPOTENCY_KEY), any()))
                .thenReturn(IdempotencyReservation.duplicate(TestDomainObjectFactory.TEST_ORDER_NUMBER));

        final PlaceOrderResult result = placeOrderUseCase.placeOrder(order, IDEMPOTENCY_KEY);

        assertEquals(TestDomainObjectFactory.TEST_ORDER_NUMBER, result.orderNumber());
        assertFalse(result.newlyPlaced());
        verifyNoInteractions(manageOrderInPort, logOrderOutPort);
        verify(idempotencyKeyOutPort, never()).complete(any(), any());
    }

    @Test
    void shouldThrowConflictWhenIdempotencyKeyWasUsedForDifferentRequest() {

        final Order order = TestDomainObjectFactory.validOrder();
        when(idempotencyKeyOutPort.reserve(eq(IDEMPOTENCY_KEY), any())).thenReturn(IdempotencyReservation.conflict());

        assertThrows(IdempotencyKeyConflictException.class, () -> placeOrderUseCase.placeOrder(order, IDEMPOTENCY_KEY));

        verifyNoInteractions(manageOrderInPort, logOrderOutPort);
        verify(idempotencyKeyOutPort, never()).complete(any(), any());
    }

    @Test
    void shouldFingerprintClientControlledOrderContentAsStableSha256Hex() {

        final Order order = TestDomainObjectFactory.validOrder();
        final Order differentOrder = Order.builder()
                .remarks("a-completely-different-remark")
                .orderNumber(TestDomainObjectFactory.TEST_ORDER_NUMBER)
                .created(TestDomainObjectFactory.TEST_CREATED)
                .customer(TestDomainObjectFactory.validCustomer())
                .build();
        when(idempotencyKeyOutPort.reserve(eq(IDEMPOTENCY_KEY), any())).thenReturn(IdempotencyReservation.reserved());
        when(manageOrderInPort.saveOrder(any(Order.class))).thenReturn(order);

        placeOrderUseCase.placeOrder(order, IDEMPOTENCY_KEY);
        placeOrderUseCase.placeOrder(order, IDEMPOTENCY_KEY);
        placeOrderUseCase.placeOrder(differentOrder, IDEMPOTENCY_KEY);

        final ArgumentCaptor<String> fingerprintCaptor = ArgumentCaptor.forClass(String.class);
        verify(idempotencyKeyOutPort, times(3)).reserve(eq(IDEMPOTENCY_KEY), fingerprintCaptor.capture());
        final String firstFingerprint = fingerprintCaptor.getAllValues().get(0);
        final String repeatedFingerprint = fingerprintCaptor.getAllValues().get(1);
        final String differentFingerprint = fingerprintCaptor.getAllValues().get(2);

        assertTrue(firstFingerprint.matches("[0-9a-f]{64}"), "fingerprint must be a 64-char SHA-256 hex digest");
        assertEquals(firstFingerprint, repeatedFingerprint, "fingerprint must be stable for identical content");
        assertNotEquals(firstFingerprint, differentFingerprint, "fingerprint must change when content changes");
    }

}
