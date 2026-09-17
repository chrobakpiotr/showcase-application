package com.cp.ecommerce.domain.order.port.outgoing;

/**
 * Generate order outgoing port.
 */
public interface GenerateOrderNumberOutPort {

    /**
     * This method is meant to generate order number based on order number template.
     *
     * @param sequenceNumber sequence number.
     * @return order number.
     */
    String generate(long sequenceNumber);

}
