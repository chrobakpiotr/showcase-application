package com.cp.ecommerce.domain.order.port.outgoing;

import com.cp.ecommerce.domain.order.IdempotencyReservation;

/**
 * Idempotency key tracking outgoing port.
 *
 * <p>
 * Backs the optional {@code Idempotency-Key} request header on order placement: lets a client safely retry a request (for
 * example after a timeout where it never saw the response) without risking the order being placed twice.
 */
public interface IdempotencyKeyOutPort {

    /**
     * Atomically reserves the given idempotency key for a request carrying the given fingerprint.
     *
     * @param key client-supplied idempotency key.
     * @param fingerprint stable hash of the request's business-meaningful content.
     * @return {@link IdempotencyReservation.Outcome#RESERVED} if this call is the first (or the only surviving, once a stale
     *         in-flight attempt is taken over) to use {@code key} and the caller should proceed;
     *         {@link IdempotencyReservation.Outcome#DUPLICATE} if an identical request already completed under this key;
     *         {@link IdempotencyReservation.Outcome#CONFLICT} if {@code key} was already used for a different request, or a
     *         request for it is still in flight.
     */
    IdempotencyReservation reserve(final String key, final String fingerprint);

    /**
     * Marks a previously {@link #reserve(String, String) reserved} key as completed with the resulting order number, so future
     * retries under the same key can replay the same response instead of placing a second order.
     *
     * @param key idempotency key previously reserved.
     * @param orderNumber order number the reserved request produced.
     */
    void complete(final String key, final String orderNumber);

}
