package com.cp.ecommerce.adapter.persistence.order.dispatch;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface OrderPlacementDispatchEntityRepository extends JpaRepository<OrderPlacementDispatchEntity, String> {

    @Query("""
            select dispatch.dispatchId from OrderPlacementDispatchEntity dispatch
            where dispatch.status in :statuses and dispatch.nextAttemptDate <= :now
            order by dispatch.nextAttemptDate asc, dispatch.dispatchId asc
            """)
    List<String> findDueDispatchIds(
            @Param("statuses") Collection<OrderPlacementDispatchStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select dispatch from OrderPlacementDispatchEntity dispatch where dispatch.dispatchId = :dispatchId")
    Optional<OrderPlacementDispatchEntity> findByIdForUpdate(@Param("dispatchId") String dispatchId);

    List<OrderPlacementDispatchEntity> findAllByOrderNumberOrderByDispatchIdAsc(String orderNumber);

    @Query(
            value = """
                    select dispatch.dispatchId as dispatchId, dispatch.orderNumber as orderNumber,
                           dispatch.dispatchType as dispatchType, dispatch.attempts as attempts,
                           dispatch.createdDate as createdAt,
                           case when dispatch.lastError = 'ORDER_MISSING' then 'ORDER_MISSING'
                                when dispatch.lastError = 'ATTEMPT_BUDGET_EXHAUSTED' then 'ATTEMPT_BUDGET_EXHAUSTED'
                                else 'OTHER' end as reasonCode
                    from OrderPlacementDispatchEntity dispatch where dispatch.status = :status
                    order by dispatch.createdDate asc, dispatch.dispatchId asc
                    """,
            countQuery = "select count(dispatch) from OrderPlacementDispatchEntity dispatch where dispatch.status = :status")
    Page<ParkedDispatchProjection> findParkedProjection(
            @Param("status") OrderPlacementDispatchStatus status,
            Pageable pageable);

    @Query("select min(dispatch.createdDate) from OrderPlacementDispatchEntity dispatch where dispatch.status = :status")
    Optional<Instant> findOldestCreatedDate(@Param("status") OrderPlacementDispatchStatus status);

}
