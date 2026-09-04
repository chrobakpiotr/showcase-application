package com.cp.ecommerce.adapter.persistence.order.analytics;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.order.analytics.mapper.OpsDigestPersistenceMapper;
import com.cp.ecommerce.domain.order.OpsDigest;
import com.cp.ecommerce.domain.order.port.outgoing.GetLatestOpsDigestOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link GetLatestOpsDigestOutPort} (see ADR 0022).
 */
@PersistenceAdapter
@RequiredArgsConstructor
class GetLatestOpsDigestAdapter implements GetLatestOpsDigestOutPort {

    private final OpsDigestEntityRepository opsDigestEntityRepository;

    private final OpsDigestPersistenceMapper opsDigestPersistenceMapper;

    @Override
    public Optional<OpsDigest> findLatest() {

        return opsDigestEntityRepository.findFirstByOrderByGeneratedDateDesc()
                .flatMap(opsDigestPersistenceMapper::mapToDomainObject);
    }

}
