package com.cp.ecommerce.domain.order.usecase;

import java.util.Optional;

import com.cp.ecommerce.domain.order.OpsDigest;
import com.cp.ecommerce.domain.order.port.incoming.GetLatestOpsDigestInPort;
import com.cp.ecommerce.domain.order.port.outgoing.GetLatestOpsDigestOutPort;
import com.cp.ecommerce.foundation.annotation.UseCase;

import lombok.RequiredArgsConstructor;

/**
 * Use case reading back the latest generated {@link OpsDigest}.
 */
@UseCase
@RequiredArgsConstructor
public class GetLatestOpsDigestUseCase implements GetLatestOpsDigestInPort {

    private final GetLatestOpsDigestOutPort getLatestOpsDigestOutPort;

    @Override
    public Optional<OpsDigest> getLatestDigest() {

        return getLatestOpsDigestOutPort.findLatest();
    }

}
