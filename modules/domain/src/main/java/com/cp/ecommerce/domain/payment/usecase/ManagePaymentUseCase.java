package com.cp.ecommerce.domain.payment.usecase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.payment.PaymentRecoveryContext;
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
import com.cp.ecommerce.foundation.exception.PaymentOperationConflictException;

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

        return capturePayment(orderNumber, amount, method, null);
    }

    @Override
    public PaymentTransaction recoverCapturePayment(
            final String orderNumber,
            final BigDecimal amount,
            final PaymentMethod method,
            final PaymentRecoveryContext recoveryContext) {

        final String operationId = ORDER_CAPTURE_PREFIX + orderNumber;
        validateRecoveryContext(recoveryContext, operationId);
        return capturePayment(orderNumber, amount, method, recoveryContext);
    }

    private PaymentTransaction capturePayment(
            final String orderNumber,
            final BigDecimal amount,
            final PaymentMethod method,
            final PaymentRecoveryContext recoveryContext) {

        final String operationId = ORDER_CAPTURE_PREFIX + orderNumber;
        PaymentTransaction current = getPayment(orderNumber);
        validateCaptureIdentity(current, amount, method);
        if (captureAlreadyResolved(current)) {

            completeReconciliation(operationId, recoveryContext);
            return current;
        }

        if (current.getCreated() == null) {
            current = PaymentTransaction.builder()
                    .orderNumber(orderNumber)
                    .amount(amount)
                    .refundedAmount(BigDecimal.ZERO)
                    .method(method)
                    .status(PaymentStatus.PENDING)
                    .created(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
                    .build();
        }
        current = recoveryContext == null
                ? preparePaymentProviderOperationOutPort.prepareCapture(operationId, current)
                : preparePaymentProviderOperationOutPort.prepareCapture(operationId, current, recoveryContext);
        validateCaptureIdentity(current, amount, method);
        if (captureAlreadyResolved(current)) {

            completeReconciliation(operationId, recoveryContext);
            return current;
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
            completeReconciliation(operationId, recoveryContext);
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
            completeReconciliation(operationId, recoveryContext);
            throw declined;
        }
    }

    @Override
    public PaymentTransaction refundPayment(final String orderNumber) {

        final String refundId = ORDER_REFUND_PREFIX + orderNumber;
        return executeRefund(preparePaymentProviderOperationOutPort.prepareRefund(refundId, orderNumber, null), null);
    }

    @Override
    public PaymentTransaction refundPayment(final String orderNumber, final String refundId, final BigDecimal amount) {

        return refundPayment(orderNumber, refundId, amount, null);
    }

    @Override
    public PaymentTransaction recoverRefundPayment(
            final String orderNumber,
            final String refundId,
            final BigDecimal amount,
            final PaymentRecoveryContext recoveryContext) {

        validateRecoveryContext(recoveryContext, refundId);
        return refundPayment(orderNumber, refundId, amount, recoveryContext);
    }

    private PaymentTransaction refundPayment(
            final String orderNumber,
            final String refundId,
            final BigDecimal amount,
            final PaymentRecoveryContext recoveryContext) {

        final PaymentRefundClaim claim = recoveryContext == null
                ? preparePaymentProviderOperationOutPort.prepareRefund(refundId, orderNumber, amount)
                : preparePaymentProviderOperationOutPort.prepareRefund(refundId, orderNumber, amount, recoveryContext);
        return executeRefund(claim, recoveryContext);
    }

    @Override
    public boolean hasPendingRefunds(final String orderNumber) {

        return managePaymentRefundOutPort.hasPending(orderNumber);
    }

    private static boolean captureAlreadyResolved(final PaymentTransaction payment) {

        return payment.getStatus() == PaymentStatus.CAPTURED || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED
                || payment.getStatus() == PaymentStatus.REFUNDED || payment.getStatus() == PaymentStatus.DECLINED;
    }

    private static void validateCaptureIdentity(
            final PaymentTransaction current,
            final BigDecimal amount,
            final PaymentMethod method) {

        if (current.getMethod() != null && (current.getAmount().compareTo(amount) != 0 || current.getMethod() != method)) {
            throw new PaymentOperationConflictException(
                    "Payment operation identity was reused with different capture parameters for order "
                            + current.getOrderNumber());
        }
    }

    private static void validateRecoveryContext(
            final PaymentRecoveryContext recoveryContext,
            final String expectedOperationId) {

        final PaymentRecoveryContext context = Objects.requireNonNull(recoveryContext, "recoveryContext");
        if (!Objects.equals(context.operationId(), expectedOperationId)) {
            throw new PaymentOperationConflictException(
                    "Recovery operation identity " + context.operationId() + " does not match expected provider operation "
                            + expectedOperationId);
        }
    }

    private static PaymentTransaction pendingPayment(final String orderNumber) {

        return PaymentTransaction.builder().orderNumber(orderNumber).build();
    }

    private PaymentTransaction executeRefund(final PaymentRefundClaim claim, final PaymentRecoveryContext recoveryContext) {

        if (claim.outcome() == PaymentRefundOutcome.NOTHING_TO_REFUND) {

            return getPayment(claim.orderNumber());
        }
        if (claim.outcome() == PaymentRefundOutcome.COMPLETED) {

            completeReconciliation(claim.refundId(), recoveryContext);
            return claim.payment();
        }

        refundPaymentOutPort.refund(claim.orderNumber(), claim.gatewayReference(), claim.refundId(), claim.amount());
        final PaymentTransaction completed = managePaymentRefundOutPort.complete(claim.refundId());
        completeReconciliation(claim.refundId(), recoveryContext);
        return completed;
    }

    private void completeReconciliation(final String operationId, final PaymentRecoveryContext recoveryContext) {

        if (recoveryContext == null) {
            managePaymentReconciliationOutPort.complete(operationId);
        } else {
            managePaymentReconciliationOutPort.completeOwned(recoveryContext);
        }
    }
}
