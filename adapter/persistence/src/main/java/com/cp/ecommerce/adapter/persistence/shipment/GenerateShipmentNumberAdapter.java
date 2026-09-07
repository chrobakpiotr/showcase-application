package com.cp.ecommerce.adapter.persistence.shipment;

import java.util.UUID;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.shipment.port.outgoing.GenerateShipmentNumberOutPort;

/**
 * Implementation of {@link GenerateShipmentNumberOutPort}.
 */
@PersistenceAdapter
class GenerateShipmentNumberAdapter implements GenerateShipmentNumberOutPort {

    private static final String SHIPMENT_NUMBER_PREFIX = "SHIP-";

    @Override
    public String generate() {

        return SHIPMENT_NUMBER_PREFIX + UUID.randomUUID();
    }

}
