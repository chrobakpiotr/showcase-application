package com.cp.ecommerce.adapter.common.mapping;

import java.util.Optional;

/**
 * Interface representing a mapper from an inbound web resource (e.g. an HTTP request body) to a domain object. Kept separate
 * from {@link WebResponseMapper} because request and response resources commonly have different shapes (e.g. a creation request
 * omits server-generated fields that a response includes), so a single mapper type covering both directions with one shared
 * resource type would not fit.
 *
 * @param <D> domain object
 * @param <R> request resource object
 */
public interface WebRequestMapper<D, R> {

    Optional<D> mapToDomainObject(R resource);

}
