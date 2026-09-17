package com.cp.ecommerce.adapter.persistence.payment;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.PaymentTransactionBuilder;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;

import static com.cp.ecommerce.adapter.common.utils.PaymentTransactionBuilder.TEST_ORDER_NUMBER;

/**
 * Test class for {@link FindPaymentTransactionAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class FindPaymentTransactionAdapterTest {

    @InjectMocks
    private transient FindPaymentTransactionAdapter findPaymentTransactionAdapter;

    @Mock
    private transient PaymentTransactionEntityRepository paymentTransactionEntityRepository;

    @Mock
    private transient PaymentTransactionPersistenceMapper paymentTransactionPersistenceMapper;

    @Test
    void shouldFindPaymentTransactionByOrderNumber() {

        final var entity = PaymentTransactionEntityBuilder.mockPaymentTransactionEntity();
        final PaymentTransaction paymentTransaction = PaymentTransactionBuilder.mockPaymentTransaction();
        doReturn(Optional.of(entity)).when(paymentTransactionEntityRepository).findById(TEST_ORDER_NUMBER);
        doReturn(Optional.of(paymentTransaction)).when(paymentTransactionPersistenceMapper).mapToDomainObject(entity);

        final PaymentTransaction result = findPaymentTransactionAdapter.find(TEST_ORDER_NUMBER);

        assertEquals(paymentTransaction, result);
    }

    @Test
    void shouldReturnNullWhenPaymentTransactionNotFound() {

        doReturn(Optional.empty()).when(paymentTransactionEntityRepository).findById(TEST_ORDER_NUMBER);

        final PaymentTransaction result = findPaymentTransactionAdapter.find(TEST_ORDER_NUMBER);

        assertNull(result);
    }

}
