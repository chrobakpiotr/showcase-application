package com.cp.ecommerce.adapter.persistence.order.dispatch;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "ORDER_PLACEMENT_DISPATCH_REDRIVE")
public class DispatchRedriveAuditEntity {

    @Id
    @Column(name = "COMMAND_ID", nullable = false, updatable = false, length = 80)
    private String commandId;

    @Column(name = "DISPATCH_ID", nullable = false, updatable = false, length = 100)
    private String dispatchId;

    @Column(name = "ORDER_NUMBER", nullable = false, updatable = false, length = 40)
    private String orderNumber;

    @Column(name = "DISPATCH_TYPE", nullable = false, updatable = false, length = 30)
    private String dispatchType;

    @Column(name = "ACTOR", nullable = false, updatable = false, length = 120)
    private String actor;

    @Column(name = "REASON", nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "PREVIOUS_ATTEMPTS", nullable = false, updatable = false)
    private int previousAttempts;

    @Column(name = "PREVIOUS_REASON", nullable = false, updatable = false, length = 40)
    private String previousReason;

    @Column(name = "ORIGINAL_CREATED_AT", nullable = false, updatable = false)
    private Instant originalCreatedAt;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;
}
