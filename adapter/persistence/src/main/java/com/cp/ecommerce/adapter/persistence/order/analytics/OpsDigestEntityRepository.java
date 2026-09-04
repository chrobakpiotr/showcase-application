package com.cp.ecommerce.adapter.persistence.order.analytics;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link OpsDigestEntity}.
 */
public interface OpsDigestEntityRepository extends JpaRepository<OpsDigestEntity, Long> {

    /**
     * @return the most recently generated digest, if any.
     */
    Optional<OpsDigestEntity> findFirstByOrderByGeneratedDateDesc();

}
