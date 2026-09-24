package com.cp.ecommerce.adapter.persistence.order.dispatch;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.RouteOrderNotificationInPort;
import com.cp.ecommerce.domain.order.port.incoming.SendOrderConfirmationEmailInPort;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ConditionalOnProperty(
        prefix = "order-placement.dispatch",
        name = "manager-enabled",
        havingValue = "true",
        matchIfMissing = true)
@Component
@RequiredArgsConstructor
public class OrderPlacementDispatchManager {

    static final String EMAIL_PREFIX = "ORDER-CONFIRMATION:";
    static final String CAMEL_PREFIX = "ORDER-CAMEL:";
    private static final int BATCH_SIZE = 50;
    private static final int LAST_ERROR_MAX_LENGTH = 500;
    private static final List<OrderPlacementDispatchStatus> RETRYABLE_STATUSES = List.of(
            OrderPlacementDispatchStatus.PENDING,
            OrderPlacementDispatchStatus.FAILED,
            OrderPlacementDispatchStatus.DELIVERING);

    private final OrderPlacementDispatchEntityRepository repository;
    private final EntityManager entityManager;
    private final ManageOrderInPort manageOrderInPort;
    private final SendOrderConfirmationEmailInPort sendOrderConfirmationEmailInPort;
    private final RouteOrderNotificationInPort routeOrderNotificationInPort;
    private final TransactionOperations transactionOperations;
    private final Clock clock;

    @Value("${order-placement.dispatch.lease-ms:30000}")
    long leaseMillis = 30_000L;
    @Value("${order-placement.dispatch.retry-delay-ms:5000}")
    long retryDelayMillis = 5_000L;

    public void enqueue(final Order order) {
        final Instant now = now();
        transactionOperations.executeWithoutResult(status -> {
            insertOnce(order.getOrderNumber(), OrderPlacementDispatchType.CONFIRMATION_EMAIL, now);
            insertOnce(order.getOrderNumber(), OrderPlacementDispatchType.CAMEL_ROUTING, now);
        });
    }

    public void retryDueDispatches() {
        final Instant now = now();
        repository.findDueDispatchIds(RETRYABLE_STATUSES, now, PageRequest.of(0, BATCH_SIZE)).forEach(this::deliverDueDispatch);
    }

    void deliverDueDispatch(final String dispatchId) {
        final DispatchClaim claim = claimDispatch(dispatchId, now());
        if (claim == null) {
            return;
        }
        try {
            final Order order = manageOrderInPort.findOrder(claim.orderNumber());
            if (claim.dispatchType() == OrderPlacementDispatchType.CONFIRMATION_EMAIL) {
                sendOrderConfirmationEmailInPort.sendConfirmationEmail(order);
            } else {
                routeOrderNotificationInPort.routeNotification(order);
            }
            markSent(claim, now());
        } catch (final RuntimeException exception) { // SUPPRESS CHECKSTYLE IllegalCatch
            log.warn(
                    "Could not deliver durable placement dispatch: dispatchId={}, orderNumber={}, type={}",
                    claim.dispatchId(),
                    claim.orderNumber(),
                    claim.dispatchType(),
                    exception);
            markFailed(claim, exception.getMessage(), now());
        }
    }

    DispatchClaim claimDispatch(final String dispatchId, final Instant now) {
        return transactionOperations.execute(
                status -> repository.findByIdForUpdate(dispatchId)
                        .filter(dispatch -> dispatch.getStatus() != OrderPlacementDispatchStatus.SENT)
                        .filter(dispatch -> !dispatch.getNextAttemptDate().isAfter(now))
                        .map(dispatch -> {
                            final String claimId = UUID.randomUUID().toString();
                            dispatch.setStatus(OrderPlacementDispatchStatus.DELIVERING);
                            dispatch.setClaimId(claimId);
                            dispatch.setClaimUntil(Instant.ofEpochMilli(now.toEpochMilli() + leaseMillis));
                            dispatch.setAttempts(dispatch.getAttempts() + 1);
                            dispatch.setNextAttemptDate(dispatch.getClaimUntil());
                            repository.saveAndFlush(dispatch);
                            return new DispatchClaim(
                                    dispatch.getDispatchId(),
                                    dispatch.getOrderNumber(),
                                    dispatch.getDispatchType(),
                                    claimId);
                        })
                        .orElse(null));
    }

