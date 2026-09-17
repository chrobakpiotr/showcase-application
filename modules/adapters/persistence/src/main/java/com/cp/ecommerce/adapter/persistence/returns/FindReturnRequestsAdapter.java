package com.cp.ecommerce.adapter.persistence.returns;

import java.util.List;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.returns.entity.ReturnRequestEntity;
import com.cp.ecommerce.adapter.persistence.returns.entity.ReturnRequestEntityRepository;
import com.cp.ecommerce.adapter.persistence.returns.mapper.ReturnRequestPersistenceMapper;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnStatus;
import com.cp.ecommerce.domain.returns.port.outgoing.FindReturnRequestsOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link FindReturnRequestsOutPort}.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindReturnRequestsAdapter implements FindReturnRequestsOutPort {

    private final ReturnRequestEntityRepository returnRequestEntityRepository;

    private final ReturnRequestPersistenceMapper returnRequestPersistenceMapper;

    @Override
    public List<ReturnRequest> findAll() {

        return returnRequestEntityRepository.findAllByOrderByRequestedDateDesc()
                .stream()
                .map(this::mapToDomainObjectOrThrow)
                .toList();
    }

    @Override
    public List<ReturnRequest> findPending() {

        return returnRequestEntityRepository.findByStatusOrderByRequestedDateAsc(ReturnStatus.REQUESTED)
                .stream()
                .map(this::mapToDomainObjectOrThrow)
                .toList();
    }

    @Override
    public List<ReturnRequest> findByOrderNumber(final String orderNumber) {

        return returnRequestEntityRepository.findByOrderNumberOrderByRequestedDateDesc(orderNumber)
                .stream()
                .map(this::mapToDomainObjectOrThrow)
                .toList();
    }

    private ReturnRequest mapToDomainObjectOrThrow(final ReturnRequestEntity entity) {

        return returnRequestPersistenceMapper.mapToDomainObject(entity)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map return request entity to domain object for return number: "
                                        + entity.getReturnNumber()));
    }

}
