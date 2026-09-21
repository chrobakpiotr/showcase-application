package com.cp.ecommerce.application.order;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import com.cp.ecommerce.domain.catalog.Category;
import com.cp.ecommerce.domain.catalog.Product;
import com.cp.ecommerce.domain.catalog.port.incoming.ManageProductInPort;
import com.cp.ecommerce.domain.coupon.CouponDiscount;
import com.cp.ecommerce.domain.coupon.port.incoming.ApplyCouponInPort;
import com.cp.ecommerce.domain.customer.Contact;
import com.cp.ecommerce.domain.customer.Customer;
import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderLineItem;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.order.PlaceOrderResult;
import com.cp.ecommerce.domain.order.usecase.PlaceOrderUseCase;
import com.cp.ecommerce.foundation.exception.ApplicationBadRequestException;
import com.cp.ecommerce.foundation.exception.ApplicationNotFoundException;
import com.cp.ecommerce.foundation.exception.InsufficientStockException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("PMD.TooManyMethods")
class PlaceOrderServiceTest {

    private static final String ORDER_NUMBER = "ORDER-1";
    private static final String EMAIL = "customer@example.com";
    private static final String FIRST_SKU = "SKU-1";
    private static final String SECOND_SKU = "SKU-2";
    private static final String COUPON_CODE = "SAVE10";

    private static final String FIRST_PRODUCT_NAME = "Keyboard";

    private static final String FIRST_PRODUCT_PRICE = "12.50";

    private static final String ROLLBACK_RESERVATION_ID = "RESERVATION-2";

    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");

    @Mock
    private transient PlaceOrderUseCase placeOrderUseCase;

    @Mock
    private transient ManageProductInPort manageProductInPort;

    @Mock
    private transient ManageStockInPort manageStockInPort;

    @Mock
    private transient ApplyCouponInPort applyCouponInPort;

    @Mock
    private transient SendNotificationInPort sendNotificationInPort;

    private transient PlaceOrderService service;

    @BeforeEach
    void setUp() {

        service = new PlaceOrderService(
                placeOrderUseCase,
                Clock.fixed(NOW, ZoneOffset.UTC),
                manageProductInPort,
                manageStockInPort,
                applyCouponInPort,
                sendNotificationInPort);
    }

    @Test
    void shouldResolvePricingApplyCouponReserveStockAndNotify() {

        final Order draft = draft(
                ORDER_NUMBER,
                null,
                COUPON_CODE,
                List.of(clientItem(FIRST_SKU, 2), clientItem(SECOND_SKU, 1)));
        given(manageProductInPort.findProduct(FIRST_SKU))
                .willReturn(product(FIRST_SKU, FIRST_PRODUCT_NAME, FIRST_PRODUCT_PRICE, true));
        given(manageProductInPort.findProduct(SECOND_SKU)).willReturn(product(SECOND_SKU, "Mouse", "5.00", true));
        given(applyCouponInPort.applyCoupon(eq(COUPON_CODE), eq(new BigDecimal("30.00")), any()))
                .willReturn(new CouponDiscount(COUPON_CODE, new BigDecimal("3.00")));

        final AtomicReference<Order> prepared = new AtomicReference<>();
        given(placeOrderUseCase.placeOrder(any(), eq("key-1"), any())).willAnswer(invocation -> {
            final Order priced = invocation.getArgument(0);
            final UnaryOperator<Order> prepare = invocation.getArgument(2);
            prepared.set(prepare.apply(priced));
            return new PlaceOrderResult(ORDER_NUMBER, true);
        });

        final PlaceOrderResult result = service.placeOrder(draft, "key-1");

        assertThat(result.orderNumber()).isEqualTo(ORDER_NUMBER);
        assertThat(prepared.get().getStockReservationId()).isEqualTo(ORDER_NUMBER);
        assertThat(prepared.get().getItems()).extracting(OrderLineItem::getProductName)
                .containsExactly(FIRST_PRODUCT_NAME, "Mouse");
        assertThat(prepared.get().getDiscountAmount()).isEqualByComparingTo("3.00");
        verify(applyCouponInPort).applyCoupon(eq(COUPON_CODE), eq(new BigDecimal("30.00")), eq(NOW));
        verify(manageStockInPort).reserveStock(ORDER_NUMBER, FIRST_SKU, 2);
        verify(manageStockInPort).reserveStock(ORDER_NUMBER, SECOND_SKU, 1);
        verify(sendNotificationInPort).sendNotification(
                anyString(),
                eq(EMAIL),
                eq(NotificationType.ORDER_CONFIRMED),
                eq("Order ORDER-1 confirmed"),
                eq("Your order ORDER-1 was confirmed."));
    }

