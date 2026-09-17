package com.cp.ecommerce.adapter.common.mapping;

import java.util.Optional;

/**
 * Interface representing a mapper from a domain object to an outbound web resource (e.g. an HTTP response body). See
 * {@link WebRequestMapper} for why this is not combined with the inbound direction into a single mapper type.
 *
 * @param <D> domain object
 * @param <R> response resource object
 */
public interface WebResponseMapper<D, R> {

    Optional<R> mapToResource(D domainObject);

}
