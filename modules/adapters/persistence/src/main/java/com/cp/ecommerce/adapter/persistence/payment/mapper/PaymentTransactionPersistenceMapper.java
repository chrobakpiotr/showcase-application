package com.cp.ecommerce.adapter.persistence.payment.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.PersistenceMapper;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntity;
import com.cp.ecommerce.domain.payment.PaymentTransaction;

import org.springframework.stereotype.Component;

import static java.util.Optional.ofNullable;

/**
 * Mapper responsible for changing {@link PaymentTransaction} object into/from entity object.
 */
@Component
public class PaymentTransactionPersistenceMapper implements PersistenceMapper<PaymentTransaction, PaymentTransactionEntity> {

    @Override
    public Optional<PaymentTransactionEntity> mapToEntity(final PaymentTransaction paymentTransaction) {

        return ofNullable(paymentTransaction).map(
                domain -> PaymentTransactionEntity.builder()
                        .orderNumber(domain.getOrderNumber())
                        .amount(domain.getAmount())
                        .method(domain.getMethod())
                        .status(domain.getStatus())
                        .gatewayReference(domain.getGatewayReference())
                        .created(domain.getCreated())
                        .build());
    }

    @Override
    public Optional<PaymentTransaction> mapToDomainObject(final PaymentTransactionEntity entity) {

        return ofNullable(entity).map(
                paymentTransactionEntity -> PaymentTransaction.builder()
                        .orderNumber(paymentTransactionEntity.getOrderNumber())
                        .amount(paymentTransactionEntity.getAmount())
                        .method(paymentTransactionEntity.getMethod())
                        .status(paymentTransactionEntity.getStatus())
                        .gatewayReference(paymentTransactionEntity.getGatewayReference())
                        .created(paymentTransactionEntity.getCreated())
                        .build());
    }

}
