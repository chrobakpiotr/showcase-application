package com.cp.ecommerce.domain.payment.usecase;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.adapter.common.exception.PaymentDeclinedException;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.incoming.GetPaymentInPort;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ChargePaymentOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.FindPaymentTransactionOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.RefundPaymentOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.SavePaymentTransactionOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for capturing and refunding an order's payment (see ADR 0030).
 */
@UseCase
@RequiredArgsConstructor
public class ManagePaymentUseCase implements GetPaymentInPort, ManagePaymentInPort {

    private final FindPaymentTransactionOutPort findPaymentTransactionOutPort;

    private final SavePaymentTransactionOutPort savePaymentTransactionOutPort;

    private final ChargePaymentOutPort chargePaymentOutPort;

    private final RefundPaymentOutPort refundPaymentOutPort;

    @Override
    public PaymentTransaction getPayment(final String orderNumber) {

        return Optional.ofNullable(findPaymentTransactionOutPort.find(orderNumber))
                .orElseGet(() -> PaymentTransaction.builder().orderNumber(orderNumber).build());
    }

    @Override
    public PaymentTransaction capturePayment(final String orderNumber, final BigDecimal amount, final PaymentMethod method) {

        final PaymentTransaction current = getPayment(orderNumber);
        if (current.getStatus() == PaymentStatus.CAPTURED) {

            // Already captured by a previous invocation of this same method (e.g. an earlier poll of the
            // order-placement saga) - return it unchanged instead of charging the customer a second time.
            return current;
        }
        try {

            final String gatewayReference = chargePaymentOutPort.charge(orderNumber, amount, method);
            return savePaymentTransactionOutPort.save(
                    PaymentTransaction.builder()
                            .orderNumber(orderNumber)
                            .amount(amount)
                            .method(method)
                            .status(PaymentStatus.CAPTURED)
                            .gatewayReference(gatewayReference)
                            .created(new Date())
                            .build());
        } catch (final PaymentDeclinedException declined) {

            savePaymentTransactionOutPort.save(
                    PaymentTransaction.builder()
                            .orderNumber(orderNumber)
                            .amount(amount)
                            .method(method)
                            .status(PaymentStatus.DECLINED)
                            .created(new Date())
                            .build());
            throw declined;
        }
    }

    @Override
    public PaymentTransaction refundPayment(final String orderNumber) {

        final PaymentTransaction current = getPayment(orderNumber);
        if (current.getStatus() != PaymentStatus.CAPTURED) {

            // Nothing to refund: never captured, already refunded, or was declined in the first place - safe to call
            // unconditionally (see ManagePaymentInPort#refundPayment).
            return current;
        }
        refundPaymentOutPort.refund(orderNumber, current.getGatewayReference());
        return savePaymentTransactionOutPort.save(
                PaymentTransaction.builder()
                        .orderNumber(current.getOrderNumber())
                        .amount(current.getAmount())
                        .method(current.getMethod())
                        .status(PaymentStatus.REFUNDED)
                        .gatewayReference(current.getGatewayReference())
                        .created(current.getCreated())
                        .build());
    }

}
