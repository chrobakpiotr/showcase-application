package com.cp.ecommerce.domain.shipment;

import java.time.Instant;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ShipmentOperation {

    String operationId;

    String shipmentNumber;

    ShipmentStatus expectedStatus;

    ShipmentStatus resultStatus;

    Instant dispatchedDate;

    Instant estimatedDeliveryDate;

    Instant deliveredDate;

    long resultVersion;
}