    @Test
    void shouldGenerateReservationIdentityForDraftWithoutOrderNumberAndSkipReplayNotification() {

        final Order draft = draft(null, null, null, List.of(clientItem(FIRST_SKU, 1)));
        given(manageProductInPort.findProduct(FIRST_SKU))
                .willReturn(product(FIRST_SKU, FIRST_PRODUCT_NAME, FIRST_PRODUCT_PRICE, true));

        final AtomicReference<Order> prepared = new AtomicReference<>();
        given(placeOrderUseCase.placeOrder(any(), eq("key-2"), any())).willAnswer(invocation -> {
            final UnaryOperator<Order> prepare = invocation.getArgument(2);
            prepared.set(prepare.apply(invocation.getArgument(0)));
            return new PlaceOrderResult("EXISTING-1", false);
        });

        service.placeOrder(draft, "key-2");

        assertThat(prepared.get().getStockReservationId()).isNotBlank();
        verify(applyCouponInPort, never()).applyCoupon(any(), any(), any());
        verify(sendNotificationInPort, never()).sendNotification(anyString(), any(), any(), any(), any());
    }

    @Test
    void shouldReplayCompletedOrderWithoutCatalogCouponOrInventoryAccess() {

        final Order draft = draft(ORDER_NUMBER, null, COUPON_CODE, List.of(clientItem(FIRST_SKU, 1)));
        given(placeOrderUseCase.placeOrder(any(), eq("replay-key"), any()))
                .willReturn(new PlaceOrderResult("EXISTING-ORDER", false));

        final PlaceOrderResult result = service.placeOrder(draft, "replay-key");

        assertThat(result.orderNumber()).isEqualTo("EXISTING-ORDER");
        assertThat(result.newlyPlaced()).isFalse();
        verify(manageProductInPort, never()).findProduct(any());
        verify(applyCouponInPort, never()).applyCoupon(any(), any(), any());
        verify(manageStockInPort, never()).reserveStock(any(), any(), org.mockito.ArgumentMatchers.anyInt());
        verify(sendNotificationInPort, never()).sendNotification(anyString(), any(), any(), any(), any());
    }

    @Test
    void shouldReuseExistingReservationIdentityAndTreatBlankCouponAsAbsent() {

        final Order draft = draft(ORDER_NUMBER, "RESERVATION-1", " ", List.of(clientItem(FIRST_SKU, 1)));
        given(manageProductInPort.findProduct(FIRST_SKU))
                .willReturn(product(FIRST_SKU, FIRST_PRODUCT_NAME, FIRST_PRODUCT_PRICE, true));
        given(placeOrderUseCase.placeOrder(any(), any(), any())).willAnswer(invocation -> {
            final UnaryOperator<Order> prepare = invocation.getArgument(2);
            final Order prepared = prepare.apply(invocation.getArgument(0));
            assertThat(prepared.getStockReservationId()).isEqualTo("RESERVATION-1");
            return new PlaceOrderResult(ORDER_NUMBER, false);
        });

        service.placeOrder(draft, null);

        verify(manageStockInPort).reserveStock("RESERVATION-1", FIRST_SKU, 1);
        verify(applyCouponInPort, never()).applyCoupon(any(), any(), any());
    }

    @Test
    void shouldRejectDuplicateSkuBeforeCallingUseCase() {

        final Order draft = draft(ORDER_NUMBER, null, null, List.of(clientItem(FIRST_SKU, 1), clientItem(FIRST_SKU, 2)));
        given(manageProductInPort.findProduct(FIRST_SKU))
                .willReturn(product(FIRST_SKU, FIRST_PRODUCT_NAME, FIRST_PRODUCT_PRICE, true));

        given(placeOrderUseCase.placeOrder(any(), any(), any())).willAnswer(invocation -> {
            final UnaryOperator<Order> prepare = invocation.getArgument(2);
            prepare.apply(invocation.getArgument(0));
            return new PlaceOrderResult(ORDER_NUMBER, true);
        });

        assertThatThrownBy(() -> service.placeOrder(draft, null)).isInstanceOf(ApplicationBadRequestException.class);

        verify(placeOrderUseCase).placeOrder(any(), any(), any());
    }

