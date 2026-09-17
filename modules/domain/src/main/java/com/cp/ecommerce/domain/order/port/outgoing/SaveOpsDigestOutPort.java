package com.cp.ecommerce.domain.order.port.outgoing;

import com.cp.ecommerce.domain.order.OpsDigest;

/**
 * Outgoing port for persisting a generated {@link OpsDigest} (see ADR 0022), so the latest one survives process restarts and
 * can be read back by {@link GetLatestOpsDigestOutPort}.
 */
public interface SaveOpsDigestOutPort {

    /**
     * @param opsDigest the digest to persist.
     */
    void save(OpsDigest opsDigest);

}
