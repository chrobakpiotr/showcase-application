package com.cp.ecommerce.adapter.persistence.order.dispatch;

import java.time.Instant;

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

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "ORDER_PLACEMENT_DISPATCH")
public class OrderPlacementDispatchEntity {

    @Id
    @Column(name = "DISPATCH_ID", length = 100, nullable = false)
    private String dispatchId;
    @Column(name = "ORDER_NUMBER", length = 40, nullable = false)
    private String orderNumber;
    @Enumerated(EnumType.STRING)
    @Column(name = "DISPATCH_TYPE", length = 30, nullable = false)
    private OrderPlacementDispatchType dispatchType;
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private OrderPlacementDispatchStatus status;
    @Column(name = "CREATED_DATE", nullable = false)
    private Instant createdDate;
    @Column(name = "SENT_DATE")
    private Instant sentDate;
    @Column(name = "ATTEMPTS", nullable = false)
    private int attempts;
    @Column(name = "NEXT_ATTEMPT_DATE", nullable = false)
    private Instant nextAttemptDate;
    @Column(name = "LAST_ERROR", length = 500)
    private String lastError;
    @Column(name = "CLAIM_ID", length = 36)
    private String claimId;
    @Column(name = "CLAIM_UNTIL")
    private Instant claimUntil;
}
