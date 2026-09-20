package com.cp.ecommerce.domain.payment.usecase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.payment.PaymentProviderOperationType;
import com.cp.ecommerce.domain.payment.PaymentRefundClaim;
import com.cp.ecommerce.domain.payment.PaymentRefundOutcome;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.incoming.GetPaymentInPort;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ChargePaymentOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.FindPaymentTransactionOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ManagePaymentReconciliationOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ManagePaymentRefundOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.PreparePaymentProviderOperationOutPort;
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

    private static final String ORDER_CAPTURE_PREFIX = "ORDER-CAPTURE:";

    private static final String ORDER_REFUND_PREFIX = "ORDER-REFUND:";

    private final FindPaymentTransactionOutPort findPaymentTransactionOutPort;

    private final SavePaymentTransactionOutPort savePaymentTransactionOutPort;

    private final ChargePaymentOutPort chargePaymentOutPort;

    private final RefundPaymentOutPort refundPaymentOutPort;

    private final ManagePaymentRefundOutPort managePaymentRefundOutPort;

    private final ManagePaymentReconciliationOutPort managePaymentReconciliationOutPort;

    private final PreparePaymentProviderOperationOutPort preparePaymentProviderOperationOutPort;

    @Override
    public PaymentTransaction getPayment(final String orderNumber) {

        return Optional.ofNullable(findPaymentTransactionOutPort.find(orderNumber))
                .orElseGet(() -> pendingPayment(orderNumber));
    }

    @Override
    public Map<String, PaymentTransaction> getPayments(final Collection<String> orderNumbers) {

        final Map<String, PaymentTransaction> persisted = findPaymentTransactionOutPort.findAll(orderNumbers);
        final Map<String, PaymentTransaction> result = new LinkedHashMap<>();
        for (final String orderNumber : orderNumbers) {
            result.put(
                    orderNumber,
                    Optional.ofNullable(persisted.get(orderNumber)).orElseGet(() -> pendingPayment(orderNumber)));
        }
        return result;
    }

    @Override
    public PaymentTransaction capturePayment(final String orderNumber, final BigDecimal amount, final PaymentMethod method) {

        final String operationId = ORDER_CAPTURE_PREFIX + orderNumber;
        PaymentTransaction current = getPayment(orderNumber);

        if (current.getMethod() != null) {
            validateCaptureIdentity(current, orderNumber, amount, method);
        }

        if (current.getStatus() == PaymentStatus.CAPTURED || current.getStatus() == PaymentStatus.PARTIALLY_REFUNDED
                || current.getStatus() == PaymentStatus.REFUNDED || current.getStatus() == PaymentStatus.DECLINED) {

            managePaymentReconciliationOutPort.complete(operationId);
            return current;
        }

        if (current.getCreated() == null) {
            current = preparePaymentProviderOperationOutPort.prepareCapture(
                    operationId,
                    PaymentTransaction.builder()
                            .orderNumber(orderNumber)
                            .amount(amount)
                            .refundedAmount(BigDecimal.ZERO)
                            .method(method)
                            .status(PaymentStatus.PENDING)
                            .created(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
                            .build());
        }

        try {
            final String gatewayReference = chargePaymentOutPort.charge(orderNumber, operationId, amount, method);
            final PaymentTransaction captured = savePaymentTransactionOutPort.saveCaptureResult(
                    PaymentTransaction.builder()
                            .orderNumber(orderNumber)
                            .amount(amount)
                            .refundedAmount(BigDecimal.ZERO)
                            .method(method)
                            .status(PaymentStatus.CAPTURED)
                            .gatewayReference(gatewayReference)
                            .created(current.getCreated())
                            .build());
            managePaymentReconciliationOutPort.complete(operationId);
            return captured;
        } catch (final PaymentDeclinedException declined) {
            savePaymentTransactionOutPort.saveCaptureResult(
                    PaymentTransaction.builder()
                            .orderNumber(orderNumber)
                            .amount(amount)
                            .refundedAmount(BigDecimal.ZERO)
                            .method(method)
                            .status(PaymentStatus.DECLINED)
                            .created(current.getCreated())
                            .build());
            managePaymentReconciliationOutPort.complete(operationId);
            throw declined;
        }
    }

    private static void validateCaptureIdentity(
            final PaymentTransaction current,
            final String orderNumber,
            final BigDecimal amount,
            final PaymentMethod method) {

        if (current.getAmount() == null || current.getAmount().compareTo(amount) != 0 || current.getMethod() != method) {
            throw new com.cp.ecommerce.foundation.exception.PaymentOperationConflictException(
                    "Payment operation identity was reused with different capture parameters for order " + orderNumber);
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

    @Override
    public boolean hasPendingRefunds(final String orderNumber) {

        return managePaymentRefundOutPort.hasPending(orderNumber);
    }

    private static PaymentTransaction pendingPayment(final String orderNumber) {

        return PaymentTransaction.builder().orderNumber(orderNumber).build();
    }

    private PaymentTransaction executeRefund(final PaymentRefundClaim claim) {

        if (claim.outcome() == PaymentRefundOutcome.NOTHING_TO_REFUND) {

            return getPayment(claim.orderNumber());
        }
        if (claim.outcome() == PaymentRefundOutcome.COMPLETED) {

            managePaymentReconciliationOutPort.complete(claim.refundId());
            return claim.payment();
        }

        managePaymentReconciliationOutPort
                .start(claim.refundId(), claim.orderNumber(), PaymentProviderOperationType.REFUND, claim.refundId());
        refundPaymentOutPort.refund(claim.orderNumber(), claim.gatewayReference(), claim.refundId(), claim.amount());
        final PaymentTransaction completed = managePaymentRefundOutPort.complete(claim.refundId());
        managePaymentReconciliationOutPort.complete(claim.refundId());
        return completed;
    }
}
