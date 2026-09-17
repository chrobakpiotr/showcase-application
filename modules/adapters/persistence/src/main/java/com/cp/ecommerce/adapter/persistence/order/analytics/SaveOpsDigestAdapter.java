package com.cp.ecommerce.adapter.persistence.order.analytics;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.order.analytics.mapper.OpsDigestPersistenceMapper;
import com.cp.ecommerce.domain.order.OpsDigest;
import com.cp.ecommerce.domain.order.port.outgoing.SaveOpsDigestOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link SaveOpsDigestOutPort} (see ADR 0022).
 */
@PersistenceAdapter
@RequiredArgsConstructor
class SaveOpsDigestAdapter implements SaveOpsDigestOutPort {

    private final OpsDigestEntityRepository opsDigestEntityRepository;

    private final OpsDigestPersistenceMapper opsDigestPersistenceMapper;

    @Override
    public void save(final OpsDigest opsDigest) {

        opsDigestPersistenceMapper.mapToEntity(opsDigest).ifPresent(opsDigestEntityRepository::save);
    }

}
