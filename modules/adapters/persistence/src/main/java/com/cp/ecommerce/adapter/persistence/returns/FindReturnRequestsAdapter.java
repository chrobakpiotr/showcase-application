package com.cp.ecommerce.adapter.persistence.returns;

import java.util.List;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.returns.entity.ReturnRequestEntity;
import com.cp.ecommerce.adapter.persistence.returns.entity.ReturnRequestEntityRepository;
import com.cp.ecommerce.adapter.persistence.returns.mapper.ReturnRequestPersistenceMapper;
import com.cp.ecommerce.domain.returns.PageQuery;
import com.cp.ecommerce.domain.returns.PagedResult;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnStatus;
import com.cp.ecommerce.domain.returns.port.outgoing.FindReturnRequestsOutPort;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

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

    @Override
    public PagedResult<ReturnRequest> findAll(final PageQuery q) {
        return page(returnRequestEntityRepository.findAllByOrderByRequestedDateDesc(PageRequest.of(q.page(), q.size())));
    }

    @Override
    public PagedResult<ReturnRequest> findPending(final PageQuery q) {
        return page(
                returnRequestEntityRepository
                        .findByStatusOrderByRequestedDateAsc(ReturnStatus.REQUESTED, PageRequest.of(q.page(), q.size())));
    }

    @Override
    public PagedResult<ReturnRequest> findByOrderNumber(final String orderNumber, final PageQuery q) {
        return page(
                returnRequestEntityRepository
                        .findByOrderNumberOrderByRequestedDateDesc(orderNumber, PageRequest.of(q.page(), q.size())));
    }

    private PagedResult<ReturnRequest> page(final Page<ReturnRequestEntity> page) {
        return new PagedResult<>(
                page.getContent().stream().map(this::mapToDomainObjectOrThrow).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

}
