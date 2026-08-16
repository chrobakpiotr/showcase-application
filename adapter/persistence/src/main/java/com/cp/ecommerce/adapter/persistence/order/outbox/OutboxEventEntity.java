package com.cp.ecommerce.adapter.persistence.order.outbox;

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
 * Representation of outbox event in database.
 */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "OUTBOX_EVENT")
public class OutboxEventEntity {

    private static final String SEQUENCE_GENERATOR_NAME = "outboxEventSequenceGenerator";
    private static final String SEQUENCE_NAME = "SEQ_OUTBOX_EVENT";

    @Id
    @Column(name = "ID", length = 13, nullable = false)
    @SequenceGenerator(name = SEQUENCE_GENERATOR_NAME, sequenceName = SEQUENCE_NAME, allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = SEQUENCE_GENERATOR_NAME)
    private Long id;

    @Column(name = "ORDER_NUMBER", length = 40, nullable = false)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private OutboxEventStatus status;

    @Column(name = "CREATED_DATE", nullable = false)
    private Date createdDate;

    @Column(name = "SENT_DATE")
    private Date sentDate;

    @Column(name = "COMPENSATED_DATE")
    private Date compensatedDate;

    @Column(name = "ATTEMPTS", nullable = false)
    private int attempts;

    @Column(name = "LAST_ERROR", length = 500)
    private String lastError;

}
