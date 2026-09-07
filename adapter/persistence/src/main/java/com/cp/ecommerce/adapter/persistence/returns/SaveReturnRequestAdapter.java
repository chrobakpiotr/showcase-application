package com.cp.ecommerce.adapter.persistence.returns;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.returns.entity.ReturnRequestEntity;
import com.cp.ecommerce.adapter.persistence.returns.entity.ReturnRequestEntityRepository;
import com.cp.ecommerce.adapter.persistence.returns.mapper.ReturnRequestPersistenceMapper;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.port.outgoing.SaveReturnRequestOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link SaveReturnRequestOutPort}.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class SaveReturnRequestAdapter implements SaveReturnRequestOutPort {

    private final ReturnRequestEntityRepository returnRequestEntityRepository;

    private final ReturnRequestPersistenceMapper returnRequestPersistenceMapper;

    @Override
    public ReturnRequest save(final ReturnRequest returnRequest) {

        final ReturnRequestEntity entityToSave = returnRequestPersistenceMapper.mapToEntity(returnRequest)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map return request domain object to entity for return number: "
                                        + returnRequest.getReturnNumber()));
        final ReturnRequestEntity saved = returnRequestEntityRepository.save(entityToSave);
        return returnRequestPersistenceMapper.mapToDomainObject(saved)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map return request entity to domain object for return number: "
                                        + returnRequest.getReturnNumber()));
    }

}
