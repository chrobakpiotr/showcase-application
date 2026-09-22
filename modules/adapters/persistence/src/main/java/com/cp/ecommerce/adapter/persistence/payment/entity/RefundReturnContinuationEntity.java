package com.cp.ecommerce.adapter.persistence.payment.entity;

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

/** Durable link from one payment refund operation to one RMA continuation. */
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

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private RefundReturnContinuationStatus status;

    @Column(name = "CREATION_DATE", nullable = false)
    private Instant created;

    @Column(name = "COMPLETION_DATE")
    private Instant completed;
}
