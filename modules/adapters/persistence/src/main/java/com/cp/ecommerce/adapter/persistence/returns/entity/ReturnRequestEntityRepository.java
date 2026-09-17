package com.cp.ecommerce.adapter.persistence.returns.entity;

import java.util.List;

import com.cp.ecommerce.domain.returns.ReturnStatus;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link ReturnRequestEntity}.
 */
public interface ReturnRequestEntityRepository extends JpaRepository<ReturnRequestEntity, String> {

    ReturnRequestEntity findByReturnNumber(String returnNumber);

    List<ReturnRequestEntity> findAllByOrderByRequestedDateDesc();

    List<ReturnRequestEntity> findByStatusOrderByRequestedDateAsc(ReturnStatus status);

    List<ReturnRequestEntity> findByOrderNumberOrderByRequestedDateDesc(String orderNumber);

}
