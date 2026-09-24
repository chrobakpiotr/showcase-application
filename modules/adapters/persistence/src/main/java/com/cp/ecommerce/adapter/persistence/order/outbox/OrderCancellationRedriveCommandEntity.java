package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.time.Instant;

import com.cp.ecommerce.domain.order.OrderCancellationRedriveOutcome;

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

/** Durable operator audit row for a cancellation manual-review redrive command. */
@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "ORDER_CANCELLATION_REDRIVE_COMMAND")
public class OrderCancellationRedriveCommandEntity {

    @Id
    @Column(name = "COMMAND_ID", length = 80, nullable = false)
    private String commandId;

    @Column(name = "ORDER_NUMBER", length = 40, nullable = false)
    private String orderNumber;

    @Column(name = "ACTOR", length = 120, nullable = false)
    private String actor;

    @Column(name = "REASON", length = 500, nullable = false)
    private String reason;

    @Column(name = "PREVIOUS_ERROR", length = 500)
    private String previousError;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private OrderCancellationRedriveOutcome status;

    @Column(name = "CREATION_DATE", nullable = false)
    private Instant createdDate;
}
