package com.cp.ecommerce.domain.shipment;

import java.time.Instant;

import com.cp.ecommerce.foundation.annotation.DomainObject;
import com.cp.ecommerce.foundation.constant.ValidationConstants;
import com.cp.ecommerce.foundation.validation.ValidDomainObject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A single fulfillment-tracking record for one order.
 *
 * <p>
 * Like {@code payment.PaymentTransaction.orderNumber} and {@code returns.ReturnRequest.orderNumber}, this aggregate stores only
 * a bare {@link #orderNumber} reference rather than importing the Order aggregate itself. The authoritative order is resolved
 * by the web/composition layer before shipment creation.
 */
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@DomainObject
public class Shipment extends ValidDomainObject<Shipment> {

    @Size(max = ValidationConstants.SHIPMENT_NUMBER_MAX, message = ValidationConstants.INVALID_SHIPMENT_NUMBER)
    String shipmentNumber;

    @NotBlank(message = ValidationConstants.INVALID_SHIPMENT_ORDER_NUMBER)
    @Size(max = 40, message = ValidationConstants.INVALID_SHIPMENT_ORDER_NUMBER)
    String orderNumber;

    @NotBlank(message = ValidationConstants.INVALID_SHIPMENT_CARRIER)
    @Size(max = ValidationConstants.SHIPMENT_CARRIER_MAX, message = ValidationConstants.INVALID_SHIPMENT_CARRIER)
    String carrier;

    @NotBlank(message = ValidationConstants.INVALID_SHIPMENT_TRACKING_NUMBER)
    @Size(
            max = ValidationConstants.SHIPMENT_TRACKING_NUMBER_MAX,
            message = ValidationConstants.INVALID_SHIPMENT_TRACKING_NUMBER)
    String trackingNumber;

    @NotNull(message = ValidationConstants.INVALID_SHIPMENT_STATUS)
    @Builder.Default
    ShipmentStatus status = ShipmentStatus.PENDING;

    Instant dispatchedDate;

    Instant estimatedDeliveryDate;

    Instant deliveredDate;

    @NotNull(message = ValidationConstants.INVALID_SHIPMENT_CREATED_DATE)
    Instant createdDate;

    public static ShipmentBuilder builder() {

        return new ShipmentBuilder() {

            @Override
            public Shipment build() {

                return super.build().validate();
            }
        };
    }

}
