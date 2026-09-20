package com.cp.ecommerce.adapter.persistence.returns;

import java.math.BigDecimal;
import java.time.Instant;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntityRepository;
import com.cp.ecommerce.adapter.persistence.returns.entity.ReturnRequestEntity;
import com.cp.ecommerce.adapter.persistence.returns.entity.ReturnRequestEntityRepository;
import com.cp.ecommerce.adapter.persistence.returns.mapper.ReturnRequestPersistenceMapper;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnStatus;
import com.cp.ecommerce.domain.returns.port.outgoing.ManageReturnRequestStateOutPort;
import com.cp.ecommerce.foundation.exception.ReturnQuantityConflictException;
import com.cp.ecommerce.foundation.exception.ReturnRequestNotApprovableException;
import com.cp.ecommerce.foundation.exception.ReturnRequestNotRefundableException;
import com.cp.ecommerce.foundation.exception.ReturnRequestNotRejectableException;

import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Atomic persistence implementation for return entitlement and moderation state.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class ManageReturnRequestStateAdapter implements ManageReturnRequestStateOutPort {

    private final ReturnRequestEntityRepository returnRequestEntityRepository;

    private final ReturnRequestPersistenceMapper returnRequestPersistenceMapper;

    private final OrderEntityRepository orderEntityRepository;

    @Override
    @Transactional
    public ReturnRequest create(final ReturnRequest returnRequest, final int orderedQuantity) {

        return create(returnRequest, orderedQuantity, false);
    }

    @Override
    @Transactional
    public ReturnRequest createFromLineEntitlement(final ReturnRequest returnRequest, final int orderedQuantity) {

        return create(returnRequest, orderedQuantity, true);
    }

    private ReturnRequest create(
            final ReturnRequest returnRequest,
            final int orderedQuantity,
            final boolean allocateFromLineEntitlement) {

        lockOrder(returnRequest.getOrderNumber());
        final long activeQuantity = returnRequestEntityRepository
                .sumActiveQuantity(returnRequest.getOrderNumber(), returnRequest.getSku(), ReturnStatus.REJECTED);
        final long remainingQuantity = Math.max(0L, (long) orderedQuantity - activeQuantity);
        if (returnRequest.getQuantity() > remainingQuantity) {
            throw new ReturnQuantityConflictException(Math.toIntExact(remainingQuantity));
        }

        final ReturnRequestEntity entity = returnRequestPersistenceMapper.mapToEntity(returnRequest)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map return request domain object to entity for return number: "
                                        + returnRequest.getReturnNumber()));
        if (allocateFromLineEntitlement) {
            entity.setRefundAmount(
                    allocateEntitlement(
                            returnRequest.getRefundAmount(),
                            orderedQuantity,
                            Math.toIntExact(activeQuantity),
                            returnRequest.getQuantity()));
        }
        return mapToDomain(returnRequestEntityRepository.saveAndFlush(entity));
    }

    @Override
    @Transactional
    public ReturnRequest approve(final String returnNumber) {

        final ReturnRequestEntity entity = returnRequestEntityRepository.findByReturnNumberForUpdate(returnNumber);
        if (entity == null) {

            return null;
        }
        if (entity.getStatus() == ReturnStatus.REJECTED) {

            throw new ReturnRequestNotApprovableException(
                    "Return request '" + returnNumber + "' cannot be approved after rejection");
        }
        if (entity.getStatus() == ReturnStatus.APPROVED || entity.getStatus() == ReturnStatus.REFUNDED) {

            return mapToDomain(entity);
        }

        entity.setStatus(ReturnStatus.APPROVED);
        entity.setDecidedDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()));
        return saveAndMap(entity);
    }

    @Override
    @Transactional
    public ReturnRequest reject(final String returnNumber) {

        final String orderNumber = returnRequestEntityRepository.findOrderNumberByReturnNumber(returnNumber);
        if (orderNumber == null) {

            return null;
        }

        lockOrder(orderNumber);
        final ReturnRequestEntity entity = returnRequestEntityRepository.findByReturnNumberForUpdate(returnNumber);
        if (entity == null) {

            return null;
        }
        if (entity.getStatus() == ReturnStatus.REJECTED) {

            return mapToDomain(entity);
        }
        if (entity.getStatus() != ReturnStatus.REQUESTED) {

            throw new ReturnRequestNotRejectableException(
                    "Return request '" + returnNumber + "' cannot be rejected once it is " + entity.getStatus());
        }

        entity.setStatus(ReturnStatus.REJECTED);
        entity.setDecidedDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()));
        return saveAndMap(entity);
    }

    @Override
    @Transactional
    public ReturnRequest markRefunded(final String returnNumber) {

        final ReturnRequestEntity entity = returnRequestEntityRepository.findByReturnNumberForUpdate(returnNumber);
        if (entity == null) {

            return null;
        }
        if (entity.getStatus() == ReturnStatus.REFUNDED) {

            return mapToDomain(entity);
        }
        if (entity.getStatus() != ReturnStatus.APPROVED) {

            throw new ReturnRequestNotRefundableException(
                    "Return request '" + returnNumber + "' cannot be marked refunded while it is " + entity.getStatus());
        }

        entity.setStatus(ReturnStatus.REFUNDED);
        return saveAndMap(entity);
    }

    private static BigDecimal allocateEntitlement(
            final BigDecimal fullLineEntitlement,
            final int orderedQuantity,
            final int alreadyAllocatedQuantity,
            final int requestedQuantity) {

        final long cents = fullLineEntitlement.movePointRight(2).longValueExact();
        final long base = cents / orderedQuantity;
        final long remainder = cents % orderedQuantity;
        long allocated = 0L;
        for (int unit = alreadyAllocatedQuantity; unit < alreadyAllocatedQuantity + requestedQuantity; unit++) {
            allocated += base + (unit < remainder ? 1L : 0L);
        }
        return BigDecimal.valueOf(allocated, 2);
    }

    private ReturnRequest saveAndMap(final ReturnRequestEntity entity) {

        return mapToDomain(returnRequestEntityRepository.saveAndFlush(entity));
    }

    private ReturnRequest mapToDomain(final ReturnRequestEntity entity) {

        return returnRequestPersistenceMapper.mapToDomainObject(entity)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map return request entity to domain object for return number: "
                                        + entity.getReturnNumber()));
    }

    private void lockOrder(final String orderNumber) {

        if (orderEntityRepository.findByOrderNumberForUpdate(orderNumber) == null) {

            throw new IllegalStateException("Order disappeared while managing return entitlement: " + orderNumber);
        }
    }

}
