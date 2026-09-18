package com.cp.ecommerce.adapter.persistence.payment.entity;

import java.math.BigDecimal;
import java.util.Date;

import com.cp.ecommerce.domain.payment.PaymentRefundStatus;

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
 * Durable payment-refund operation.
 */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "PAYMENT_REFUND")
public class PaymentRefundEntity {

    @Id
    @Column(name = "REFUND_ID", length = 80, nullable = false)
    private String refundId;

    @Column(name = "ORDER_NUMBER", length = 40, nullable = false)
    private String orderNumber;

    @Column(name = "AMOUNT", nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private PaymentRefundStatus status;

    @Column(name = "CREATION_DATE", nullable = false)
    private Date created;

    @Column(name = "COMPLETION_DATE")
    private Date completed;
}
