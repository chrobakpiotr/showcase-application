package com.cp.ecommerce.adapter.persistence.returns.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.PersistenceMapper;
import com.cp.ecommerce.adapter.persistence.returns.entity.ReturnRequestEntity;
import com.cp.ecommerce.domain.returns.ReturnRequest;

import org.springframework.stereotype.Component;

import static java.util.Optional.ofNullable;

/**
 * Mapper responsible for changing {@link ReturnRequest} object into/from entity object.
 */
@Component
public class ReturnRequestPersistenceMapper implements PersistenceMapper<ReturnRequest, ReturnRequestEntity> {

    @Override
    public Optional<ReturnRequestEntity> mapToEntity(final ReturnRequest returnRequest) {

        return ofNullable(returnRequest).map(
                domain -> ReturnRequestEntity.builder()
                        .returnNumber(domain.getReturnNumber())
                        .orderNumber(domain.getOrderNumber())
                        .sku(domain.getSku())
                        .quantity(domain.getQuantity())
                        .reason(domain.getReason())
                        .status(domain.getStatus())
                        .requestedDate(domain.getRequestedDate())
                        .decidedDate(domain.getDecidedDate())
                        .refundAmount(domain.getRefundAmount())
                        .build());
    }

    @Override
    public Optional<ReturnRequest> mapToDomainObject(final ReturnRequestEntity entity) {

        return ofNullable(entity).map(
                returnRequestEntity -> ReturnRequest.builder()
                        .returnNumber(returnRequestEntity.getReturnNumber())
                        .orderNumber(returnRequestEntity.getOrderNumber())
                        .sku(returnRequestEntity.getSku())
                        .quantity(returnRequestEntity.getQuantity())
                        .reason(returnRequestEntity.getReason())
                        .status(returnRequestEntity.getStatus())
                        .requestedDate(returnRequestEntity.getRequestedDate())
                        .decidedDate(returnRequestEntity.getDecidedDate())
                        .refundAmount(returnRequestEntity.getRefundAmount())
                        .build());
    }

}
