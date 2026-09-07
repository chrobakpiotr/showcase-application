package com.cp.ecommerce.domain.shipment.port.outgoing;

/**
 * Outgoing port for generating tracking numbers.
 */
public interface GenerateTrackingNumberOutPort {

    String generate(String carrier);

}
