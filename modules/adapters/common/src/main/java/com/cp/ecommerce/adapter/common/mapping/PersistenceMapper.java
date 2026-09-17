package com.cp.ecommerce.adapter.common.mapping;

import java.util.Optional;

/**
 * Interface representing persistence mapper.
 *
 * @param <D> domain object
 * @param <E> entity object
 */
public interface PersistenceMapper<D, E> {

    Optional<D> mapToDomainObject(E entity);

    Optional<E> mapToEntity(D domainObject);

}
