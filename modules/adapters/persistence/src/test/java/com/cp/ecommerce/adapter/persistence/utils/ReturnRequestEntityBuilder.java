package com.cp.ecommerce.adapter.persistence.utils;

import com.cp.ecommerce.adapter.persistence.returns.entity.ReturnRequestEntity;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static com.cp.ecommerce.adapter.common.utils.ReturnRequestBuilder.TEST_DECIDED_DATE;
import static com.cp.ecommerce.adapter.common.utils.ReturnRequestBuilder.TEST_ORDER_NUMBER;
import static com.cp.ecommerce.adapter.common.utils.ReturnRequestBuilder.TEST_QUANTITY;
import static com.cp.ecommerce.adapter.common.utils.ReturnRequestBuilder.TEST_REASON;
import static com.cp.ecommerce.adapter.common.utils.ReturnRequestBuilder.TEST_REFUND_AMOUNT;
import static com.cp.ecommerce.adapter.common.utils.ReturnRequestBuilder.TEST_REQUESTED_DATE;
import static com.cp.ecommerce.adapter.common.utils.ReturnRequestBuilder.TEST_RETURN_NUMBER;
import static com.cp.ecommerce.adapter.common.utils.ReturnRequestBuilder.TEST_SKU;
import static com.cp.ecommerce.adapter.common.utils.ReturnRequestBuilder.TEST_STATUS;

/**
 * Builder class for {@link ReturnRequestEntity}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ReturnRequestEntityBuilder {

    public static ReturnRequestEntity mockReturnRequestEntity() {

        return ReturnRequestEntity.builder()
                .returnNumber(TEST_RETURN_NUMBER)
                .orderNumber(TEST_ORDER_NUMBER)
                .sku(TEST_SKU)
                .quantity(TEST_QUANTITY)
                .reason(TEST_REASON)
                .status(TEST_STATUS)
                .requestedDate(TEST_REQUESTED_DATE)
                .decidedDate(TEST_DECIDED_DATE)
                .refundAmount(TEST_REFUND_AMOUNT)
                .build();
    }

}
