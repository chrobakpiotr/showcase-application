package com.cp.ecommerce.domain.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.cp.ecommerce.domain.customer.Customer;
import com.cp.ecommerce.foundation.annotation.DomainObject;
import com.cp.ecommerce.foundation.constant.ValidationConstants;
import com.cp.ecommerce.foundation.validation.ValidDomainObject;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Representation of order domain object.
 */
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@DomainObject
public class Order extends ValidDomainObject<Order> {

    @Size(max = ValidationConstants.ORDER_REMARKS_MAX, message = ValidationConstants.INVALID_REMARKS)
    String remarks;

    String orderNumber;

    String stockReservationId;

    Instant created;

    @NotNull(message = ValidationConstants.INVALID_CUSTOMER)
    @Valid
    Customer customer;

    @NotEmpty(message = ValidationConstants.INVALID_ORDER_LINE_ITEMS)
    @Valid
    @Builder.Default
    List<OrderLineItem> items = List.of();

    @Builder.Default
    OrderStatus status = OrderStatus.CONFIRMED;

    @NotNull(message = ValidationConstants.INVALID_PAYMENT_METHOD)
    PaymentMethod paymentMethod;

    @Size(max = ValidationConstants.COUPON_CODE_MAX, message = ValidationConstants.INVALID_ORDER_COUPON_CODE)
    String couponCode;

    @DecimalMin(value = "0.00", message = ValidationConstants.INVALID_ORDER_DISCOUNT_AMOUNT)
    @Builder.Default
    BigDecimal discountAmount = BigDecimal.ZERO;

    public static Order.OrderBuilder builder() {

        return new Order.OrderBuilder() {

            @Override
            public Order build() {

                return super.build().validate();
            }
        };
    }

    public boolean canBeCancelled() {

        return status == OrderStatus.CONFIRMED;
    }

    public BigDecimal getSubtotal() {

        return items.stream().map(OrderLineItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotal() {

        return getSubtotal().subtract(discountAmount == null ? BigDecimal.ZERO : discountAmount).max(BigDecimal.ZERO);
    }

}
