package com.cp.ecommerce.domain.order.port.outgoing;

import java.util.Optional;

import com.cp.ecommerce.domain.order.OpsDigest;

/**
 * Outgoing port for reading back the most recently persisted {@link OpsDigest} (see ADR 0022).
 */
public interface GetLatestOpsDigestOutPort {

    /**
     * @return the most recently generated {@link OpsDigest}, or empty if none has been generated yet (e.g. right after a fresh
     *         deployment, before the scheduler's eager first run has completed).
     */
    Optional<OpsDigest> findLatest();

}
