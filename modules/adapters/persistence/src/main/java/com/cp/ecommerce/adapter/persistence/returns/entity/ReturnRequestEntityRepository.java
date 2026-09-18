package com.cp.ecommerce.adapter.persistence.returns.entity;

import java.util.List;

import com.cp.ecommerce.domain.returns.ReturnStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/**
 * Spring Data repository for {@link ReturnRequestEntity}.
 */
public interface ReturnRequestEntityRepository extends JpaRepository<ReturnRequestEntity, String> {

    ReturnRequestEntity findByReturnNumber(String returnNumber);

    @Query("select r.orderNumber from ReturnRequestEntity r where r.returnNumber = :returnNumber")
    String findOrderNumberByReturnNumber(@Param("returnNumber") String returnNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReturnRequestEntity r where r.returnNumber = :returnNumber")
    ReturnRequestEntity findByReturnNumberForUpdate(@Param("returnNumber") String returnNumber);

    @Query("select coalesce(sum(r.quantity), 0) from ReturnRequestEntity r "
            + "where r.orderNumber = :orderNumber and r.sku = :sku and r.status <> :rejectedStatus")
    long sumActiveQuantity(
            @Param("orderNumber") String orderNumber,
            @Param("sku") String sku,
            @Param("rejectedStatus") ReturnStatus rejectedStatus);

    List<ReturnRequestEntity> findAllByOrderByRequestedDateDesc();

    List<ReturnRequestEntity> findByStatusOrderByRequestedDateAsc(ReturnStatus status);

    List<ReturnRequestEntity> findByOrderNumberOrderByRequestedDateDesc(String orderNumber);

}
