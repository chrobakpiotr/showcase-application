package com.cp.ecommerce.adapter.persistence.payment.entity;

import java.time.Instant;

import com.cp.ecommerce.domain.payment.PaymentProviderOperationType;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Durable provider-operation reconciliation intent.
 */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "PAYMENT_RECONCILIATION_OPERATION")
public class PaymentReconciliationEntity {

    @Id
    @Column(name = "OPERATION_ID", length = 120, nullable = false)
    private String operationId;

    @Column(name = "ORDER_NUMBER", length = 40, nullable = false)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "OPERATION_TYPE", length = 20, nullable = false)
    private PaymentProviderOperationType operationType;

    @Column(name = "REFUND_ID", length = 80)
    private String refundId;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private PaymentReconciliationStatus status;

    @Builder.Default
    @Column(name = "ATTEMPTS", nullable = false)
    private int attempts = 0;

    @Column(name = "NEXT_ATTEMPT_DATE", nullable = false)
    private Instant nextAttemptDate;

    @Column(name = "CLAIM_ID", length = 80)
    private String claimId;

    @Column(name = "CLAIM_UNTIL")
    private Instant claimUntil;

    @Column(name = "LAST_ERROR", length = 1000)
    private String lastError;

    @Column(name = "CREATION_DATE", nullable = false)
    private Instant created;

    @Column(name = "COMPLETION_DATE")
    private Instant completed;
}
