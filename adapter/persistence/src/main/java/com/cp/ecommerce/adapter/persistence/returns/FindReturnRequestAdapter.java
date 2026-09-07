package com.cp.ecommerce.adapter.persistence.returns;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.returns.entity.ReturnRequestEntityRepository;
import com.cp.ecommerce.adapter.persistence.returns.mapper.ReturnRequestPersistenceMapper;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.port.outgoing.FindReturnRequestOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link FindReturnRequestOutPort}.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindReturnRequestAdapter implements FindReturnRequestOutPort {

    private final ReturnRequestEntityRepository returnRequestEntityRepository;

    private final ReturnRequestPersistenceMapper returnRequestPersistenceMapper;

    @Override
    public ReturnRequest find(final String returnNumber) {

        return Optional.ofNullable(returnRequestEntityRepository.findByReturnNumber(returnNumber))
                .map(
                        entity -> returnRequestPersistenceMapper.mapToDomainObject(entity)
                                .orElseThrow(
                                        () -> new IllegalStateException(
                                                "Failed to map return request entity to domain object for return number: "
                                                        + returnNumber)))
                .orElse(null);
    }

}
