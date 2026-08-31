package com.cp.ecommerce.domain.order;

import java.util.Date;

import com.cp.ecommerce.adapter.common.annotation.DomainObject;
import com.cp.ecommerce.adapter.common.constant.ValidationConstants;
import com.cp.ecommerce.adapter.common.validation.ValidDomainObject;
import com.cp.ecommerce.domain.customer.Customer;

import jakarta.validation.Valid;
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

}
