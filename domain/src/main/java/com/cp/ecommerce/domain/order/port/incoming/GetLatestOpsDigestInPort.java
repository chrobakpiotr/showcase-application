package com.cp.ecommerce.domain.order.port.incoming;

import java.util.Optional;

import com.cp.ecommerce.domain.order.OpsDigest;

/**
 * Incoming port for reading the latest generated {@link OpsDigest} (see ADR 0022), used by the ops-analytics page to display it
 * alongside the AI chat assistant.
 */
public interface GetLatestOpsDigestInPort {

    /**
     * @return the most recently generated {@link OpsDigest}, or empty if none has been generated yet.
     */
    Optional<OpsDigest> getLatestDigest();

}
