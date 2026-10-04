package com.cp.ecommerce.adapter.persistence.order.dispatch;

import java.time.Instant;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.order.idempotency.IdempotencyLockRepository;
import com.cp.ecommerce.domain.order.dispatch.DispatchRedriveCommand;
import com.cp.ecommerce.domain.order.dispatch.DispatchRedriveOutcome;
import com.cp.ecommerce.domain.order.dispatch.port.outgoing.RedriveDispatchOutPort;
import com.cp.ecommerce.foundation.exception.ApplicationNotFoundException;
import com.cp.ecommerce.foundation.exception.OrderPlacementDispatchRedriveConflictException;

import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@PersistenceAdapter
@RequiredArgsConstructor
class RedriveDispatchAdapter implements RedriveDispatchOutPort {

    private static final int LOCK_STRIPES = 64;
    private final IdempotencyLockRepository locks;
    private final DispatchRedriveAuditRepository audits;
    private final OrderPlacementDispatchEntityRepository dispatches;

    @Override
    @Transactional
    public DispatchRedriveOutcome redrive(final DispatchRedriveCommand command, final Instant now) {
        locks.findById(Math.floorMod(command.commandId().hashCode(), LOCK_STRIPES))
                .orElseThrow(() -> new IllegalStateException("Missing idempotency lock stripe for dispatch redrive"));
        final var existing = audits.findById(command.commandId()).orElse(null);
        if (existing != null) {
            if (!existing.getDispatchId().equals(command.dispatchId()) || !existing.getActor().equals(command.actor())
                    || !existing.getReason().equals(command.reason())) {
                throw new OrderPlacementDispatchRedriveConflictException(
                        "Redrive command ID is bound to different command data");
            }
            return DispatchRedriveOutcome.REPLAYED;
        }
        final var dispatch = dispatches.findByIdForUpdate(command.dispatchId())
                .orElseThrow(() -> new ApplicationNotFoundException("Placement dispatch not found: " + command.dispatchId()));
        if (dispatch.getStatus() != OrderPlacementDispatchStatus.PARKED
                || !"ATTEMPT_BUDGET_EXHAUSTED".equals(dispatch.getLastError()) || dispatch.getClaimId() != null
                || dispatch.getClaimUntil() != null) {
            throw new OrderPlacementDispatchRedriveConflictException("Dispatch is not eligible for budget-exhausted redrive");
        }
        audits.saveAndFlush(
                DispatchRedriveAuditEntity.builder()
                        .commandId(command.commandId())
                        .dispatchId(dispatch.getDispatchId())
                        .orderNumber(dispatch.getOrderNumber())
                        .dispatchType(dispatch.getDispatchType().name())
                        .actor(command.actor())
                        .reason(command.reason())
                        .previousAttempts(dispatch.getAttempts())
                        .previousReason("ATTEMPT_BUDGET_EXHAUSTED")
                        .originalCreatedAt(dispatch.getCreatedDate())
                        .createdAt(now)
                        .build());
        dispatch.setStatus(OrderPlacementDispatchStatus.PENDING);
        dispatch.setAttempts(0);
        dispatch.setNextAttemptDate(now);
        dispatch.setClaimId(null);
        dispatch.setClaimUntil(null);
        dispatch.setLastError(null);
        dispatch.setSentDate(null);
        dispatches.saveAndFlush(dispatch);
        return DispatchRedriveOutcome.REQUEUED;
    }
}
