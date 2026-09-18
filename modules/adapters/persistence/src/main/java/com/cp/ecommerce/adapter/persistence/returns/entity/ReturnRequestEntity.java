package com.cp.ecommerce.adapter.persistence.returns.entity;

import java.math.BigDecimal;
import java.util.Date;

import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representation of {@link ReturnRequest} in database.
 */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "RETURN_REQUEST")
public class ReturnRequestEntity {

    @Id
    @Column(name = "RETURN_NUMBER", length = 43, nullable = false)
    private String returnNumber;

    @Column(name = "ORDER_NUMBER", length = 40, nullable = false)
    private String orderNumber;

    @Column(name = "SKU", length = 40, nullable = false)
    private String sku;

    @Column(name = "QUANTITY", nullable = false)
    private int quantity;

    @Column(name = "REASON", length = 2000, nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private ReturnStatus status;

    @Column(name = "REQUESTED_DATE", nullable = false)
    private Date requestedDate;

    @Column(name = "DECIDED_DATE")
    private Date decidedDate;

    @Column(name = "REFUND_AMOUNT", nullable = false)
    private BigDecimal refundAmount;

    @Version
    @Column(name = "VERSION", nullable = false)
    private long version;

}
