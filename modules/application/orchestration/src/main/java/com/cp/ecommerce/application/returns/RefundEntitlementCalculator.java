package com.cp.ecommerce.application.returns;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderLineItem;
import com.cp.ecommerce.foundation.exception.ApplicationNotFoundException;

import org.springframework.stereotype.Component;

/**
 * Allocates the captured/payable order total to immutable order-line snapshots in integer minor units.
 */
@Component
public class RefundEntitlementCalculator {

    private static final int MONEY_SCALE = 2;

    BigDecimal lineEntitlement(final Order order, final String sku) {

        final long payableMinor = minorUnits(order.getTotal());
        final long subtotalMinor = minorUnits(order.getSubtotal());
        if (subtotalMinor <= 0L || payableMinor <= 0L) {
            ensureLineExists(order, sku);
            return BigDecimal.ZERO.setScale(MONEY_SCALE);
        }

        final List<OrderLineItem> stableItems = order.getItems()
                .stream()
                .sorted(Comparator.comparing(OrderLineItem::getSku))
                .toList();
        final Map<String, Long> allocated = new LinkedHashMap<>();
        long allocatedMinor = 0L;
        for (final OrderLineItem item : stableItems) {
            final long lineGrossMinor = minorUnits(item.getSubtotal());
            final long floor = BigDecimal.valueOf(payableMinor)
                    .multiply(BigDecimal.valueOf(lineGrossMinor))
                    .divide(BigDecimal.valueOf(subtotalMinor), 0, RoundingMode.DOWN)
                    .longValueExact();
            allocated.put(item.getSku(), floor);
            allocatedMinor += floor;
        }

        long remainder = payableMinor - allocatedMinor;
        for (final OrderLineItem item : stableItems) {
            if (remainder == 0L) {
                break;
            }
            allocated.computeIfPresent(item.getSku(), (ignored, value) -> value + 1L);
            remainder--;
        }

        final Long entitlement = allocated.get(sku);
        if (entitlement == null) {
            throw new ApplicationNotFoundException("Order line item not found");
        }
        return BigDecimal.valueOf(entitlement, MONEY_SCALE);
    }

    private static void ensureLineExists(final Order order, final String sku) {

        final boolean exists = order.getItems().stream().anyMatch(item -> item.getSku().equals(sku));
        if (!exists) {
            throw new ApplicationNotFoundException("Order line item not found");
        }
    }

    private static long minorUnits(final BigDecimal amount) {

        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_EVEN).movePointRight(MONEY_SCALE).longValueExact();
    }
}
