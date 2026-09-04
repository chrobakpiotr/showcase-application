package com.cp.ecommerce.adapter.persistence.utils;

import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntity;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static com.cp.ecommerce.adapter.common.utils.PaymentTransactionBuilder.TEST_AMOUNT;
import static com.cp.ecommerce.adapter.common.utils.PaymentTransactionBuilder.TEST_GATEWAY_REFERENCE;
import static com.cp.ecommerce.adapter.common.utils.PaymentTransactionBuilder.TEST_ORDER_NUMBER;
import static com.cp.ecommerce.adapter.common.utils.PaymentTransactionBuilder.TEST_PAYMENT_METHOD;
import static com.cp.ecommerce.adapter.common.utils.PaymentTransactionBuilder.TEST_PAYMENT_STATUS;

/**
 * Builder class for {@link PaymentTransactionEntity}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentTransactionEntityBuilder {

    public static PaymentTransactionEntity mockPaymentTransactionEntity() {

        return PaymentTransactionEntity.builder()
                .orderNumber(TEST_ORDER_NUMBER)
                .amount(TEST_AMOUNT)
                .method(TEST_PAYMENT_METHOD)
                .status(TEST_PAYMENT_STATUS)
                .gatewayReference(TEST_GATEWAY_REFERENCE)
                .build();
    }

}
