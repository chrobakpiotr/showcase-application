package com.cp.ecommerce.adapter.persistence.payment;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.PaymentTransactionBuilder;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntityRepository;
import com.cp.ecommerce.adapter.persistence.payment.mapper.PaymentTransactionPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.PaymentTransactionEntityBuilder;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.foundation.exception.PaymentOperationConflictException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

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

    @Mock
    private transient EntityManager entityManager;

    @Mock
    private transient Query nativeQuery;

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
    void shouldReturnCanonicalRefundedPaymentInsteadOfStalePendingSnapshot() {

        final PaymentTransaction base = PaymentTransactionBuilder.mockPaymentTransaction();
        final PaymentTransaction stale = PaymentTransaction.builder()
                .orderNumber(base.getOrderNumber())
                .amount(base.getAmount())
                .refundedAmount(base.getRefundedAmount())
                .method(base.getMethod())
                .status(PaymentStatus.PENDING)
                .gatewayReference(base.getGatewayReference())
                .created(base.getCreated())
                .build();
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

        stubNativeInsert();
        doReturn(Optional.of(current)).when(paymentTransactionEntityRepository)
                .findByOrderNumberForUpdate(stale.getOrderNumber());
        doReturn(Optional.of(refunded)).when(paymentTransactionPersistenceMapper).mapToDomainObject(current);

        assertEquals(refunded, savePaymentTransactionAdapter.prepareCapture(stale));
        verify(paymentTransactionPersistenceMapper, never()).mapToEntity(stale);
    }

    @Test
    void shouldRejectCanonicalCaptureWithDifferentImmutableFingerprint() {

        final PaymentTransaction base = PaymentTransactionBuilder.mockPaymentTransaction();
        final PaymentTransaction stale = PaymentTransaction.builder()
                .orderNumber(base.getOrderNumber())
                .amount(base.getAmount())
                .refundedAmount(base.getRefundedAmount())
                .method(base.getMethod())
                .status(PaymentStatus.PENDING)
                .gatewayReference(base.getGatewayReference())
                .created(base.getCreated())
                .build();
        final PaymentTransactionEntity current = PaymentTransactionEntityBuilder.mockPaymentTransactionEntity();
        current.setAmount(current.getAmount().add(java.math.BigDecimal.ONE));

        stubNativeInsert();
        doReturn(Optional.of(current)).when(paymentTransactionEntityRepository)
                .findByOrderNumberForUpdate(stale.getOrderNumber());

        assertThrows(PaymentOperationConflictException.class, () -> savePaymentTransactionAdapter.prepareCapture(stale));
    }

    @Test
    void shouldFailWhenCanonicalPaymentDisappearsAfterRaceSafeInsert() {

        final PaymentTransaction pending = pendingPayment();

        stubNativeInsert();
        doReturn(Optional.empty()).when(paymentTransactionEntityRepository)
                .findByOrderNumberForUpdate(pending.getOrderNumber());

        assertThrows(IllegalStateException.class, () -> savePaymentTransactionAdapter.prepareCapture(pending));
    }

    @Test
    void shouldDefaultNullRefundedAmountToZeroWhenPreparingCanonicalPayment() {

        final PaymentTransaction base = PaymentTransactionBuilder.mockPaymentTransaction();
        final PaymentTransaction pending = PaymentTransaction.builder()
                .orderNumber(base.getOrderNumber())
                .amount(base.getAmount())
                .refundedAmount(null)
                .method(base.getMethod())
                .status(PaymentStatus.PENDING)
                .gatewayReference(base.getGatewayReference())
                .created(base.getCreated())
                .build();
        final PaymentTransactionEntity current = PaymentTransactionEntityBuilder.mockPaymentTransactionEntity();

        stubNativeInsert();
        doReturn(Optional.of(current)).when(paymentTransactionEntityRepository)
                .findByOrderNumberForUpdate(pending.getOrderNumber());
        doReturn(Optional.of(pending)).when(paymentTransactionPersistenceMapper).mapToDomainObject(current);

        assertEquals(pending, savePaymentTransactionAdapter.prepareCapture(pending));
        verify(nativeQuery).setParameter("refundedAmount", java.math.BigDecimal.ZERO);
    }

    @Test
    void shouldRejectCanonicalCaptureWithDifferentOrderNumber() {

        final PaymentTransaction pending = pendingPayment();
        final PaymentTransactionEntity current = PaymentTransactionEntityBuilder.mockPaymentTransactionEntity();
        current.setOrderNumber("OTHER-ORDER");

        stubNativeInsert();
        doReturn(Optional.of(current)).when(paymentTransactionEntityRepository)
                .findByOrderNumberForUpdate(pending.getOrderNumber());

        assertThrows(PaymentOperationConflictException.class, () -> savePaymentTransactionAdapter.prepareCapture(pending));
    }

    @Test
    void shouldRejectCanonicalCaptureWithDifferentMethod() {

        final PaymentTransaction pending = pendingPayment();
        final PaymentTransactionEntity current = PaymentTransactionEntityBuilder.mockPaymentTransactionEntity();
        current.setMethod(
                current.getMethod() == com.cp.ecommerce.domain.order.PaymentMethod.CARD
                        ? com.cp.ecommerce.domain.order.PaymentMethod.PAYPAL
                        : com.cp.ecommerce.domain.order.PaymentMethod.CARD);

        stubNativeInsert();
        doReturn(Optional.of(current)).when(paymentTransactionEntityRepository)
                .findByOrderNumberForUpdate(pending.getOrderNumber());

        assertThrows(PaymentOperationConflictException.class, () -> savePaymentTransactionAdapter.prepareCapture(pending));
    }

    @Test
    void shouldFailWhenCanonicalPreparedPaymentCannotBeMapped() {

        final PaymentTransaction pending = pendingPayment();
        final PaymentTransactionEntity current = PaymentTransactionEntityBuilder.mockPaymentTransactionEntity();

        current.setOrderNumber(pending.getOrderNumber());
        current.setAmount(pending.getAmount());
        current.setMethod(pending.getMethod());

        stubNativeInsert();
        doReturn(Optional.of(current)).when(paymentTransactionEntityRepository)
                .findByOrderNumberForUpdate(pending.getOrderNumber());
        doReturn(Optional.empty()).when(paymentTransactionPersistenceMapper).mapToDomainObject(current);

        assertThrows(IllegalStateException.class, () -> savePaymentTransactionAdapter.prepareCapture(pending));
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

    private static PaymentTransaction pendingPayment() {

        final PaymentTransaction base = PaymentTransactionBuilder.mockPaymentTransaction();
        return PaymentTransaction.builder()
                .orderNumber(base.getOrderNumber())
                .amount(base.getAmount())
                .refundedAmount(base.getRefundedAmount())
                .method(base.getMethod())
                .status(PaymentStatus.PENDING)
                .gatewayReference(base.getGatewayReference())
                .created(base.getCreated())
                .build();
    }

    private void stubNativeInsert() {

        doReturn(nativeQuery).when(entityManager).createNativeQuery(org.mockito.ArgumentMatchers.anyString());
        doReturn(nativeQuery).when(nativeQuery)
                .setParameter(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        doReturn(1).when(nativeQuery).executeUpdate();
    }

}
