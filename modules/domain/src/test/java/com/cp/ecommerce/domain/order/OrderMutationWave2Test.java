package com.cp.ecommerce.domain.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderMutationWave2Test {

    @Test
    void shouldExposeSubtotalDiscountedTotalNullDiscountAndZeroClamp() {
        final OrderLineItem item = OrderLineItem.builder()
                .sku("SKU-1")
                .productName("Product")
                .unitPrice(new BigDecimal("12.50"))
                .quantity(2)
                .build();

        final Order discounted = order(List.of(item), new BigDecimal("5.00"));
        final Order nullDiscount = order(List.of(item), null);
        final Order clamped = order(List.of(item), new BigDecimal("30.00"));

        assertThat(discounted.getSubtotal()).isEqualByComparingTo("25.00");
        assertThat(discounted.getTotal()).isEqualByComparingTo("20.00");
        assertThat(nullDiscount.getTotal()).isEqualByComparingTo("25.00");
        assertThat(clamped.getTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldAcceptZeroPageAndRejectNegativePage() {
        assertThat(new PageQuery(0, 1).page()).isZero();
        assertThatThrownBy(() -> new PageQuery(-1, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Order order(final List<OrderLineItem> items, final BigDecimal discount) {
        return Order.builder()
                .orderNumber("ORDER-1")
                .created(Instant.parse("2026-09-20T10:00:00Z"))
                .customer(TestDomainObjectFactory.validCustomer())
                .items(items)
                .paymentMethod(PaymentMethod.CARD)
                .discountAmount(discount)
                .build();
    }
}
