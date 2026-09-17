package com.cp.ecommerce.adapter.common.mapping;

import java.util.Optional;

/**
 * Interface representing queue message mapper.
 *
 * @param <D> domain object
 * @param <M> message object
 */
public interface QueueMessageMapper<D, M> {

    Optional<D> mapToDomainObject(M message);

    Optional<M> mapToMessage(D domainObject);

}
