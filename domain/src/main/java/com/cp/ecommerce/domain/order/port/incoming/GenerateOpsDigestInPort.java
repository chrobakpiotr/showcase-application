package com.cp.ecommerce.domain.order.port.incoming;

import com.cp.ecommerce.domain.order.OpsDigest;

/**
 * Incoming port for generating a fresh {@link OpsDigest} from the current order-volume and remarks-triage figures, and
 * persisting it as the new "latest" digest (see ADR 0022). Driven by {@code OpsDigestScheduler}, both eagerly on application
 * start-up and thereafter on a recurring schedule.
 */
public interface GenerateOpsDigestInPort {

    /**
     * @return the freshly generated (and persisted) {@link OpsDigest}.
     */
    OpsDigest generateDigest();

}
