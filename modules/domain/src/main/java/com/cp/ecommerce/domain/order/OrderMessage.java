package com.cp.ecommerce.domain.order;

import java.time.Instant;

import com.cp.ecommerce.foundation.annotation.DomainObject;

import lombok.Builder;

/**
 * Representation of order message domain object.
 */
@Builder
@DomainObject
public record OrderMessage(String schemaVersion, Instant created, Long customerId, String orderNumber) {

    public static final String SCHEMA_VERSION = "1.0";

    public OrderMessage {

        if (schemaVersion == null) {
            schemaVersion = SCHEMA_VERSION;
        }
    }

}
