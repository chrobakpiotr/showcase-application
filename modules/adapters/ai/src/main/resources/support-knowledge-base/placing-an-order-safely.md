# Placing an order safely (Idempotency-Key)

When placing an order, a client may optionally send an `Idempotency-Key` header - any client-generated token unique to that
one order-placement attempt. If a request with the same key is sent again (for example because a network request timed out
and the client's app retried it automatically), the platform recognizes the repeat and returns the result of the original
attempt instead of creating a second order. This is what prevents a shaky connection from accidentally placing the same
order twice.

Guidance for customers worried about a duplicate charge or a duplicate order after a failed checkout: reassure them that
retrying the exact same order (same checkout session) is safe by design, and offer to look up their order number via the
order-lookup tool to confirm only one order exists, if they can provide it.

# Remarks

An order may include free-text "remarks" (for example, delivery instructions). Remarks are optional and limited to 800
characters.
