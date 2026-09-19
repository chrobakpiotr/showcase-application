package com.cp.ecommerce.adapter.common.utils;

import java.math.BigDecimal;
import java.time.Instant;

import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnStatus;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builder class for {@link ReturnRequest} test data.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ReturnRequestBuilder {

    public static final String TEST_RETURN_NUMBER = "RETURN-1234";

    public static final String TEST_ORDER_NUMBER = "ORD-1001";

    public static final String TEST_SKU = "SKU-1234";

    public static final int TEST_QUANTITY = 1;

    public static final String TEST_REASON = "Damaged on arrival";

    public static final ReturnStatus TEST_STATUS = ReturnStatus.REQUESTED;

    public static final Instant TEST_REQUESTED_DATE = Instant.ofEpochMilli(1710000000000L);

    public static final Instant TEST_DECIDED_DATE = Instant.ofEpochMilli(1710003600000L);

    public static final BigDecimal TEST_REFUND_AMOUNT = new BigDecimal("29.99");

    public static ReturnRequest mockReturnRequest() {

        return ReturnRequest.builder()
                .returnNumber(TEST_RETURN_NUMBER)
                .orderNumber(TEST_ORDER_NUMBER)
                .sku(TEST_SKU)
                .quantity(TEST_QUANTITY)
                .reason(TEST_REASON)
                .status(TEST_STATUS)
                .requestedDate(TEST_REQUESTED_DATE)
                .refundAmount(TEST_REFUND_AMOUNT)
                .build();
    }

}
