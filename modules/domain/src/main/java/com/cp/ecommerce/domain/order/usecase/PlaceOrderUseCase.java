package com.cp.ecommerce.domain.order.usecase;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.cp.ecommerce.domain.customer.Address;
import com.cp.ecommerce.domain.customer.Contact;
import com.cp.ecommerce.domain.order.IdempotencyReservation;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.PlaceOrderResult;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.PlaceOrderInPort;
import com.cp.ecommerce.domain.order.port.outgoing.IdempotencyKeyOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.LogOrderOutPort;
import com.cp.ecommerce.foundation.annotation.UseCase;
import com.cp.ecommerce.foundation.exception.IdempotencyKeyConflictException;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

/**
 * Use case for placing order.
 *
 * <p>
 * This use case only guarantees that the order is durably saved (order row + {@code PENDING} outbox row written atomically, per
 * the transactional outbox pattern). Everything else the order placement fans out to - confirmation email, fulfillment
 * notification, export, audit, analytics - is deliberately left to the asynchronous order-placement saga
 * ({@code OrderPlacementSagaOrchestrator}) so that a slow/unavailable downstream dependency can never turn an order that was
 * actually placed into a failed HTTP response.
 *
 * <p>
 * When the caller supplies an {@code idempotencyKey}, this use case also makes the request safe to retry: the key is first
 * reserved via {@link IdempotencyKeyOutPort#reserve}, keyed together with a fingerprint of the order's client-controlled
 * content, so a retried request with the exact same payload replays the original order number instead of placing a second
 * order, while a retried request reusing the same key with a different payload is rejected as a conflict.
 */
@RequiredArgsConstructor
@UseCase
public class PlaceOrderUseCase implements PlaceOrderInPort {

    private final ManageOrderInPort manageOrderInPort;

    private final LogOrderOutPort logOrderOutPort;

    private final IdempotencyKeyOutPort idempotencyKeyOutPort;

    @Override
    public PlaceOrderResult placeOrder(final Order order, final String idempotencyKey) {

        return placeOrder(order, idempotencyKey, UnaryOperator.identity());
    }

    @Override
    public PlaceOrderResult placeOrder(final Order order, final String idempotencyKey, final UnaryOperator<Order> prepare) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {

            return doPlaceOrder(prepare.apply(order));
        }

        final IdempotencyReservation reservation = idempotencyKeyOutPort
                .reserve(idempotencyKey, fingerprint(order), legacyFingerprint(order));

        return switch (reservation.outcome()) {
        case DUPLICATE -> {
            yield new PlaceOrderResult(reservation.existingOrderNumber(), false);
        }
        case CONFLICT -> throw new IdempotencyKeyConflictException(
                "Idempotency-Key cannot be reused for this request: it is still being processed, or was already used "
                        + "for a request with different content");
        case RESERVED -> {
            final PlaceOrderResult result = doPlaceOrder(prepare.apply(order));
            idempotencyKeyOutPort.complete(idempotencyKey, result.orderNumber());
            yield result;
        }
        };
    }

    private PlaceOrderResult doPlaceOrder(final Order order) {

        final Order savedOrder = manageOrderInPort.saveOrder(order);

        logOrderOutPort.log(savedOrder);

        return new PlaceOrderResult(savedOrder.getOrderNumber(), true);
    }

    // Stable hash of the order's client-controlled fields, used to detect an Idempotency-Key being reused for a
    // materially different request rather than a genuine retry of the same one.
    //
    // @SneakyThrows: SHA-256 is a mandatory algorithm on every conforming JDK implementation (see the MessageDigest
    // javadoc), so NoSuchAlgorithmException is provably unreachable here. A real try/catch around it would be an
    // untestable branch that this module's 100% line/mutation coverage requirement can't be satisfied without
    // artificially forcing the "impossible" path in a test.
    private static String fingerprint(final Order order) {

        return fingerprint(order, "order-request-v3", lineItemsFingerprint(order));
    }

    private static String legacyFingerprint(final Order order) {

        return fingerprint(order, "order-request-v2", legacyLineItemsFingerprint(order));
    }

    @SneakyThrows(NoSuchAlgorithmException.class)
    private static String fingerprint(final Order order, final String version, final String lineItemsFingerprint) {

        final Optional<Contact> contact = Optional.ofNullable(order.getCustomer().getContact());
        final Optional<Address> address = Optional.ofNullable(order.getCustomer().getAddress());
        final String canonical = fields(
                version,
                order.getRemarks(),
                Optional.ofNullable(order.getCreated()).map(java.util.Date::getTime).orElse(null),
                contact.map(Contact::getFullName).orElse(null),
                contact.map(Contact::getEmail).orElse(null),
                contact.map(Contact::getPhone).orElse(null),
                address.map(Address::getStreet).orElse(null),
                address.map(Address::getPostalCode).orElse(null),
                address.map(Address::getCity).orElse(null),
                address.map(Address::getCountryCode).orElse(null),
                order.getPaymentMethod(),
                order.getCouponCode(),
                order.getItems().size(),
                lineItemsFingerprint);
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        final ByteBuffer encoded = ByteBuffer.allocate(canonical.length() * Character.BYTES);
        encoded.asCharBuffer().put(canonical);
        return HexFormat.of().formatHex(digest.digest(encoded.array()));
    }

    private static String lineItemsFingerprint(final Order order) {

        return order.getItems().stream().map(item -> fields(item.getSku(), item.getQuantity())).collect(Collectors.joining());
    }

    private static String legacyLineItemsFingerprint(final Order order) {

        return order.getItems()
                .stream()
                .map(item -> fields(item.getSku(), item.getProductName(), item.getQuantity(), item.getUnitPrice()))
                .collect(Collectors.joining());
    }

    private static String fields(final Object... values) {

        return Stream.of(values)
                .map(value -> value == null ? "-1:" : value.toString().length() + ":" + value)
                .collect(Collectors.joining());
    }

}
