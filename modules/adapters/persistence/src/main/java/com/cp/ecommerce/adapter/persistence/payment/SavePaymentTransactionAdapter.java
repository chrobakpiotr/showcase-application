package com.cp.ecommerce.adapter.persistence.payment;

import java.math.BigDecimal;
import java.util.Objects;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntityRepository;
import com.cp.ecommerce.adapter.persistence.payment.mapper.PaymentTransactionPersistenceMapper;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.outgoing.SavePaymentTransactionOutPort;
import com.cp.ecommerce.foundation.exception.PaymentOperationConflictException;

import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link SavePaymentTransactionOutPort}.
 */
@PersistenceAdapter
@Transactional
@RequiredArgsConstructor
class SavePaymentTransactionAdapter implements SavePaymentTransactionOutPort {

    private final PaymentTransactionEntityRepository paymentTransactionEntityRepository;

    private final PaymentTransactionPersistenceMapper paymentTransactionPersistenceMapper;

    private final EntityManager entityManager;

    @Override
    public PaymentTransaction save(final PaymentTransaction paymentTransaction) {

        final PaymentTransactionEntity entityToSave = paymentTransactionPersistenceMapper.mapToEntity(paymentTransaction)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map payment transaction domain object to entity for order: "
                                        + paymentTransaction.getOrderNumber()));
        final PaymentTransactionEntity saved = paymentTransactionEntityRepository.save(entityToSave);
        return paymentTransactionPersistenceMapper.mapToDomainObject(saved)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map payment transaction entity to domain object for order: "
                                        + paymentTransaction.getOrderNumber()));
    }

    @Override
    @Transactional
    public PaymentTransaction prepareCapture(final PaymentTransaction pendingPayment) {

        insertPendingIfAbsent(pendingPayment);
        final PaymentTransactionEntity current = paymentTransactionEntityRepository
                .findByOrderNumberForUpdate(pendingPayment.getOrderNumber())
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Payment transaction disappeared during capture preparation for order: "
                                        + pendingPayment.getOrderNumber()));
        validateCaptureIdentity(current, pendingPayment);
        return mapCurrent(current);
    }

    @Override
    @Transactional
    public PaymentTransaction saveCaptureResult(final PaymentTransaction paymentTransaction) {

        final var current = paymentTransactionEntityRepository.findByOrderNumberForUpdate(paymentTransaction.getOrderNumber())
                .orElse(null);
        if (current != null && (current.getStatus() == PaymentStatus.CAPTURED
                || current.getStatus() == PaymentStatus.PARTIALLY_REFUNDED || current.getStatus() == PaymentStatus.REFUNDED)) {

            return paymentTransactionPersistenceMapper.mapToDomainObject(current)
                    .orElseThrow(
                            () -> new IllegalStateException(
                                    "Failed to map current payment for order: " + paymentTransaction.getOrderNumber()));
        }
        return save(paymentTransaction);
    }

    private void insertPendingIfAbsent(final PaymentTransaction pendingPayment) {

        entityManager
                .createNativeQuery(
                        "insert into test_db.PAYMENT_TRANSACTION "
                                + "(ORDER_NUMBER, AMOUNT, REFUNDED_AMOUNT, METHOD, STATUS, CREATION_DATE) "
                                + "values (:orderNumber, :amount, :refundedAmount, :method, :status, :created) "
                                + "on conflict (ORDER_NUMBER) do nothing")
                .setParameter("orderNumber", pendingPayment.getOrderNumber())
                .setParameter("amount", pendingPayment.getAmount())
                .setParameter("refundedAmount", defaultRefundedAmount(pendingPayment))
                .setParameter("method", pendingPayment.getMethod().name())
                .setParameter("status", PaymentStatus.PENDING.name())
                .setParameter("created", pendingPayment.getCreated())
                .executeUpdate();
    }

    private static BigDecimal defaultRefundedAmount(final PaymentTransaction payment) {

        return payment.getRefundedAmount() == null ? BigDecimal.ZERO : payment.getRefundedAmount();
    }

    private static void validateCaptureIdentity(final PaymentTransactionEntity current, final PaymentTransaction requested) {

        if (!Objects.equals(current.getOrderNumber(), requested.getOrderNumber())
                || current.getAmount().compareTo(requested.getAmount()) != 0 || current.getMethod() != requested.getMethod()) {

            throw new PaymentOperationConflictException(
                    "Payment capture identity was reused with different immutable parameters for order "
                            + requested.getOrderNumber());
        }
    }

    private PaymentTransaction mapCurrent(final PaymentTransactionEntity current) {

        return paymentTransactionPersistenceMapper.mapToDomainObject(current)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map canonical payment for order: " + current.getOrderNumber()));
    }

}
