package com.cp.ecommerce.adapter.persistence.payment.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.cp.ecommerce.domain.payment.RefundReturnContinuationStatus;

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

/** Durable, self-contained refund-to-RMA continuation intent. */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "PAYMENT_REFUND_RETURN_CONTINUATION")
public class RefundReturnContinuationEntity {

    @Id
    @Column(name = "REFUND_ID", length = 80, nullable = false)
    private String refundId;

    @Column(name = "RETURN_NUMBER", length = 43, nullable = false, unique = true)
    private String returnNumber;

    @Column(name = "ORDER_NUMBER", length = 40, nullable = false)
    private String orderNumber;

    @Column(name = "REFUND_AMOUNT", precision = 19, scale = 2, nullable = false)
    private BigDecimal refundAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private RefundReturnContinuationStatus status;

    @Column(name = "ATTEMPTS", nullable = false)
    private int attempts;

    @Column(name = "NEXT_ATTEMPT_DATE", nullable = false)
    private Instant nextAttemptDate;

    @Column(name = "LAST_ERROR", length = 1000)
    private String lastError;

    @Column(name = "CREATION_DATE", nullable = false)
    private Instant created;

    @Column(name = "COMPLETION_DATE")
    private Instant completed;
}
