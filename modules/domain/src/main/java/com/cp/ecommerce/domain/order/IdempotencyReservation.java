package com.cp.ecommerce.domain.order;

/**
 * Outcome of {@link com.cp.ecommerce.domain.order.port.outgoing.IdempotencyKeyOutPort#reserve(String, String)}.
 *
 * @param outcome which of the possible outcomes this reservation attempt resulted in.
 * @param existingOrderNumber order number of the previously completed request for this key; only meaningful when
 *            {@code outcome} is {@link Outcome#DUPLICATE}.
 */
public record IdempotencyReservation(Outcome outcome, String existingOrderNumber) {

    public static IdempotencyReservation reserved() {

        return new IdempotencyReservation(Outcome.RESERVED, null);
    }

    public static IdempotencyReservation duplicate(final String existingOrderNumber) {

        return new IdempotencyReservation(Outcome.DUPLICATE, existingOrderNumber);
    }

    public static IdempotencyReservation conflict() {

        return new IdempotencyReservation(Outcome.CONFLICT, null);
    }

    /**
     * Possible outcomes of attempting to reserve an idempotency key.
     */
    public enum Outcome {

        /**
         * No prior request is known for this key (or the prior attempt is considered abandoned/stale): the caller should
         * proceed and eventually call {@link com.cp.ecommerce.domain.order.port.outgoing.IdempotencyKeyOutPort#complete}.
         */
        RESERVED,

        /**
         * An identical request already completed under this key: the caller should replay {@code existingOrderNumber} instead
         * of placing a second order.
         */
        DUPLICATE,

        /**
         * This key was already used for a materially different request, or a request for it is still being processed.
         */
        CONFLICT

    }

}
