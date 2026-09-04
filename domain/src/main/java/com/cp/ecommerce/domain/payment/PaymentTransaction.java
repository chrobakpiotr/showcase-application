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
 * A single payment attempt/outcome for one {@link com.cp.ecommerce.domain.order.Order}, identified by {@link #orderNumber} -
 * the payment bounded context is downstream of order (a payment transaction only ever exists to settle a specific order's
 * total), so it is the one allowed to depend on {@code order.PaymentMethod}; the reverse dependency is what is deliberately
 * avoided (see ADR 0030), matching the established catalog/inventory/cart precedent (ADR 0026) of keeping {@code Order}'s own
 * domain independent of bounded contexts composed at the adapter layer.
 *
 * <p>
 * {@link #method} is intentionally not {@code @NotNull}: {@link PaymentStatus#PENDING} placeholder instances (see
 * {@code GetPaymentInPort}) are returned before any method has actually been chosen/charged, so it can only be required once a
 * capture actually happens (enforced by {@code ManagePaymentUseCase}, not this class).
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

    PaymentMethod method;

    @NotNull(message = ValidationConstants.INVALID_PAYMENT_METHOD)
    @Builder.Default
    PaymentStatus status = PaymentStatus.PENDING;

    @Size(
            max = ValidationConstants.PAYMENT_GATEWAY_REFERENCE_MAX,
            message = ValidationConstants.INVALID_PAYMENT_GATEWAY_REFERENCE)
    String gatewayReference;

    Date created;

    public static PaymentTransaction.PaymentTransactionBuilder builder() {

        return new PaymentTransaction.PaymentTransactionBuilder() {

            @Override
            public PaymentTransaction build() {

                return super.build().validate();
            }
        };
    }

}
