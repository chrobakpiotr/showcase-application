package com.cp.ecommerce.adapter.persistence.payment.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.PersistenceMapper;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntity;
import com.cp.ecommerce.domain.payment.PaymentTransaction;

import org.springframework.stereotype.Component;

import static java.util.Optional.ofNullable;

/**
 * Maps payment transactions to/from persistence.
 */
@Component
public class PaymentTransactionPersistenceMapper implements PersistenceMapper<PaymentTransaction, PaymentTransactionEntity> {

    @Override
    public Optional<PaymentTransactionEntity> mapToEntity(final PaymentTransaction paymentTransaction) {

        return ofNullable(paymentTransaction).map(
                domain -> PaymentTransactionEntity.builder()
                        .orderNumber(domain.getOrderNumber())
                        .amount(domain.getAmount())
                        .refundedAmount(domain.getRefundedAmount())
                        .method(domain.getMethod())
                        .status(domain.getStatus())
                        .gatewayReference(domain.getGatewayReference())
                        .created(domain.getCreated())
                        .build());
    }

    @Override
    public Optional<PaymentTransaction> mapToDomainObject(final PaymentTransactionEntity entity) {

        return ofNullable(entity).map(
                persisted -> PaymentTransaction.builder()
                        .orderNumber(persisted.getOrderNumber())
                        .amount(persisted.getAmount())
                        .refundedAmount(persisted.getRefundedAmount())
                        .method(persisted.getMethod())
                        .status(persisted.getStatus())
                        .gatewayReference(persisted.getGatewayReference())
                        .created(persisted.getCreated())
                        .build());
    }
}
