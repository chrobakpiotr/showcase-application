package com.cp.ecommerce.adapter.persistence.payment;

import java.math.BigDecimal;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentRefundEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentRefundEntityRepository;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntityRepository;
import com.cp.ecommerce.adapter.persistence.payment.mapper.PaymentTransactionPersistenceMapper;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.payment.PaymentRefundClaim;
import com.cp.ecommerce.domain.payment.PaymentRefundOutcome;
import com.cp.ecommerce.domain.payment.PaymentRefundStatus;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.foundation.exception.PaymentRefundConflictException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManagePaymentRefundAdapterTest {

    private static final String ORDER_NUMBER = "ORDER-1";
    private static final String REFUND_ID = "RETURN-1";
    private static final String OTHER_ORDER = "ORDER-2";
    private static final String GATEWAY_REFERENCE = "gw-1";
    private static final BigDecimal CAPTURED = new BigDecimal("100.00");
    private static final BigDecimal PARTIAL = new BigDecimal("30.00");

    @Mock
    private transient PaymentTransactionEntityRepository paymentRepository;

    @Mock
    private transient PaymentRefundEntityRepository refundRepository;

    private transient ManagePaymentRefundAdapter adapter;

    @BeforeEach
    void setUp() {

        adapter = new ManagePaymentRefundAdapter(
                paymentRepository,
                refundRepository,
                new PaymentTransactionPersistenceMapper());
    }

    @Test
    void shouldReservePartialRefund() {

        final PaymentTransactionEntity payment = payment(PaymentStatus.CAPTURED, BigDecimal.ZERO);
        given(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(payment));
        given(refundRepository.findById(REFUND_ID)).willReturn(Optional.empty());
        given(refundRepository.sumAmountByOrderNumberAndStatus(ORDER_NUMBER, PaymentRefundStatus.PENDING)).willReturn(null);

        final PaymentRefundClaim claim = adapter.reserve(REFUND_ID, ORDER_NUMBER, PARTIAL);

        assertThat(claim.outcome()).isEqualTo(PaymentRefundOutcome.RESERVED);
        assertThat(claim.amount()).isEqualByComparingTo(PARTIAL);
        assertThat(claim.gatewayReference()).isEqualTo(GATEWAY_REFERENCE);
        verify(refundRepository).save(any(PaymentRefundEntity.class));
    }

    @Test
    void shouldReserveOnlyRemainingAmountForWholeOrderRefund() {

        final PaymentTransactionEntity payment = payment(PaymentStatus.PARTIALLY_REFUNDED, PARTIAL);
        given(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(payment));
        given(refundRepository.findById(REFUND_ID)).willReturn(Optional.empty());
        given(refundRepository.sumAmountByOrderNumberAndStatus(ORDER_NUMBER, PaymentRefundStatus.PENDING))
                .willReturn(new BigDecimal("10.00"));

        final PaymentRefundClaim claim = adapter.reserveRemaining(REFUND_ID, ORDER_NUMBER);

        assertThat(claim.amount()).isEqualByComparingTo(new BigDecimal("60.00"));
    }

    @Test
    void shouldReturnNothingForMissingPaymentWhenRefundingRemaining() {

        given(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.empty());

        assertThat(adapter.reserveRemaining(REFUND_ID, ORDER_NUMBER).outcome())
                .isEqualTo(PaymentRefundOutcome.NOTHING_TO_REFUND);
    }

    @Test
    void shouldRejectPartialRefundForMissingPayment() {

        given(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.reserve(REFUND_ID, ORDER_NUMBER, PARTIAL))
                .isInstanceOf(PaymentRefundConflictException.class);
    }

    @Test
    void shouldReturnCompletedExistingRefund() {

        final PaymentTransactionEntity payment = payment(PaymentStatus.PARTIALLY_REFUNDED, PARTIAL);
        given(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(payment));
        given(refundRepository.findById(REFUND_ID))
                .willReturn(Optional.of(refund(ORDER_NUMBER, PARTIAL, PaymentRefundStatus.COMPLETED)));

        assertThat(adapter.reserve(REFUND_ID, ORDER_NUMBER, PARTIAL).outcome()).isEqualTo(PaymentRefundOutcome.COMPLETED);
    }

    @Test
    void shouldReturnRetryForPendingExistingRefund() {

        final PaymentTransactionEntity payment = payment(PaymentStatus.CAPTURED, BigDecimal.ZERO);
        given(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(payment));
        given(refundRepository.findById(REFUND_ID))
                .willReturn(Optional.of(refund(ORDER_NUMBER, PARTIAL, PaymentRefundStatus.PENDING)));

        assertThat(adapter.reserve(REFUND_ID, ORDER_NUMBER, PARTIAL).outcome()).isEqualTo(PaymentRefundOutcome.RETRY);
    }

    @Test
    void shouldRejectExistingIdentityForAnotherOrder() {

        final PaymentTransactionEntity payment = payment(PaymentStatus.CAPTURED, BigDecimal.ZERO);
        given(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(payment));
        given(refundRepository.findById(REFUND_ID))
                .willReturn(Optional.of(refund(OTHER_ORDER, PARTIAL, PaymentRefundStatus.PENDING)));

        assertThatThrownBy(() -> adapter.reserve(REFUND_ID, ORDER_NUMBER, PARTIAL))
                .isInstanceOf(PaymentRefundConflictException.class);
    }

    @Test
    void shouldRejectExistingIdentityForAnotherAmount() {

        final PaymentTransactionEntity payment = payment(PaymentStatus.CAPTURED, BigDecimal.ZERO);
        given(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(payment));
        given(refundRepository.findById(REFUND_ID))
                .willReturn(Optional.of(refund(ORDER_NUMBER, BigDecimal.TEN, PaymentRefundStatus.PENDING)));

        assertThatThrownBy(() -> adapter.reserve(REFUND_ID, ORDER_NUMBER, PARTIAL))
                .isInstanceOf(PaymentRefundConflictException.class);
    }

    @Test
    void shouldReuseExistingRemainingRefundAmountOnRetry() {

        final PaymentTransactionEntity payment = payment(PaymentStatus.PARTIALLY_REFUNDED, PARTIAL);
        given(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(payment));
        given(refundRepository.findById(REFUND_ID))
                .willReturn(Optional.of(refund(ORDER_NUMBER, new BigDecimal("70.00"), PaymentRefundStatus.PENDING)));

        assertThat(adapter.reserveRemaining(REFUND_ID, ORDER_NUMBER).amount()).isEqualByComparingTo(new BigDecimal("70.00"));
    }

    @Test
    void shouldRejectPartialRefundForNonRefundableStatus() {

        final PaymentTransactionEntity payment = payment(PaymentStatus.DECLINED, BigDecimal.ZERO);
        given(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(payment));
        given(refundRepository.findById(REFUND_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.reserve(REFUND_ID, ORDER_NUMBER, PARTIAL))
                .isInstanceOf(PaymentRefundConflictException.class);
    }

    @Test
    void shouldNoOpRemainingRefundForNonRefundableStatus() {

        final PaymentTransactionEntity payment = payment(PaymentStatus.REFUNDED, CAPTURED);
        given(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(payment));
        given(refundRepository.findById(REFUND_ID)).willReturn(Optional.empty());

        assertThat(adapter.reserveRemaining(REFUND_ID, ORDER_NUMBER).outcome())
                .isEqualTo(PaymentRefundOutcome.NOTHING_TO_REFUND);
    }

    @Test
    void shouldRejectZeroPartialRefund() {

        final PaymentTransactionEntity payment = payment(PaymentStatus.CAPTURED, BigDecimal.ZERO);
        given(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(payment));
        given(refundRepository.findById(REFUND_ID)).willReturn(Optional.empty());
        given(refundRepository.sumAmountByOrderNumberAndStatus(ORDER_NUMBER, PaymentRefundStatus.PENDING))
                .willReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> adapter.reserve(REFUND_ID, ORDER_NUMBER, BigDecimal.ZERO))
                .isInstanceOf(PaymentRefundConflictException.class);
    }

    @Test
    void shouldReturnNothingWhenNoRemainingAmountExists() {

        final PaymentTransactionEntity payment = payment(PaymentStatus.PARTIALLY_REFUNDED, new BigDecimal("90.00"));
        given(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(payment));
        given(refundRepository.findById(REFUND_ID)).willReturn(Optional.empty());
        given(refundRepository.sumAmountByOrderNumberAndStatus(ORDER_NUMBER, PaymentRefundStatus.PENDING))
                .willReturn(BigDecimal.TEN);

        assertThat(adapter.reserveRemaining(REFUND_ID, ORDER_NUMBER).outcome())
                .isEqualTo(PaymentRefundOutcome.NOTHING_TO_REFUND);
    }

    @Test
    void shouldRejectOverRefundIncludingPendingClaims() {

        final PaymentTransactionEntity payment = payment(PaymentStatus.CAPTURED, BigDecimal.ZERO);
        given(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(payment));
        given(refundRepository.findById(REFUND_ID)).willReturn(Optional.empty());
        given(refundRepository.sumAmountByOrderNumberAndStatus(ORDER_NUMBER, PaymentRefundStatus.PENDING))
                .willReturn(new BigDecimal("80.00"));

        assertThatThrownBy(() -> adapter.reserve(REFUND_ID, ORDER_NUMBER, PARTIAL))
                .isInstanceOf(PaymentRefundConflictException.class);
    }

    @Test
    void shouldCompletePartialRefund() {

        final PaymentTransactionEntity payment = payment(PaymentStatus.CAPTURED, BigDecimal.ZERO);
        final PaymentRefundEntity refund = refund(ORDER_NUMBER, PARTIAL, PaymentRefundStatus.PENDING);
        given(refundRepository.findOrderNumberByRefundId(REFUND_ID)).willReturn(Optional.of(ORDER_NUMBER));
        given(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(payment));
        given(refundRepository.findByIdForUpdate(REFUND_ID)).willReturn(Optional.of(refund));
        given(paymentRepository.saveAndFlush(payment)).willReturn(payment);

        final PaymentTransaction result = adapter.complete(REFUND_ID);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(result.getRefundedAmount()).isEqualByComparingTo(PARTIAL);
        assertThat(refund.getStatus()).isEqualTo(PaymentRefundStatus.COMPLETED);
    }

    @Test
    void shouldCompleteFinalRefund() {

        final PaymentTransactionEntity payment = payment(PaymentStatus.PARTIALLY_REFUNDED, new BigDecimal("70.00"));
        final PaymentRefundEntity refund = refund(ORDER_NUMBER, PARTIAL, PaymentRefundStatus.PENDING);
        given(refundRepository.findOrderNumberByRefundId(REFUND_ID)).willReturn(Optional.of(ORDER_NUMBER));
        given(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(payment));
        given(refundRepository.findByIdForUpdate(REFUND_ID)).willReturn(Optional.of(refund));
        given(paymentRepository.saveAndFlush(payment)).willReturn(payment);

        assertThat(adapter.complete(REFUND_ID).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void shouldNotCompleteSameRefundTwice() {

        final PaymentTransactionEntity payment = payment(PaymentStatus.PARTIALLY_REFUNDED, PARTIAL);
        final PaymentRefundEntity refund = refund(ORDER_NUMBER, PARTIAL, PaymentRefundStatus.COMPLETED);
        given(refundRepository.findOrderNumberByRefundId(REFUND_ID)).willReturn(Optional.of(ORDER_NUMBER));
        given(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(payment));
        given(refundRepository.findByIdForUpdate(REFUND_ID)).willReturn(Optional.of(refund));

        assertThat(adapter.complete(REFUND_ID).getRefundedAmount()).isEqualByComparingTo(PARTIAL);
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectCorruptCompletionThatWouldOverRefund() {

        final PaymentTransactionEntity payment = payment(PaymentStatus.PARTIALLY_REFUNDED, new BigDecimal("90.00"));
        final PaymentRefundEntity refund = refund(ORDER_NUMBER, PARTIAL, PaymentRefundStatus.PENDING);
        given(refundRepository.findOrderNumberByRefundId(REFUND_ID)).willReturn(Optional.of(ORDER_NUMBER));
        given(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(payment));
        given(refundRepository.findByIdForUpdate(REFUND_ID)).willReturn(Optional.of(refund));

        assertThatThrownBy(() -> adapter.complete(REFUND_ID)).isInstanceOf(PaymentRefundConflictException.class);
    }

    @Test
    void shouldFailWhenLockedPaymentCannotBeMappedToDomain() {

        final PaymentTransactionEntity payment = payment(PaymentStatus.CAPTURED, BigDecimal.ZERO);
        final PaymentTransactionPersistenceMapper failingMapper = mock(PaymentTransactionPersistenceMapper.class);
        final ManagePaymentRefundAdapter adapterWithFailingMapper = new ManagePaymentRefundAdapter(
                paymentRepository,
                refundRepository,
                failingMapper);
        given(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(payment));
        given(refundRepository.findById(REFUND_ID)).willReturn(Optional.empty());
        given(refundRepository.sumAmountByOrderNumberAndStatus(ORDER_NUMBER, PaymentRefundStatus.PENDING))
                .willReturn(BigDecimal.ZERO);
        given(failingMapper.mapToDomainObject(payment)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adapterWithFailingMapper.reserve(REFUND_ID, ORDER_NUMBER, PARTIAL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ORDER_NUMBER);
    }

    @Test
    void shouldRejectNullPartialRefundAmount() {

        final PaymentTransactionEntity payment = payment(PaymentStatus.CAPTURED, BigDecimal.ZERO);
        given(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(payment));
        given(refundRepository.findById(REFUND_ID)).willReturn(Optional.empty());
        given(refundRepository.sumAmountByOrderNumberAndStatus(ORDER_NUMBER, PaymentRefundStatus.PENDING))
                .willReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> adapter.reserve(REFUND_ID, ORDER_NUMBER, null))
                .isInstanceOf(PaymentRefundConflictException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void shouldFailCompletionForUnknownRefund() {

        given(refundRepository.findOrderNumberByRefundId(REFUND_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.complete(REFUND_ID)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldFailCompletionWhenPaymentDisappears() {

        given(refundRepository.findOrderNumberByRefundId(REFUND_ID)).willReturn(Optional.of(ORDER_NUMBER));
        given(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.complete(REFUND_ID)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldFailCompletionWhenRefundDisappears() {

        given(refundRepository.findOrderNumberByRefundId(REFUND_ID)).willReturn(Optional.of(ORDER_NUMBER));
        given(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER))
                .willReturn(Optional.of(payment(PaymentStatus.CAPTURED, BigDecimal.ZERO)));
        given(refundRepository.findByIdForUpdate(REFUND_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.complete(REFUND_ID)).isInstanceOf(IllegalStateException.class);
    }

    private static PaymentTransactionEntity payment(final PaymentStatus status, final BigDecimal refundedAmount) {

        return PaymentTransactionEntity.builder()
                .orderNumber(ORDER_NUMBER)
                .amount(CAPTURED)
                .refundedAmount(refundedAmount)
                .method(PaymentMethod.CARD)
                .status(status)
                .gatewayReference(GATEWAY_REFERENCE)
                .build();
    }

    private static PaymentRefundEntity refund(
            final String orderNumber,
            final BigDecimal amount,
            final PaymentRefundStatus status) {

        return PaymentRefundEntity.builder().refundId(REFUND_ID).orderNumber(orderNumber).amount(amount).status(status).build();
    }
}
