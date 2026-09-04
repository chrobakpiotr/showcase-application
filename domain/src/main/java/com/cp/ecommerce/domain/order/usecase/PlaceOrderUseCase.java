package com.cp.ecommerce.domain.order.usecase;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.adapter.common.exception.IdempotencyKeyConflictException;
import com.cp.ecommerce.domain.order.IdempotencyReservation;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.PlaceOrderResult;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.PlaceOrderInPort;
import com.cp.ecommerce.domain.order.port.outgoing.IdempotencyKeyOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.LogOrderOutPort;

import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
@RequiredArgsConstructor
@UseCase
public class PlaceOrderUseCase implements PlaceOrderInPort {

    private final ManageOrderInPort manageOrderInPort;

    private final LogOrderOutPort logOrderOutPort;

    private final IdempotencyKeyOutPort idempotencyKeyOutPort;

    @Override
    public PlaceOrderResult placeOrder(final Order order, final String idempotencyKey) {

        if (!StringUtils.hasText(idempotencyKey)) {

            return doPlaceOrder(order);
        }

        final IdempotencyReservation reservation = idempotencyKeyOutPort.reserve(idempotencyKey, fingerprint(order));

        return switch (reservation.outcome()) {
        case DUPLICATE -> {
            log.info("Idempotency-Key '{}' already processed, replaying stored result.", idempotencyKey);
            yield new PlaceOrderResult(reservation.existingOrderNumber(), false);
        }
        case CONFLICT -> throw new IdempotencyKeyConflictException(
                "Idempotency-Key '" + idempotencyKey
                        + "' cannot be reused for this request: it is still being processed, or was already used "
                        + "for a request with different content");
        case RESERVED -> {
            final PlaceOrderResult result = doPlaceOrder(order);
            idempotencyKeyOutPort.complete(idempotencyKey, result.orderNumber());
            yield result;
        }
        };
    }

    private PlaceOrderResult doPlaceOrder(final Order order) {

        log.info("Saving order data started...");
        final Order savedOrder = manageOrderInPort.saveOrder(order);
        log.info("Saving order completed.");

        log.info("Order's number: {}", savedOrder.getOrderNumber());
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
    @SneakyThrows(NoSuchAlgorithmException.class)
    private static String fingerprint(final Order order) {

        final String canonical = String.join(
                "|",
                String.valueOf(order.getRemarks()),
                String.valueOf(order.getCreated()),
                String.valueOf(order.getCustomer().getId()),
                String.valueOf(order.getCustomer().getContact().getEmail()),
                lineItemsFingerprint(order));
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    // Stable (order-preserving) representation of the order's line items, so an Idempotency-Key replay is only
    // considered a genuine retry of the same request if the items also match - not just the customer/remarks.
    private static String lineItemsFingerprint(final Order order) {

        return order.getItems()
                .stream()
                .map(item -> item.getSku() + ":" + item.getQuantity() + ":" + item.getUnitPrice())
                .collect(Collectors.joining(","));
    }

}
