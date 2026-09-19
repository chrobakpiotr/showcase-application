package com.cp.ecommerce.domain.payment.usecase;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Optional;

import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.payment.PaymentRefundClaim;
import com.cp.ecommerce.domain.payment.PaymentRefundOutcome;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.incoming.GetPaymentInPort;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ChargePaymentOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.FindPaymentTransactionOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ManagePaymentRefundOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.RefundPaymentOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.SavePaymentTransactionOutPort;
import com.cp.ecommerce.foundation.annotation.UseCase;
import com.cp.ecommerce.foundation.exception.PaymentDeclinedException;

import lombok.RequiredArgsConstructor;

/**
 * Use case for capturing and refunding an order's payment.
 */
@UseCase
@RequiredArgsConstructor
public class ManagePaymentUseCase implements GetPaymentInPort, ManagePaymentInPort {

    private static final String ORDER_REFUND_PREFIX = "ORDER-REFUND:";

    private final FindPaymentTransactionOutPort findPaymentTransactionOutPort;

    private final SavePaymentTransactionOutPort savePaymentTransactionOutPort;

    private final ChargePaymentOutPort chargePaymentOutPort;

    private final RefundPaymentOutPort refundPaymentOutPort;

    private final ManagePaymentRefundOutPort managePaymentRefundOutPort;

    @Override
    public PaymentTransaction getPayment(final String orderNumber) {

        return Optional.ofNullable(findPaymentTransactionOutPort.find(orderNumber))
                .orElseGet(() -> PaymentTransaction.builder().orderNumber(orderNumber).build());
    }

    @Override
    public PaymentTransaction capturePayment(final String orderNumber, final BigDecimal amount, final PaymentMethod method) {

        final PaymentTransaction current = getPayment(orderNumber);
        if (current.getStatus() == PaymentStatus.CAPTURED || current.getStatus() == PaymentStatus.PARTIALLY_REFUNDED
                || current.getStatus() == PaymentStatus.REFUNDED) {

            return current;
        }
        try {

            final String gatewayReference = chargePaymentOutPort.charge(orderNumber, amount, method);
            return savePaymentTransactionOutPort.save(
                    PaymentTransaction.builder()
                            .orderNumber(orderNumber)
                            .amount(amount)
                            .refundedAmount(BigDecimal.ZERO)
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
                            .refundedAmount(BigDecimal.ZERO)
                            .method(method)
                            .status(PaymentStatus.DECLINED)
                            .created(new Date())
                            .build());
            throw declined;
        }
    }

    @Override
    public PaymentTransaction refundPayment(final String orderNumber) {

        return executeRefund(managePaymentRefundOutPort.reserveRemaining(ORDER_REFUND_PREFIX + orderNumber, orderNumber));
    }

    @Override
    public PaymentTransaction refundPayment(final String orderNumber, final String refundId, final BigDecimal amount) {

        return executeRefund(managePaymentRefundOutPort.reserve(refundId, orderNumber, amount));
    }

    private PaymentTransaction executeRefund(final PaymentRefundClaim claim) {

        if (claim.outcome() == PaymentRefundOutcome.NOTHING_TO_REFUND) {

            return getPayment(claim.orderNumber());
        }
        if (claim.outcome() == PaymentRefundOutcome.COMPLETED) {

            return claim.payment();
        }
        refundPaymentOutPort.refund(claim.orderNumber(), claim.gatewayReference(), claim.refundId(), claim.amount());
        return managePaymentRefundOutPort.complete(claim.refundId());
    }
}
