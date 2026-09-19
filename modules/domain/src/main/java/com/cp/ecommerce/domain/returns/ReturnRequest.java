package com.cp.ecommerce.domain.returns;

import java.math.BigDecimal;
import java.util.Date;

import com.cp.ecommerce.foundation.annotation.DomainObject;
import com.cp.ecommerce.foundation.constant.ValidationConstants;
import com.cp.ecommerce.foundation.validation.ValidDomainObject;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A single return / RMA request for one ordered SKU.
 *
 * <p>
 * Like {@code review.Review.sku} and {@code payment.PaymentTransaction.orderNumber}, this aggregate stores only bare references
 * ({@link #orderNumber} and {@link #sku}) rather than importing another bounded context's aggregate. The authoritative order is
 * resolved by the web/composition layer before creation, while this bounded context persists its own refund snapshot
 * ({@link #refundAmount}) for later auditing.
 */
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@DomainObject
public class ReturnRequest extends ValidDomainObject<ReturnRequest> {

    @Size(max = ValidationConstants.RETURN_NUMBER_MAX, message = ValidationConstants.INVALID_RETURN_NUMBER)
    String returnNumber;

    @NotBlank(message = ValidationConstants.INVALID_RETURN_ORDER_NUMBER)
    @Size(max = 40, message = ValidationConstants.INVALID_RETURN_ORDER_NUMBER)
    String orderNumber;

    @NotBlank(message = ValidationConstants.INVALID_RETURN_SKU)
    @Size(max = ValidationConstants.RETURN_SKU_MAX, message = ValidationConstants.INVALID_RETURN_SKU)
    String sku;

    @Min(value = 1, message = ValidationConstants.INVALID_RETURN_QUANTITY)
    int quantity;

    @NotBlank(message = ValidationConstants.INVALID_RETURN_REASON)
    @Size(max = ValidationConstants.RETURN_REASON_MAX, message = ValidationConstants.INVALID_RETURN_REASON)
    String reason;

    @NotNull(message = ValidationConstants.INVALID_RETURN_STATUS)
    @Builder.Default
    ReturnStatus status = ReturnStatus.REQUESTED;

    @NotNull(message = ValidationConstants.INVALID_RETURN_REQUESTED_DATE)
    Date requestedDate;

    Date decidedDate;

    @NotNull(message = ValidationConstants.INVALID_RETURN_REFUND_AMOUNT)
    @DecimalMin(value = "0.00", message = ValidationConstants.INVALID_RETURN_REFUND_AMOUNT)
    @Builder.Default
    BigDecimal refundAmount = BigDecimal.ZERO;

    public static ReturnRequest.ReturnRequestBuilder builder() {

        return new ReturnRequest.ReturnRequestBuilder() {

            @Override
            public ReturnRequest build() {

                return super.build().validate();
            }
        };
    }

}
