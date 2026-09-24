package com.cp.ecommerce.adapter.persistence.order.dispatch;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
            order by dispatch.createdDate asc, dispatch.dispatchId asc
            """)
    List<String> findDueDispatchIds(
            @Param("statuses") Collection<OrderPlacementDispatchStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select dispatch from OrderPlacementDispatchEntity dispatch where dispatch.dispatchId = :dispatchId")
    Optional<OrderPlacementDispatchEntity> findByIdForUpdate(@Param("dispatchId") String dispatchId);

    List<OrderPlacementDispatchEntity> findAllByOrderNumberOrderByDispatchIdAsc(String orderNumber);
}
