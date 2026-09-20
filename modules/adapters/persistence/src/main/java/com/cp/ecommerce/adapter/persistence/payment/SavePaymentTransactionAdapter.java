package com.cp.ecommerce.adapter.persistence.payment;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntityRepository;
import com.cp.ecommerce.adapter.persistence.payment.mapper.PaymentTransactionPersistenceMapper;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.outgoing.SavePaymentTransactionOutPort;

import org.springframework.transaction.annotation.Transactional;

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

}
