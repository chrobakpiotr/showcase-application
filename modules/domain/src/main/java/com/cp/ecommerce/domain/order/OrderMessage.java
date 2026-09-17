package com.cp.ecommerce.domain.order;

import java.util.Date;

import com.cp.ecommerce.adapter.common.annotation.DomainObject;

import lombok.Builder;

/**
 * Representation of order message domain object.
 */
@Builder
@DomainObject
public record OrderMessage(String schemaVersion, Date created, Long customerId, String orderNumber) {

    public static final String SCHEMA_VERSION = "1.0";

    public OrderMessage {

        if (schemaVersion == null) {
            schemaVersion = SCHEMA_VERSION;
        }
    }

}
