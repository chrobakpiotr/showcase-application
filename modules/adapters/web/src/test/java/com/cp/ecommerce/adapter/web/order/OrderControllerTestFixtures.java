package com.cp.ecommerce.adapter.web.order;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import com.cp.ecommerce.adapter.common.resilience.RateLimitedExecutor;
import com.cp.ecommerce.adapter.common.utils.CustomerBuilder;
import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.adapter.web.order.resource.CustomerResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderDetailsResource;
import com.cp.ecommerce.adapter.web.utils.OrderResourceBuilder;
import com.cp.ecommerce.domain.catalog.Category;
import com.cp.ecommerce.domain.catalog.Product;
import com.cp.ecommerce.domain.catalog.port.incoming.ManageProductInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.PlaceOrderResult;
import com.cp.ecommerce.domain.order.usecase.PlaceOrderUseCase;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import tools.jackson.databind.json.JsonMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

final class OrderControllerTestFixtures {

    private static final String SECOND_LINE_ITEM_SKU = "SKU-2";

    private OrderControllerTestFixtures() {
    }

    static void stubPlacementInfrastructure(
            final PlatformTransactionManager transactionManager,
            final Clock clock,
            final ManageProductInPort manageProductInPort,
            final PlaceOrderUseCase placeOrderUseCase,
            final RateLimitedExecutor rateLimitedExecutor) {

        org.mockito.Mockito.lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        org.mockito.Mockito.lenient().when(clock.instant()).thenReturn(Instant.parse("2026-09-19T10:00:00Z"));
        org.mockito.Mockito.lenient().when(manageProductInPort.findProduct(anyString())).thenAnswer(invocation -> {
            final String sku = invocation.getArgument(0);
            final BigDecimal price = SECOND_LINE_ITEM_SKU.equals(sku)
                    ? BigDecimal.ONE
                    : OrderBuilder.TEST_ORDER_LINE_ITEM_UNIT_PRICE;
            return Product.builder()
                    .sku(sku)
                    .name(OrderBuilder.TEST_ORDER_LINE_ITEM_PRODUCT_NAME)
                    .category(Category.builder().name("Test").slug("test").build())
                    .unitPrice(price)
                    .active(true)
                    .build();
        });
        org.mockito.Mockito.lenient().when(placeOrderUseCase.placeOrder(any(), any(), any())).thenAnswer(invocation -> {
            final UnaryOperator<Order> prepare = invocation.getArgument(2);
            prepare.apply(invocation.getArgument(0));
            return new PlaceOrderResult(OrderBuilder.TEST_ORDER_NUMBER, true);
        });
        org.mockito.BDDMockito.given(rateLimitedExecutor.callRateLimited(anyString(), any())).willAnswer(invocation -> {
            final Supplier<?> action = invocation.getArgument(1);
            return action.get();
        });
    }

    static Order cancelledOrder() {

        final Order confirmed = OrderBuilder.mockOrder();
        return Order.builder()
                .remarks(confirmed.getRemarks())
                .orderNumber(confirmed.getOrderNumber())
                .created(confirmed.getCreated())
                .customer(confirmed.getCustomer())
                .items(confirmed.getItems())
                .status(OrderStatus.CANCELLED)
                .build();
    }

    static OrderDetailsResource confirmedOrderDetailsResource() {

        return orderDetailsResource(OrderStatus.CONFIRMED);
    }

    static OrderDetailsResource cancelledOrderDetailsResource() {

        return orderDetailsResource(OrderStatus.CANCELLED);
    }

    static com.cp.ecommerce.foundation.exception.RateLimitExceededException rateLimitExceeded() {

        return new com.cp.ecommerce.foundation.exception.RateLimitExceededException(
                "Rate limit exceeded for 'placeOrder'",
                java.time.Duration.ofSeconds(1),
                null);
    }

    static String orderJson() throws Exception {

        return JsonMapper.builder().build().writeValueAsString(OrderResourceBuilder.mockOrderResource());
    }

    private static OrderDetailsResource orderDetailsResource(final OrderStatus status) {

        final CustomerResource customer = CustomerResource.builder()
                .fullName(CustomerBuilder.TEST_FULL_NAME)
                .email(CustomerBuilder.TEST_EMAIL)
                .phone(CustomerBuilder.TEST_PHONE_NUMBER)
                .street(CustomerBuilder.TEST_STREET_ADDRESS)
                .postalCode(CustomerBuilder.TEST_POSTAL_CODE)
                .city(CustomerBuilder.TEST_CITY)
                .countryCode(CustomerBuilder.TEST_COUNTRY_CODE)
                .build();
        return OrderDetailsResource.builder()
                .orderNumber(OrderBuilder.TEST_ORDER_NUMBER)
                .status(status)
                .remarks(OrderBuilder.TEST_REMARKS)
                .customer(customer)
                .build();
    }
}
