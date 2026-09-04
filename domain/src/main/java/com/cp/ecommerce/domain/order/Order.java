package com.cp.ecommerce.domain.order;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.cp.ecommerce.adapter.common.annotation.DomainObject;
import com.cp.ecommerce.adapter.common.constant.ValidationConstants;
import com.cp.ecommerce.adapter.common.validation.ValidDomainObject;
import com.cp.ecommerce.domain.customer.Customer;

import jakarta.validation.Valid;
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

    Date created;

    // @NotNull + @Valid: a request placing an order without customer/address data must fail validation cleanly
    // (DomainObjectValidationException) rather than let PlaceOrderUseCase NPE on order.getCustomer().getContact()
    // further downstream, and the cascade enforces the nested Contact/Address constraints too.
    @NotNull(message = ValidationConstants.INVALID_CUSTOMER)
    @Valid
    Customer customer;

    // @NotEmpty + @Valid: an order with no line items isn't a real order (see ADR 0029) - the price/name snapshot
    // taken per item mirrors cart.CartLineItem (ADR 0027), just carried through to the placed order.
    @NotEmpty(message = ValidationConstants.INVALID_ORDER_LINE_ITEMS)
    @Valid
    @Builder.Default
    List<OrderLineItem> items = List.of();

    @Builder.Default
    OrderStatus status = OrderStatus.CONFIRMED;

    public static Order.OrderBuilder builder() {

        return new Order.OrderBuilder() {

            @Override
            public Order build() {

                return super.build().validate();
            }
        };
    }

    /**
     * Whether this order is still eligible for a customer-initiated cancellation request (see
     * {@code RequestOrderCancellationUseCase}). Only {@link OrderStatus#CONFIRMED} orders qualify - once cancelled, an order
     * stays cancelled; this keeps that one-way transition rule on the aggregate itself rather than duplicated/ re-derived by
     * every caller that needs to check it.
     */
    public boolean canBeCancelled() {

        return status == OrderStatus.CONFIRMED;
    }

    /**
     * Sum of every line item's {@link OrderLineItem#getSubtotal()}.
     */
    public BigDecimal getTotal() {

        return items.stream().map(OrderLineItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

}
