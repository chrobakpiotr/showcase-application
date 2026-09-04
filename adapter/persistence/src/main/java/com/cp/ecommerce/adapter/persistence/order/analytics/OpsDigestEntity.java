package com.cp.ecommerce.adapter.persistence.order.analytics;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persisted row for a single AI-generated ops digest (see ADR 0022): one row per generation run, latest read back by
 * {@code GetLatestOpsDigestAdapter}.
 */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "OPS_DIGEST")
public class OpsDigestEntity {

    private static final String SEQUENCE_GENERATOR_NAME = "opsDigestSequenceGenerator";
    private static final String SEQUENCE_NAME = "SEQ_OPS_DIGEST";

    @Id
    @Column(name = "ID", length = 13, nullable = false)
    @SequenceGenerator(name = SEQUENCE_GENERATOR_NAME, sequenceName = SEQUENCE_NAME, allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = SEQUENCE_GENERATOR_NAME)
    private Long id;

    @Column(name = "GENERATED_DATE", nullable = false)
    private Date generatedDate;

    @Column(name = "ORDERS_PLACED_LAST_DAY", nullable = false)
    private long ordersPlacedLastDay;

    @Column(name = "STANDARD_COUNT", nullable = false)
    private long standardCount;

    @Column(name = "URGENT_COUNT", nullable = false)
    private long urgentCount;

    @Column(name = "COMPLAINT_COUNT", nullable = false)
    private long complaintCount;

    @Column(name = "SUSPICIOUS_COUNT", nullable = false)
    private long suspiciousCount;

    @Column(name = "NARRATIVE", length = 2000, nullable = false)
    private String narrative;

}
