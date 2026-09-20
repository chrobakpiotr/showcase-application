package com.cp.ecommerce.application.order;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.cp.ecommerce.domain.catalog.Product;
import com.cp.ecommerce.domain.catalog.port.incoming.ManageProductInPort;
import com.cp.ecommerce.domain.coupon.CouponDiscount;
import com.cp.ecommerce.domain.coupon.port.incoming.ApplyCouponInPort;
import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderLineItem;
import com.cp.ecommerce.domain.order.PlaceOrderResult;
import com.cp.ecommerce.domain.order.usecase.PlaceOrderUseCase;
import com.cp.ecommerce.foundation.exception.ApplicationBadRequestException;
import com.cp.ecommerce.foundation.exception.ApplicationNotFoundException;
import com.cp.ecommerce.foundation.exception.InsufficientStockException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlaceOrderService implements PlaceOrderWorkflow {

    private final PlaceOrderUseCase placeOrderUseCase;

    private final Clock clock;

    private final ManageProductInPort manageProductInPort;

    private final ManageStockInPort manageStockInPort;

    private final ApplyCouponInPort applyCouponInPort;

    private final SendNotificationInPort sendNotificationInPort;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PlaceOrderResult placeOrder(final Order draft, final String idempotencyKey) {

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

    private Order priceOrder(final Order draft) {

        final java.util.Set<String> seen = new java.util.HashSet<>();
        final List<OrderLineItem> pricedItems = draft.getItems().stream().map(item -> {
            if (!seen.add(item.getSku())) {
                throw new ApplicationBadRequestException("Duplicate SKU in order: " + item.getSku());
            }
            final Product product = manageProductInPort.findProduct(item.getSku());
            if (product == null || !product.isActive()) {
                throw new ApplicationNotFoundException("Active product not found: " + item.getSku());
            }
            return OrderLineItem.builder()
                    .sku(product.getSku())
                    .productName(product.getName())
                    .unitPrice(product.getUnitPrice())
                    .quantity(item.getQuantity())
                    .build();
        }).toList();

        return Order.builder()
                .remarks(draft.getRemarks())
                .orderNumber(draft.getOrderNumber())
                .stockReservationId(draft.getStockReservationId())
                .created(draft.getCreated())
                .customer(draft.getCustomer())
                .items(pricedItems)
                .status(draft.getStatus())
                .paymentMethod(draft.getPaymentMethod())
                .couponCode(draft.getCouponCode())
                .discountAmount(draft.getDiscountAmount())
                .build();
    }

    private Order prepareOrder(final Order draft) {

        final Order priced = priceOrder(draft);
        priced.assertValidationsEmpty();
        final Order prepared = withStockReservationIdentity(applyCouponIfPresent(priced));
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
        final CouponDiscount discount = applyCouponInPort
                .applyCoupon(order.getCouponCode(), order.getSubtotal(), Instant.ofEpochMilli(clock.instant().toEpochMilli()));
        if (discount == null) {
            throw new ApplicationNotFoundException("Coupon not found");
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
