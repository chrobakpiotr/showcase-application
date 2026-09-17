package com.cp.ecommerce.adapter.persistence.payment;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntityRepository;
import com.cp.ecommerce.adapter.persistence.payment.mapper.PaymentTransactionPersistenceMapper;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.outgoing.FindPaymentTransactionOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link FindPaymentTransactionOutPort}.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindPaymentTransactionAdapter implements FindPaymentTransactionOutPort {

    private final PaymentTransactionEntityRepository paymentTransactionEntityRepository;

    private final PaymentTransactionPersistenceMapper paymentTransactionPersistenceMapper;

    @Override
    public PaymentTransaction find(final String orderNumber) {

        return paymentTransactionEntityRepository.findById(orderNumber)
                .flatMap(paymentTransactionPersistenceMapper::mapToDomainObject)
                .orElse(null);
    }

}
