package com.cp.ecommerce.adapter.persistence.payment;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    @Override
    public Map<String, PaymentTransaction> findAll(final Collection<String> orderNumbers) {

        return paymentTransactionEntityRepository.findAllById(orderNumbers)
                .stream()
                .map(paymentTransactionPersistenceMapper::mapToDomainObject)
                .flatMap(mapped -> mapped.stream())
                .collect(Collectors.toMap(PaymentTransaction::getOrderNumber, Function.identity()));
    }

}
