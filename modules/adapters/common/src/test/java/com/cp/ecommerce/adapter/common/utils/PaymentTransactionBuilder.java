package com.cp.ecommerce.adapter.common.utils;

import java.math.BigDecimal;

import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builder class for {@link PaymentTransaction} test data.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentTransactionBuilder {

    public static final String TEST_ORDER_NUMBER = "1234";

    public static final BigDecimal TEST_AMOUNT = new BigDecimal("59.98");

    public static final PaymentMethod TEST_PAYMENT_METHOD = PaymentMethod.CARD;

    public static final PaymentStatus TEST_PAYMENT_STATUS = PaymentStatus.CAPTURED;

    public static final String TEST_GATEWAY_REFERENCE = "mock-gw-1234";

    public static PaymentTransaction mockPaymentTransaction() {

        return PaymentTransaction.builder()
                .orderNumber(TEST_ORDER_NUMBER)
                .amount(TEST_AMOUNT)
                .method(TEST_PAYMENT_METHOD)
                .status(TEST_PAYMENT_STATUS)
                .gatewayReference(TEST_GATEWAY_REFERENCE)
                .build();
    }

}