    @Test
    void shouldRejectUnknownProduct() {

        final Order draft = draft(ORDER_NUMBER, null, null, List.of(clientItem(FIRST_SKU, 1)));
        given(manageProductInPort.findProduct(FIRST_SKU)).willReturn(null);

        given(placeOrderUseCase.placeOrder(any(), any(), any())).willAnswer(invocation -> {
            final UnaryOperator<Order> prepare = invocation.getArgument(2);
            prepare.apply(invocation.getArgument(0));
            return new PlaceOrderResult(ORDER_NUMBER, true);
        });

        assertThatThrownBy(() -> service.placeOrder(draft, null)).isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    void shouldRejectInactiveProduct() {

        final Order draft = draft(ORDER_NUMBER, null, null, List.of(clientItem(FIRST_SKU, 1)));
        given(manageProductInPort.findProduct(FIRST_SKU))
                .willReturn(product(FIRST_SKU, FIRST_PRODUCT_NAME, FIRST_PRODUCT_PRICE, false));

        given(placeOrderUseCase.placeOrder(any(), any(), any())).willAnswer(invocation -> {
            final UnaryOperator<Order> prepare = invocation.getArgument(2);
            prepare.apply(invocation.getArgument(0));
            return new PlaceOrderResult(ORDER_NUMBER, true);
        });

        assertThatThrownBy(() -> service.placeOrder(draft, null)).isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    void shouldReturnNotFoundWhenCouponCannotBeResolved() {

        final Order draft = draft(ORDER_NUMBER, null, "MISSING", List.of(clientItem(FIRST_SKU, 1)));
        given(manageProductInPort.findProduct(FIRST_SKU))
                .willReturn(product(FIRST_SKU, FIRST_PRODUCT_NAME, FIRST_PRODUCT_PRICE, true));
        given(applyCouponInPort.applyCoupon(eq("MISSING"), eq(new BigDecimal(FIRST_PRODUCT_PRICE)), any())).willReturn(null);
        given(placeOrderUseCase.placeOrder(any(), any(), any())).willAnswer(invocation -> {
            final UnaryOperator<Order> prepare = invocation.getArgument(2);
            return new PlaceOrderResult(prepare.apply(invocation.getArgument(0)).getOrderNumber(), true);
        });

        assertThatThrownBy(() -> service.placeOrder(draft, null)).isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    void shouldReleaseAlreadyReservedItemsWhenLaterReservationFails() {

        final Order draft = draft(
                ORDER_NUMBER,
                ROLLBACK_RESERVATION_ID,
                null,
                List.of(clientItem(FIRST_SKU, 1), clientItem(SECOND_SKU, 1)));
        given(manageProductInPort.findProduct(FIRST_SKU))
                .willReturn(product(FIRST_SKU, FIRST_PRODUCT_NAME, FIRST_PRODUCT_PRICE, true));
        given(manageProductInPort.findProduct(SECOND_SKU)).willReturn(product(SECOND_SKU, "Mouse", "5.00", true));
        given(placeOrderUseCase.placeOrder(any(), any(), any())).willAnswer(invocation -> {
            final UnaryOperator<Order> prepare = invocation.getArgument(2);
            prepare.apply(invocation.getArgument(0));
            return new PlaceOrderResult(ORDER_NUMBER, true);
        });
        org.mockito.Mockito.doThrow(new InsufficientStockException("insufficient"))
                .when(manageStockInPort)
                .reserveStock(ROLLBACK_RESERVATION_ID, SECOND_SKU, 1);

        assertThatThrownBy(() -> service.placeOrder(draft, null)).isInstanceOf(InsufficientStockException.class);

        verify(manageStockInPort).reserveStock(ROLLBACK_RESERVATION_ID, FIRST_SKU, 1);
        verify(manageStockInPort).releaseStock(ROLLBACK_RESERVATION_ID, FIRST_SKU);
    }

    private static Order draft(
            final String orderNumber,
            final String reservationId,
            final String couponCode,
            final List<OrderLineItem> items) {

        return Order.builder()
                .remarks("test")
                .orderNumber(orderNumber)
                .stockReservationId(reservationId)
                .customer(Customer.builder().contact(Contact.builder().email(EMAIL).build()).build())
                .items(items)
                .paymentMethod(PaymentMethod.CARD)
                .couponCode(couponCode)
                .build();
    }

    private static OrderLineItem clientItem(final String sku, final int quantity) {

        return OrderLineItem.builder()
                .sku(sku)
                .productName("Client supplied")
                .unitPrice(BigDecimal.ONE)
                .quantity(quantity)
                .build();
    }

    private static Product product(final String sku, final String name, final String unitPrice, final boolean active) {

        return Product.builder()
                .sku(sku)
                .name(name)
                .category(Category.builder().name("Test").slug("test").build())
                .unitPrice(new BigDecimal(unitPrice))
                .active(active)
                .build();
    }
}
