package com.cp.ecommerce.adapter.persistence.payment.mapper;

import com.cp.ecommerce.adapter.common.utils.PaymentTransactionBuilder;
import com.cp.ecommerce.adapter.persistence.utils.PaymentTransactionEntityBuilder;
import com.cp.ecommerce.domain.payment.PaymentTransaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link PaymentTransactionPersistenceMapper}.
 */
class PaymentTransactionPersistenceMapperTest {

    private final transient PaymentTransactionPersistenceMapper paymentTransactionPersistenceMapper = new PaymentTransactionPersistenceMapper();

    @Test
    void shouldMapToEntity() {

        final PaymentTransaction paymentTransaction = PaymentTransactionBuilder.mockPaymentTransaction();

        final var result = paymentTransactionPersistenceMapper.mapToEntity(paymentTransaction);

        assertTrue(result.isPresent());
        assertEquals(paymentTransaction.getOrderNumber(), result.get().getOrderNumber());
        assertEquals(paymentTransaction.getAmount(), result.get().getAmount());
        assertEquals(paymentTransaction.getMethod(), result.get().getMethod());
        assertEquals(paymentTransaction.getStatus(), result.get().getStatus());
        assertEquals(paymentTransaction.getGatewayReference(), result.get().getGatewayReference());
    }

    @Test
    void shouldMapToDomainObject() {

        final var entity = PaymentTransactionEntityBuilder.mockPaymentTransactionEntity();

        final var result = paymentTransactionPersistenceMapper.mapToDomainObject(entity);

        assertTrue(result.isPresent());
        assertEquals(entity.getOrderNumber(), result.get().getOrderNumber());
        assertEquals(entity.getAmount(), result.get().getAmount());
        assertEquals(entity.getMethod(), result.get().getMethod());
        assertEquals(entity.getStatus(), result.get().getStatus());
        assertEquals(entity.getGatewayReference(), result.get().getGatewayReference());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToEntity() {

        assertTrue(paymentTransactionPersistenceMapper.mapToEntity(null).isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToDomainObject() {

        assertTrue(paymentTransactionPersistenceMapper.mapToDomainObject(null).isEmpty());
    }

}