    void markSent(final DispatchClaim claim, final Instant sentAt) {
        transactionOperations.executeWithoutResult(
                status -> repository.findByIdForUpdate(claim.dispatchId())
                        .filter(dispatch -> ownsClaim(dispatch, claim))
                        .ifPresent(dispatch -> {
                            dispatch.setStatus(OrderPlacementDispatchStatus.SENT);
                            dispatch.setSentDate(sentAt);
                            dispatch.setLastError(null);
                            dispatch.setClaimId(null);
                            dispatch.setClaimUntil(null);
                            dispatch.setNextAttemptDate(sentAt);
                            repository.saveAndFlush(dispatch);
                        }));
    }

    void markFailed(final DispatchClaim claim, final String error, final Instant failedAt) {
        transactionOperations.executeWithoutResult(
                status -> repository.findByIdForUpdate(claim.dispatchId())
                        .filter(dispatch -> ownsClaim(dispatch, claim))
                        .ifPresent(dispatch -> {
                            final String message = String.valueOf(error);
                            dispatch.setStatus(OrderPlacementDispatchStatus.FAILED);
                            dispatch.setClaimId(null);
                            dispatch.setClaimUntil(null);
                            dispatch.setLastError(message.substring(0, Math.min(message.length(), LAST_ERROR_MAX_LENGTH)));
                            dispatch.setNextAttemptDate(Instant.ofEpochMilli(failedAt.toEpochMilli() + retryDelayMillis));
                            repository.saveAndFlush(dispatch);
                        }));
    }

    static String dispatchId(final String orderNumber, final OrderPlacementDispatchType type) {
        return switch (type) {
        case CONFIRMATION_EMAIL -> EMAIL_PREFIX + orderNumber;
        case CAMEL_ROUTING -> CAMEL_PREFIX + orderNumber;
        };
    }

    private void insertOnce(final String orderNumber, final OrderPlacementDispatchType type, final Instant createdAt) {
        entityManager.createNativeQuery("""
                insert into test_db.ORDER_PLACEMENT_DISPATCH (
                    DISPATCH_ID, ORDER_NUMBER, DISPATCH_TYPE, STATUS, CREATED_DATE, SENT_DATE,
                    ATTEMPTS, NEXT_ATTEMPT_DATE, LAST_ERROR, CLAIM_ID, CLAIM_UNTIL
                ) values (
                    :dispatchId, :orderNumber, :dispatchType, :status, :createdDate, null,
                    0, :nextAttemptDate, null, null, null
                ) on conflict do nothing
                """)
                .setParameter("dispatchId", dispatchId(orderNumber, type))
                .setParameter("orderNumber", orderNumber)
                .setParameter("dispatchType", type.name())
                .setParameter("status", OrderPlacementDispatchStatus.PENDING.name())
                .setParameter("createdDate", createdAt)
                .setParameter("nextAttemptDate", createdAt)
                .executeUpdate();
    }

    private boolean ownsClaim(final OrderPlacementDispatchEntity dispatch, final DispatchClaim claim) {
        return dispatch.getStatus() == OrderPlacementDispatchStatus.DELIVERING
                && Objects.equals(dispatch.getClaimId(), claim.claimId());
    }

    private Instant now() {
        return Instant.ofEpochMilli(clock.instant().toEpochMilli());
    }

    record DispatchClaim(String dispatchId, String orderNumber, OrderPlacementDispatchType dispatchType, String claimId) {
    }
}
