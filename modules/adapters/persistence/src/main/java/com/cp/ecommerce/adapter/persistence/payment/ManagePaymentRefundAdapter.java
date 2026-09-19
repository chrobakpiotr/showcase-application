package com.cp.ecommerce.adapter.persistence.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentRefundEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentRefundEntityRepository;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntityRepository;
import com.cp.ecommerce.adapter.persistence.payment.mapper.PaymentTransactionPersistenceMapper;
import com.cp.ecommerce.domain.payment.PaymentRefundClaim;
import com.cp.ecommerce.domain.payment.PaymentRefundOutcome;
import com.cp.ecommerce.domain.payment.PaymentRefundStatus;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.outgoing.ManagePaymentRefundOutPort;
import com.cp.ecommerce.foundation.exception.PaymentRefundConflictException;

import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * PostgreSQL/JPA arbitration boundary for refund claims and completion.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class ManagePaymentRefundAdapter implements ManagePaymentRefundOutPort {

    private final PaymentTransactionEntityRepository paymentTransactionEntityRepository;

    private final PaymentRefundEntityRepository paymentRefundEntityRepository;

    private final PaymentTransactionPersistenceMapper paymentTransactionPersistenceMapper;

    @Override
    @Transactional
    public PaymentRefundClaim reserve(final String refundId, final String orderNumber, final BigDecimal amount) {

        final PaymentTransactionEntity payment = requirePayment(orderNumber);
        final PaymentRefundEntity existing = paymentRefundEntityRepository.findById(refundId).orElse(null);
        if (existing != null) {

            validateExistingPartial(existing, orderNumber, amount);
            return existingClaim(existing, payment);
        }

        requireRefundable(payment);
        final BigDecimal remaining = remainingRefundableAmount(payment, orderNumber);
        validatePartialAmount(amount, remaining, orderNumber);

        return createPendingRefund(refundId, orderNumber, amount, payment);
    }

    @Override
    @Transactional
    public PaymentRefundClaim reserveRemaining(final String refundId, final String orderNumber) {

        final Optional<PaymentTransactionEntity> lockedPayment = paymentTransactionEntityRepository
                .findByOrderNumberForUpdate(orderNumber);
        if (lockedPayment.isEmpty()) {

            return nothingToRefund(refundId, orderNumber);
        }

        final PaymentTransactionEntity payment = lockedPayment.get();
        final PaymentRefundEntity existing = paymentRefundEntityRepository.findById(refundId).orElse(null);
        if (existing != null) {

            validateExistingOrder(existing, orderNumber);
            return existingClaim(existing, payment);
        }

        if (!isRefundable(payment.getStatus())) {

            return nothingToRefund(refundId, orderNumber);
        }

        final BigDecimal remaining = remainingRefundableAmount(payment, orderNumber);
        if (remaining.signum() <= 0) {

            return nothingToRefund(refundId, orderNumber);
        }

        return createPendingRefund(refundId, orderNumber, remaining, payment);
    }

    @Override
    @Transactional
    public PaymentTransaction complete(final String refundId) {

        final String orderNumber = paymentRefundEntityRepository.findOrderNumberByRefundId(refundId)
                .orElseThrow(() -> new IllegalStateException("Unknown payment refund: " + refundId));
        final PaymentTransactionEntity payment = paymentTransactionEntityRepository.findByOrderNumberForUpdate(orderNumber)
                .orElseThrow(() -> new IllegalStateException("Payment missing for refund: " + refundId));
        final PaymentRefundEntity refund = paymentRefundEntityRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new IllegalStateException("Payment refund disappeared: " + refundId));

        if (refund.getStatus() == PaymentRefundStatus.COMPLETED) {

            return toDomain(payment);
        }

        final BigDecimal refundedAmount = payment.getRefundedAmount().add(refund.getAmount());
        if (refundedAmount.compareTo(payment.getAmount()) > 0) {

            throw conflict("Refund would exceed captured amount for order " + orderNumber);
        }

        payment.setRefundedAmount(refundedAmount);
        payment.setStatus(paymentStatusAfterRefund(payment, refundedAmount));
        final PaymentTransactionEntity saved = paymentTransactionEntityRepository.saveAndFlush(payment);

        refund.setStatus(PaymentRefundStatus.COMPLETED);
        refund.setCompleted(Instant.ofEpochMilli(Instant.now().toEpochMilli()));
        paymentRefundEntityRepository.save(refund);

        return toDomain(saved);
    }

    private PaymentTransactionEntity requirePayment(final String orderNumber) {

        return paymentTransactionEntityRepository.findByOrderNumberForUpdate(orderNumber)
                .orElseThrow(() -> conflict("Payment has not been captured for order " + orderNumber));
    }

    private void requireRefundable(final PaymentTransactionEntity payment) {

        if (!isRefundable(payment.getStatus())) {

            throw conflict("Payment is not refundable while it is " + payment.getStatus());
        }
    }

    private BigDecimal remainingRefundableAmount(final PaymentTransactionEntity payment, final String orderNumber) {

        final BigDecimal pendingAmount = Optional
                .ofNullable(
                        paymentRefundEntityRepository.sumAmountByOrderNumberAndStatus(orderNumber, PaymentRefundStatus.PENDING))
                .orElse(BigDecimal.ZERO);

        return payment.getAmount().subtract(payment.getRefundedAmount()).subtract(pendingAmount);
    }

    private void validatePartialAmount(final BigDecimal amount, final BigDecimal remaining, final String orderNumber) {

        if (amount == null || amount.signum() <= 0) {

            throw conflict("Refund amount must be greater than zero");
        }
        if (amount.compareTo(remaining) > 0) {

            throw conflict(
                    "Refund amount " + amount + " exceeds remaining refundable amount " + remaining + " for order "
                            + orderNumber);
        }
    }

    private void validateExistingPartial(
            final PaymentRefundEntity existing,
            final String orderNumber,
            final BigDecimal requestedAmount) {

        validateExistingOrder(existing, orderNumber);
        if (existing.getAmount().compareTo(requestedAmount) != 0) {

            throw conflict("Refund identity " + existing.getRefundId() + " is already used for another amount");
        }
    }

    private void validateExistingOrder(final PaymentRefundEntity existing, final String orderNumber) {

        if (!existing.getOrderNumber().equals(orderNumber)) {

            throw conflict("Refund identity " + existing.getRefundId() + " is already used for another order");
        }
    }

    private PaymentRefundClaim createPendingRefund(
            final String refundId,
            final String orderNumber,
            final BigDecimal amount,
            final PaymentTransactionEntity payment) {

        final PaymentRefundEntity refund = PaymentRefundEntity.builder()
                .refundId(refundId)
                .orderNumber(orderNumber)
                .amount(amount)
                .status(PaymentRefundStatus.PENDING)
                .created(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
                .build();
        paymentRefundEntityRepository.save(refund);

        return new PaymentRefundClaim(
                PaymentRefundOutcome.RESERVED,
                refundId,
                orderNumber,
                amount,
                payment.getGatewayReference(),
                toDomain(payment));
    }

    private PaymentRefundClaim existingClaim(final PaymentRefundEntity existing, final PaymentTransactionEntity payment) {

        final PaymentRefundOutcome outcome = existing.getStatus() == PaymentRefundStatus.COMPLETED
                ? PaymentRefundOutcome.COMPLETED
                : PaymentRefundOutcome.RETRY;

        return new PaymentRefundClaim(
                outcome,
                existing.getRefundId(),
                existing.getOrderNumber(),
                existing.getAmount(),
                payment.getGatewayReference(),
                toDomain(payment));
    }

    private PaymentRefundClaim nothingToRefund(final String refundId, final String orderNumber) {

        return new PaymentRefundClaim(
                PaymentRefundOutcome.NOTHING_TO_REFUND,
                refundId,
                orderNumber,
                BigDecimal.ZERO,
                null,
                null);
    }

    private PaymentTransaction toDomain(final PaymentTransactionEntity payment) {

        return paymentTransactionPersistenceMapper.mapToDomainObject(payment)
                .orElseThrow(() -> new IllegalStateException("Could not map payment for order " + payment.getOrderNumber()));
    }

    private static PaymentStatus paymentStatusAfterRefund(
            final PaymentTransactionEntity payment,
            final BigDecimal refundedAmount) {

        return refundedAmount.compareTo(payment.getAmount()) == 0 ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
    }

    private static boolean isRefundable(final PaymentStatus status) {

        return status == PaymentStatus.CAPTURED || status == PaymentStatus.PARTIALLY_REFUNDED;
    }

    private static PaymentRefundConflictException conflict(final String message) {

        return new PaymentRefundConflictException(message);
    }
}
