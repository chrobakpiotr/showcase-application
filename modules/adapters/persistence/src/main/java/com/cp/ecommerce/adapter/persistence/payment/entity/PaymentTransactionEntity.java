package com.cp.ecommerce.adapter.persistence.payment.entity;

import java.math.BigDecimal;
import java.util.Date;

import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;

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
 * Representation of {@link PaymentTransaction} in database.
 *
 * <p>
 * Uses the order number directly as its primary key rather than a surrogate id - there is exactly one payment transaction row
 * per order, so a separate technical id would only add an unused indirection (mirrors {@code StockLevelEntity}'s use of SKU as
 * its own primary key, see ADR 0026).
 */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "PAYMENT_TRANSACTION")
public class PaymentTransactionEntity {

    @Id
    @Column(name = "ORDER_NUMBER", length = 40, nullable = false)
    private String orderNumber;

    @Column(name = "AMOUNT", nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "METHOD", length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private PaymentStatus status;

    @Column(name = "GATEWAY_REFERENCE", length = 80)
    private String gatewayReference;

    @Column(name = "CREATION_DATE")
    private Date created;

}
