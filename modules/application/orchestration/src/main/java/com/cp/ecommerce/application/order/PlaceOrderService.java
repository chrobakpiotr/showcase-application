package com.cp.ecommerce.application.order;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.cp.ecommerce.adapter.common.exception.InsufficientStockException;
import com.cp.ecommerce.domain.coupon.CouponDiscount;
import com.cp.ecommerce.domain.coupon.port.incoming.ApplyCouponInPort;
import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderLineItem;
import com.cp.ecommerce.domain.order.PlaceOrderResult;
import com.cp.ecommerce.domain.order.usecase.PlaceOrderUseCase;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlaceOrderService implements PlaceOrderWorkflow {

    private final PlaceOrderUseCase placeOrderUseCase;

    private final ManageStockInPort manageStockInPort;

    private final ApplyCouponInPort applyCouponInPort;

    private final SendNotificationInPort sendNotificationInPort;

    @Override
    public PlaceOrderResult placeOrder(final Order draft, final String idempotencyKey) {

        draft.assertValidationsEmpty();
        final PlaceOrderResult result = placeOrderUseCase.placeOrder(draft, idempotencyKey, this::prepareOrder);
        if (result.newlyPlaced()) {
            sendNotificationInPort.sendNotification(
                    draft.getCustomer().getContact().getEmail(),
                    NotificationType.ORDER_CONFIRMED,
                    "Order " + result.orderNumber() + " confirmed",
                    "Your order " + result.orderNumber() + " was confirmed.");
        }
        return result;
    }

    private Order prepareOrder(final Order draft) {

        final Order prepared = withStockReservationIdentity(applyCouponIfPresent(draft));
        prepared.assertValidationsEmpty();
        reserveStockFor(prepared);
        return prepared;
    }

    private Order withStockReservationIdentity(final Order order) {

        if (order.getStockReservationId() != null && !order.getStockReservationId().isBlank()) {
            return order;
        }
        final String reservationId = order.getOrderNumber() == null || order.getOrderNumber().isBlank()
                ? UUID.randomUUID().toString()
                : order.getOrderNumber();
        return copy(order, reservationId, order.getCouponCode(), order.getDiscountAmount());
    }

    private Order applyCouponIfPresent(final Order order) {

        if (order.getCouponCode() == null || order.getCouponCode().isBlank()) {
            return order;
        }
        final CouponDiscount discount = applyCouponInPort.applyCoupon(order.getCouponCode(), order.getSubtotal(), new Date());
        if (discount == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found");
        }
        return copy(order, order.getStockReservationId(), discount.code(), discount.discountAmount());
    }

    private void reserveStockFor(final Order order) {

        final List<OrderLineItem> reserved = new ArrayList<>();
        try {
            for (final OrderLineItem item : order.getItems()) {
                manageStockInPort.reserveStock(order.getStockReservationId(), item.getSku(), item.getQuantity());
                reserved.add(item);
            }
        } catch (final InsufficientStockException exception) {
            reserved.forEach(item -> manageStockInPort.releaseStock(order.getStockReservationId(), item.getSku()));
            throw exception;
        }
    }

    private static Order copy(
            final Order order,
            final String reservationId,
            final String couponCode,
            final java.math.BigDecimal discountAmount) {

        return Order.builder()
                .remarks(order.getRemarks())
                .orderNumber(order.getOrderNumber())
                .stockReservationId(reservationId)
                .created(order.getCreated())
                .customer(order.getCustomer())
                .items(order.getItems())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .couponCode(couponCode)
                .discountAmount(discountAmount)
                .build();
    }
}
