package com.cp.ecommerce.domain.payment;

import java.math.BigDecimal;
import java.util.Date;

import com.cp.ecommerce.adapter.common.annotation.DomainObject;
import com.cp.ecommerce.adapter.common.constant.ValidationConstants;
import com.cp.ecommerce.adapter.common.validation.ValidDomainObject;
import com.cp.ecommerce.domain.order.PaymentMethod;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Payment state for one order.
 */
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@DomainObject
public class PaymentTransaction extends ValidDomainObject<PaymentTransaction> {

    @NotBlank(message = ValidationConstants.INVALID_PAYMENT_ORDER_NUMBER)
    String orderNumber;

    @NotNull(message = ValidationConstants.INVALID_PAYMENT_AMOUNT)
    @DecimalMin(value = "0.0", message = ValidationConstants.INVALID_PAYMENT_AMOUNT)
    @Builder.Default
    BigDecimal amount = BigDecimal.ZERO;

    @NotNull(message = ValidationConstants.INVALID_PAYMENT_AMOUNT)
    @DecimalMin(value = "0.0", message = ValidationConstants.INVALID_PAYMENT_AMOUNT)
    @Builder.Default
    BigDecimal refundedAmount = BigDecimal.ZERO;

    PaymentMethod method;

    @NotNull(message = ValidationConstants.INVALID_PAYMENT_METHOD)
    @Builder.Default
    PaymentStatus status = PaymentStatus.PENDING;

    @Size(
            max = ValidationConstants.PAYMENT_GATEWAY_REFERENCE_MAX,
            message = ValidationConstants.INVALID_PAYMENT_GATEWAY_REFERENCE)
    String gatewayReference;

    Date created;

    public BigDecimal getRemainingRefundableAmount() {

        return amount.subtract(refundedAmount).max(BigDecimal.ZERO);
    }

    public static PaymentTransaction.PaymentTransactionBuilder builder() {

        return new PaymentTransaction.PaymentTransactionBuilder() {

            @Override
            public PaymentTransaction build() {

                return super.build().validate();
            }
        };
    }
}
