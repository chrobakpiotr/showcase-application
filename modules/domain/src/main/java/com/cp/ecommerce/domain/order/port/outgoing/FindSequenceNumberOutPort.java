package com.cp.ecommerce.domain.order.port.outgoing;

/**
 * Find sequence outgoing port.
 */
public interface FindSequenceNumberOutPort {

    /**
     * Finding sequence number.
     *
     * @return sequence number.
     */
    long find();

}
