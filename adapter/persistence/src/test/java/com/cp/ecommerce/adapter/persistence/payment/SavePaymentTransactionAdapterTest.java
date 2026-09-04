package com.cp.ecommerce.adapter.persistence.payment;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.PaymentTransactionBuilder;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntityRepository;
import com.cp.ecommerce.adapter.persistence.payment.mapper.PaymentTransactionPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.PaymentTransactionEntityBuilder;
import com.cp.ecommerce.domain.payment.PaymentTransaction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * Test class for {@link SavePaymentTransactionAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class SavePaymentTransactionAdapterTest {

    @InjectMocks
    private transient SavePaymentTransactionAdapter savePaymentTransactionAdapter;

    @Mock
    private transient PaymentTransactionEntityRepository paymentTransactionEntityRepository;

    @Mock
    private transient PaymentTransactionPersistenceMapper paymentTransactionPersistenceMapper;

    @Test
    void shouldSaveAndReturnMappedPaymentTransaction() {

        final PaymentTransaction paymentTransaction = PaymentTransactionBuilder.mockPaymentTransaction();
        final PaymentTransactionEntity mappedEntity = PaymentTransactionEntityBuilder.mockPaymentTransactionEntity();
        doReturn(Optional.of(mappedEntity)).when(paymentTransactionPersistenceMapper).mapToEntity(eq(paymentTransaction));
        doReturn(mappedEntity).when(paymentTransactionEntityRepository).save(mappedEntity);
        doReturn(Optional.of(paymentTransaction)).when(paymentTransactionPersistenceMapper).mapToDomainObject(mappedEntity);

        final PaymentTransaction result = savePaymentTransactionAdapter.save(paymentTransaction);

        assertEquals(paymentTransaction, result);
    }

    @Test
    void shouldThrowExceptionWhenMappingToEntityFails() {

        final PaymentTransaction paymentTransaction = PaymentTransactionBuilder.mockPaymentTransaction();
        doReturn(Optional.empty()).when(paymentTransactionPersistenceMapper).mapToEntity(eq(paymentTransaction));

        assertThrows(IllegalStateException.class, () -> savePaymentTransactionAdapter.save(paymentTransaction));
    }

    @Test
    void shouldThrowExceptionWhenMappingToDomainObjectFails() {

        final PaymentTransaction paymentTransaction = PaymentTransactionBuilder.mockPaymentTransaction();
        final PaymentTransactionEntity mappedEntity = PaymentTransactionEntityBuilder.mockPaymentTransactionEntity();
        doReturn(Optional.of(mappedEntity)).when(paymentTransactionPersistenceMapper).mapToEntity(eq(paymentTransaction));
        doReturn(mappedEntity).when(paymentTransactionEntityRepository).save(mappedEntity);
        doReturn(Optional.empty()).when(paymentTransactionPersistenceMapper).mapToDomainObject(mappedEntity);

        assertThrows(IllegalStateException.class, () -> savePaymentTransactionAdapter.save(paymentTransaction));
    }

}
