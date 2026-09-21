package com.cp.ecommerce.adapter.web.order;

import java.util.Map;

import com.cp.ecommerce.adapter.web.common.PagedModelAssembler;
import com.cp.ecommerce.adapter.web.order.mapper.OrderWebMapper;
import com.cp.ecommerce.adapter.web.order.resource.OrderDetailsResource;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.PagedResult;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.incoming.GetPaymentInPort;
import com.cp.ecommerce.foundation.exception.TechnicalProblemException;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
@RequiredArgsConstructor
class OrderRepresentationAssembler {

    private final OrderWebMapper orderWebMapper;
    private final GetPaymentInPort getPaymentInPort;

    PagedModel<EntityModel<OrderDetailsResource>> toPagedModel(final PagedResult<Order> result, final int size) {

        final Map<String, PaymentTransaction> payments = getPaymentInPort
                .getPayments(result.content().stream().map(Order::getOrderNumber).toList());
        final var content = result.content()
                .stream()
                .map(order -> toModel(order, order.getOrderNumber(), payments.get(order.getOrderNumber())))
                .toList();

        return PagedModelAssembler.assemble(
                content,
                PagedModelAssembler.page(result.page(), result.size(), result.totalElements(), result.totalPages()),
                target -> linkTo(methodOn(OrderController.class).listOrders(target, size)).withSelfRel());
    }

    EntityModel<OrderDetailsResource> toModel(final Order order, final String orderNumber) {

        return toModel(order, orderNumber, getPaymentInPort.getPayment(orderNumber));
    }

    private EntityModel<OrderDetailsResource> toModel(
            final Order order,
            final String orderNumber,
            final PaymentTransaction payment) {

        final OrderDetailsResource resource = orderWebMapper.mapToResource(order, payment)
                .orElseThrow(() -> new TechnicalProblemException("Order data is missing"));
        final EntityModel<OrderDetailsResource> model = EntityModel
                .of(resource, linkTo(methodOn(OrderController.class).findOrder(orderNumber)).withSelfRel());
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            model.add(linkTo(methodOn(OrderController.class).cancelOrder(orderNumber)).withRel("cancel"));
        }
        return model;
    }
}
