package com.cp.ecommerce.adapter.persistence.order.idempotency;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Persisted record of a client-supplied {@code Idempotency-Key}, used to make {@code POST /api/order} safe to retry.
 */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "IDEMPOTENCY_KEY")
public class IdempotencyKeyEntity {

    private static final String SEQUENCE_GENERATOR_NAME = "idempotencyKeySequenceGenerator";
    private static final String SEQUENCE_NAME = "SEQ_IDEMPOTENCY_KEY";

    @Id
    @Column(name = "ID", length = 13, nullable = false)
    @SequenceGenerator(name = SEQUENCE_GENERATOR_NAME, sequenceName = SEQUENCE_NAME, allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = SEQUENCE_GENERATOR_NAME)
    private Long id;

    @Column(name = "IDEMPOTENCY_KEY", length = 255, nullable = false)
    private String key;

    @Column(name = "FINGERPRINT", length = 64, nullable = false)
    private String fingerprint;

    @Column(name = "ORDER_NUMBER", length = 40)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private IdempotencyKeyStatus status;

    @Column(name = "CREATED_DATE", nullable = false)
    private Date createdDate;

    @Column(name = "COMPLETED_DATE")
    private Date completedDate;

}
