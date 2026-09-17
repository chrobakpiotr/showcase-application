package com.cp.ecommerce.adapter.persistence.catalog;

import java.util.UUID;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.catalog.port.outgoing.GenerateSkuOutPort;

/**
 * Implementation of {@link GenerateSkuOutPort}.
 *
 * <p>
 * Unlike {@code GenerateOrderNumberAdapter} (which combines a dialect-specific database sequence with a UUID for a
 * human-legible, monotonically-traceable order number), a product SKU has no such ordering/traceability requirement - a pure
 * random UUID is sufficient to guarantee uniqueness without needing a second, dialect-specific (H2 vs Postgres) adapter.
 */
@PersistenceAdapter
class GenerateSkuAdapter implements GenerateSkuOutPort {

    private static final String SKU_PREFIX = "SKU-";

    @Override
    public String generate() {

        return SKU_PREFIX + UUID.randomUUID();
    }

}
