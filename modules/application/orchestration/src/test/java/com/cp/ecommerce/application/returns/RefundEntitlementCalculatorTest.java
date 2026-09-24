package com.cp.ecommerce.application.returns;

import java.math.BigDecimal;
import java.util.List;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderLineItem;
import com.cp.ecommerce.foundation.exception.ApplicationNotFoundException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class RefundEntitlementCalculatorTest {

    private static final String SKU_ONE = "SKU-1";

    private static final String SKU_TWO = "SKU-2";

    private static final String ONE_HUNDRED = "100.00";

    private static final String ZERO = "0.00";

    private static final String ONE_CENT = "0.01";

    private static final String TWO_CENTS = "0.02";

    private final RefundEntitlementCalculator calculator = new RefundEntitlementCalculator();

    @Test
    void shouldAllocateOrderDiscountProportionallyAcrossLines() {
        final OrderLineItem first = line(SKU_ONE, ONE_HUNDRED);
        final OrderLineItem second = line(SKU_TWO, ONE_HUNDRED);
        final Order order = order("200.00", "180.00", first, second);

        assertThat(calculator.lineEntitlement(order, SKU_ONE)).isEqualByComparingTo("90.00");
        assertThat(calculator.lineEntitlement(order, SKU_TWO)).isEqualByComparingTo("90.00");
    }

    @Test
    void shouldAllocateMinorUnitRemainderDeterministically() {
        final OrderLineItem first = line(SKU_ONE, ONE_CENT);
        final OrderLineItem second = line(SKU_TWO, TWO_CENTS);
        final Order order = order("0.03", TWO_CENTS, first, second);

        assertThat(calculator.lineEntitlement(order, SKU_ONE)).isEqualByComparingTo(ONE_CENT);
        assertThat(calculator.lineEntitlement(order, SKU_TWO)).isEqualByComparingTo(ONE_CENT);
    }

    @Test
    void shouldAllocateRemainderIndependentOfOrderLineIterationOrder() {

        final OrderLineItem first = line(SKU_ONE, ONE_CENT);
        final OrderLineItem second = line(SKU_TWO, ONE_CENT);
        final Order forward = order(TWO_CENTS, ONE_CENT, first, second);
        final Order reversed = order(TWO_CENTS, ONE_CENT, second, first);

        assertThat(calculator.lineEntitlement(forward, SKU_ONE))
                .isEqualByComparingTo(calculator.lineEntitlement(reversed, SKU_ONE));
        assertThat(calculator.lineEntitlement(forward, SKU_TWO))
                .isEqualByComparingTo(calculator.lineEntitlement(reversed, SKU_TWO));
    }

    @Test
    void shouldUseSkuAsStableMinorUnitTieBreak() {

        final OrderLineItem skuTwo = line(SKU_TWO, ONE_CENT);
        final OrderLineItem skuOne = line(SKU_ONE, ONE_CENT);
        final Order historicalIterationOrder = order(TWO_CENTS, ONE_CENT, skuTwo, skuOne);

        assertThat(calculator.lineEntitlement(historicalIterationOrder, SKU_ONE)).isEqualByComparingTo(ONE_CENT);
        assertThat(calculator.lineEntitlement(historicalIterationOrder, SKU_TWO)).isEqualByComparingTo(ZERO);
    }

    @Test
    void shouldReturnZeroForFullyDiscountedExistingLine() {
        final OrderLineItem line = line(SKU_ONE, ONE_HUNDRED);
        final Order order = order(ONE_HUNDRED, ZERO, line);

        assertThat(calculator.lineEntitlement(order, SKU_ONE)).isEqualByComparingTo(ZERO);
    }

    @Test
    void shouldRejectSkuOutsideHistoricalOrderSnapshot() {
        final Order order = order(ONE_HUNDRED, "90.00", line(SKU_ONE, ONE_HUNDRED));

        assertThatThrownBy(() -> calculator.lineEntitlement(order, "OTHER")).isInstanceOf(ApplicationNotFoundException.class);
    }

    private static OrderLineItem line(final String sku, final String subtotal) {
        final OrderLineItem item = mock(OrderLineItem.class);
        given(item.getSku()).willReturn(sku);
        given(item.getSubtotal()).willReturn(new BigDecimal(subtotal));
        return item;
    }

    private static Order order(final String subtotal, final String total, final OrderLineItem... items) {
        final Order order = mock(Order.class);
        given(order.getSubtotal()).willReturn(new BigDecimal(subtotal));
        given(order.getTotal()).willReturn(new BigDecimal(total));
        given(order.getItems()).willReturn(List.of(items));
        return order;
    }

    @Test
    void shouldRejectMissingSkuForFullyDiscountedOrder() {

        final Order order = order(ONE_HUNDRED, ZERO, line(SKU_ONE, ONE_HUNDRED));

        assertThatThrownBy(() -> calculator.lineEntitlement(order, "OTHER")).isInstanceOf(ApplicationNotFoundException.class);
    }

}
