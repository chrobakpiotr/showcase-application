package com.cp.ecommerce.domain.order.usecase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import com.cp.ecommerce.domain.customer.Address;
import com.cp.ecommerce.domain.customer.Contact;
import com.cp.ecommerce.domain.customer.Customer;
import com.cp.ecommerce.domain.order.IdempotencyReservation;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderLineItem;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.order.PlaceOrderResult;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.outgoing.IdempotencyKeyOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.LogOrderOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;
import com.cp.ecommerce.foundation.exception.IdempotencyKeyConflictException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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

    @Mock
    private transient UnaryOperator<Order> prepare;

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
        when(idempotencyKeyOutPort.reserve(eq(IDEMPOTENCY_KEY), any(), any())).thenReturn(IdempotencyReservation.reserved());
        when(manageOrderInPort.saveOrder(any(Order.class))).thenReturn(savedOrder);

        final PlaceOrderResult result = placeOrderUseCase.placeOrder(order, IDEMPOTENCY_KEY);

        assertEquals(savedOrder.getOrderNumber(), result.orderNumber());
        assertTrue(result.newlyPlaced());
        verify(idempotencyKeyOutPort).complete(IDEMPOTENCY_KEY, savedOrder.getOrderNumber());
    }

    @Test
    void shouldReplayStoredOrderNumberWhenIdempotencyKeyIsDuplicate() {

        final Order order = TestDomainObjectFactory.validOrder();
        when(idempotencyKeyOutPort.reserve(eq(IDEMPOTENCY_KEY), any(), any()))
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
        when(idempotencyKeyOutPort.reserve(eq(IDEMPOTENCY_KEY), any(), any())).thenReturn(IdempotencyReservation.conflict());

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
        when(idempotencyKeyOutPort.reserve(eq(IDEMPOTENCY_KEY), any(), any())).thenReturn(IdempotencyReservation.reserved());
        when(manageOrderInPort.saveOrder(any(Order.class))).thenReturn(order);

        placeOrderUseCase.placeOrder(order, IDEMPOTENCY_KEY);
        placeOrderUseCase.placeOrder(order, IDEMPOTENCY_KEY);
        placeOrderUseCase.placeOrder(differentOrder, IDEMPOTENCY_KEY);

        final ArgumentCaptor<String> fingerprintCaptor = ArgumentCaptor.forClass(String.class);
        verify(idempotencyKeyOutPort, times(3)).reserve(eq(IDEMPOTENCY_KEY), fingerprintCaptor.capture(), any());
        final String firstFingerprint = fingerprintCaptor.getAllValues().get(0);
        final String repeatedFingerprint = fingerprintCaptor.getAllValues().get(1);
        final String differentFingerprint = fingerprintCaptor.getAllValues().get(2);

        assertTrue(firstFingerprint.matches("[0-9a-f]{64}"), "fingerprint must be a 64-char SHA-256 hex digest");
        assertEquals(firstFingerprint, repeatedFingerprint, "fingerprint must be stable for identical content");
        assertNotEquals(firstFingerprint, differentFingerprint, "fingerprint must change when content changes");
    }

    @Test
    void shouldSkipPreparationForReplayAndConflict() {

        final Order order = TestDomainObjectFactory.validOrder();
        when(idempotencyKeyOutPort.reserve(eq(IDEMPOTENCY_KEY), any(), any()))
                .thenReturn(IdempotencyReservation.duplicate("ORD-1"), IdempotencyReservation.conflict());
        assertEquals("ORD-1", placeOrderUseCase.placeOrder(order, IDEMPOTENCY_KEY, prepare).orderNumber());
        assertThrows(
                IdempotencyKeyConflictException.class,
                () -> placeOrderUseCase.placeOrder(order, IDEMPOTENCY_KEY, prepare));
        verifyNoInteractions(prepare, manageOrderInPort);
    }

    @Test
    void shouldPrepareExactlyOnceBeforeSavingANewAttempt() {

        final Order order = TestDomainObjectFactory.validOrder();
        when(idempotencyKeyOutPort.reserve(eq(IDEMPOTENCY_KEY), any(), any())).thenReturn(IdempotencyReservation.reserved());
        when(prepare.apply(order)).thenReturn(order);
        when(manageOrderInPort.saveOrder(order)).thenReturn(order);
        placeOrderUseCase.placeOrder(order, IDEMPOTENCY_KEY, prepare);
        final org.mockito.InOrder ordering = org.mockito.Mockito.inOrder(prepare, manageOrderInPort, idempotencyKeyOutPort);
        ordering.verify(idempotencyKeyOutPort).reserve(eq(IDEMPOTENCY_KEY), any(), any());
        ordering.verify(prepare).apply(order);
        ordering.verify(manageOrderInPort).saveOrder(order);
        ordering.verify(idempotencyKeyOutPort).complete(IDEMPOTENCY_KEY, order.getOrderNumber());
    }

    @Test
    void shouldNotPersistOrCompleteWhenPreparationFails() {

        final Order order = TestDomainObjectFactory.validOrder();
        when(idempotencyKeyOutPort.reserve(eq(IDEMPOTENCY_KEY), any(), any())).thenReturn(IdempotencyReservation.reserved());
        when(prepare.apply(order)).thenThrow(new IllegalStateException("stock unavailable"));
        assertThrows(IllegalStateException.class, () -> placeOrderUseCase.placeOrder(order, IDEMPOTENCY_KEY, prepare));
        verifyNoInteractions(manageOrderInPort);
        verify(idempotencyKeyOutPort, never()).complete(any(), any());
    }

    @ParameterizedTest
    @MethodSource("changedRequests")
    void shouldFingerprintEveryAcceptedField(final Consumer<Order.OrderBuilder> change) {

        final Order.OrderBuilder changed = request();
        change.accept(changed);
        assertNotEquals(fingerprintOf(request().build()), fingerprintOf(changed.build()));
    }

    private static Stream<Consumer<Order.OrderBuilder>> changedRequests() {

        return Stream.of(
                builder -> builder.remarks("different"),
                builder -> builder.remarks(null),
                builder -> builder.created(Instant.ofEpochMilli(TestDomainObjectFactory.TEST_CREATED.toEpochMilli() + 1)),
                builder -> builder.created(null),
                builder -> builder.paymentMethod(PaymentMethod.PAYPAL),
                builder -> builder.couponCode("SAVE10"),
                builder -> builder.customer(
                        customer(
                                contact("Other", "john.doe@test.com", "+48 123 456 789"),
                                address("Main Street 1", "12-345", "Warsaw", "PL"))),
                builder -> builder.customer(
                        customer(
                                contact("John Doe", "other@test.com", "+48 123 456 789"),
                                address("Main Street 1", "12-345", "Warsaw", "PL"))),
                builder -> builder.customer(
                        customer(
                                contact("John Doe", "john.doe@test.com", "123"),
                                address("Main Street 1", "12-345", "Warsaw", "PL"))),
                builder -> builder.customer(
                        customer(
                                contact("John Doe", "john.doe@test.com", "+48 123 456 789"),
                                address("Other", "12-345", "Warsaw", "PL"))),
                builder -> builder.customer(
                        customer(
                                contact("John Doe", "john.doe@test.com", "+48 123 456 789"),
                                address("Main Street 1", "other", "Warsaw", "PL"))),
                builder -> builder.customer(
                        customer(
                                contact("John Doe", "john.doe@test.com", "+48 123 456 789"),
                                address("Main Street 1", "12-345", "Other", "PL"))),
                builder -> builder.customer(
                        customer(
                                contact("John Doe", "john.doe@test.com", "+48 123 456 789"),
                                address("Main Street 1", "12-345", "Warsaw", "DE"))),
                builder -> builder.customer(Customer.builder().build()),
                builder -> builder.items(List.of(item("OTHER", "Wireless Mouse", "29.99", 2))),
                builder -> builder.items(List.of(item("SKU-1001", "Wireless Mouse", "29.99", 3))),
                builder -> builder.items(List.of()));
    }

    @Test
    void shouldIgnoreDerivedValuesButPreserveNullsAndItemBoundaries() {

        final String original = fingerprintOf(request().build());
        assertEquals("4903a49c1be8113dad64ae7d97e017474adf37ca3fa512d6d0fb85db1a359309", original);
        assertEquals(original, fingerprintOf(request().discountAmount(BigDecimal.TEN).orderNumber("server-generated").build()));
        assertEquals(original, fingerprintOf(request().items(List.of(item("SKU-1001", "Other", "29.99", 2))).build()));
        assertEquals(original, fingerprintOf(request().items(List.of(item("SKU-1001", "Wireless Mouse", "30.00", 2))).build()));
        assertEquals(
                original,
                fingerprintOf(
                        request()
                                .customer(
                                        customer(
                                                contact("John Doe", "john.doe@test.com", "+48 123 456 789"),
                                                address("Main Street 1", "12-345", "Warsaw", "PL")))
                                .build()));
        assertNotEquals(fingerprintOf(request().remarks(null).build()), fingerprintOf(request().remarks("null").build()));
        assertNotEquals(fingerprintOf(request().remarks("\uD800").build()), fingerprintOf(request().remarks("?").build()));
        final OrderLineItem first = item("A", "B:C", "1.00", 1);
        final OrderLineItem second = item("A:B", "C", "1.00", 1);
        assertNotEquals(
                fingerprintOf(request().items(List.of(first)).build()),
                fingerprintOf(request().items(List.of(second)).build()));
        assertNotEquals(
                fingerprintOf(request().items(List.of(first, second)).build()),
                fingerprintOf(request().items(List.of(second, first)).build()));
    }

    @Test
    void shouldPreserveLegacyV2FingerprintForHistoricalReplay() {

        assertEquals(
                "7c2150b1fef773c64f247ff2287bc4f3978a55ee22044e4a6c96728037c2927f",
                legacyFingerprintOf(request().build()));
    }

    private String fingerprintOf(final Order order) {

        final AtomicReference<String> captured = new AtomicReference<>();
        when(idempotencyKeyOutPort.reserve(eq(IDEMPOTENCY_KEY), any(), any())).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(1));
            return IdempotencyReservation.conflict();
        });
        assertThrows(IdempotencyKeyConflictException.class, () -> placeOrderUseCase.placeOrder(order, IDEMPOTENCY_KEY));
        return captured.get();
    }

    private String legacyFingerprintOf(final Order order) {

        final AtomicReference<String> captured = new AtomicReference<>();
        when(idempotencyKeyOutPort.reserve(eq(IDEMPOTENCY_KEY), any(), any())).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(2));
            return IdempotencyReservation.conflict();
        });
        assertThrows(IdempotencyKeyConflictException.class, () -> placeOrderUseCase.placeOrder(order, IDEMPOTENCY_KEY));
        return captured.get();
    }

    private static Order.OrderBuilder request() {

        final Order original = TestDomainObjectFactory.validOrder();
        return Order.builder()
                .remarks(original.getRemarks())
                .created(original.getCreated())
                .customer(original.getCustomer())
                .items(original.getItems())
                .paymentMethod(original.getPaymentMethod());
    }

    private static Customer customer(final Contact contact, final Address address) {

        return Customer.builder().contact(contact).address(address).build();
    }

    private static Contact contact(final String name, final String email, final String phone) {

        return Contact.builder().fullName(name).email(email).phone(phone).build();
    }

    private static Address address(final String street, final String postalCode, final String city, final String country) {

        return Address.builder().street(street).postalCode(postalCode).city(city).countryCode(country).build();
    }

    private static OrderLineItem item(final String sku, final String name, final String price, final int quantity) {

        return OrderLineItem.builder().sku(sku).productName(name).unitPrice(new BigDecimal(price)).quantity(quantity).build();
    }

}
