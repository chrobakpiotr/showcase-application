package com.cp.ecommerce.adapter.persistence.returns;

import java.util.UUID;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.returns.port.outgoing.GenerateReturnNumberOutPort;

/**
 * Implementation of {@link GenerateReturnNumberOutPort}.
 */
@PersistenceAdapter
class GenerateReturnNumberAdapter implements GenerateReturnNumberOutPort {

    private static final String RETURN_NUMBER_PREFIX = "RETURN-";

    @Override
    public String generate() {

        return RETURN_NUMBER_PREFIX + UUID.randomUUID();
    }

}
