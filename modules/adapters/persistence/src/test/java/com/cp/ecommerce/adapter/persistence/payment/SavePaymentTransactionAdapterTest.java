package com.cp.ecommerce.adapter.persistence.payment;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.PaymentTransactionBuilder;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntityRepository;
import com.cp.ecommerce.adapter.persistence.payment.mapper.PaymentTransactionPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.PaymentTransactionEntityBuilder;
import com.cp.ecommerce.domain.payment.PaymentStatus;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
    void shouldPreserveRefundedStateWhenLateCaptureCompletionArrives() {

        final PaymentTransaction lateCapture = PaymentTransactionBuilder.mockPaymentTransaction();
        final PaymentTransactionEntity current = PaymentTransactionEntityBuilder.mockPaymentTransactionEntity();
        current.setStatus(PaymentStatus.REFUNDED);
        current.setRefundedAmount(current.getAmount());
        final PaymentTransaction refunded = PaymentTransaction.builder()
                .orderNumber(current.getOrderNumber())
                .amount(current.getAmount())
                .refundedAmount(current.getAmount())
                .method(current.getMethod())
                .status(PaymentStatus.REFUNDED)
                .gatewayReference(current.getGatewayReference())
                .created(current.getCreated())
                .build();

        doReturn(Optional.of(current)).when(paymentTransactionEntityRepository)
                .findByOrderNumberForUpdate(lateCapture.getOrderNumber());
        doReturn(Optional.of(refunded)).when(paymentTransactionPersistenceMapper).mapToDomainObject(current);

        assertEquals(refunded, savePaymentTransactionAdapter.saveCaptureResult(lateCapture));
        verify(paymentTransactionPersistenceMapper, never()).mapToEntity(lateCapture);
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

    @Test
    void shouldFallBackToSaveForNonTerminalCurrentPayment() {

        final PaymentTransaction paymentTransaction = PaymentTransactionBuilder.mockPaymentTransaction();
        final PaymentTransactionEntity current = PaymentTransactionEntityBuilder.mockPaymentTransactionEntity();
        final PaymentTransactionEntity mappedEntity = PaymentTransactionEntityBuilder.mockPaymentTransactionEntity();
        current.setStatus(PaymentStatus.PENDING);
        doReturn(Optional.of(current)).when(paymentTransactionEntityRepository)
                .findByOrderNumberForUpdate(paymentTransaction.getOrderNumber());
        doReturn(Optional.of(mappedEntity)).when(paymentTransactionPersistenceMapper).mapToEntity(paymentTransaction);
        doReturn(mappedEntity).when(paymentTransactionEntityRepository).save(mappedEntity);
        doReturn(Optional.of(paymentTransaction)).when(paymentTransactionPersistenceMapper).mapToDomainObject(mappedEntity);

        assertEquals(paymentTransaction, savePaymentTransactionAdapter.saveCaptureResult(paymentTransaction));
    }

    @Test
    void shouldFailWhenTerminalCurrentPaymentCannotBeMapped() {

        final PaymentTransaction paymentTransaction = PaymentTransactionBuilder.mockPaymentTransaction();
        final PaymentTransactionEntity current = PaymentTransactionEntityBuilder.mockPaymentTransactionEntity();
        current.setStatus(PaymentStatus.REFUNDED);
        doReturn(Optional.of(current)).when(paymentTransactionEntityRepository)
                .findByOrderNumberForUpdate(paymentTransaction.getOrderNumber());
        doReturn(Optional.empty()).when(paymentTransactionPersistenceMapper).mapToDomainObject(current);

        assertThrows(IllegalStateException.class, () -> savePaymentTransactionAdapter.saveCaptureResult(paymentTransaction));
    }

}
