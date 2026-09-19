package com.cp.ecommerce.adapter.web.returns.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.WebRequestMapper;
import com.cp.ecommerce.adapter.common.mapping.WebResponseMapper;
import com.cp.ecommerce.adapter.web.returns.resource.RequestReturnResource;
import com.cp.ecommerce.adapter.web.returns.resource.ReturnRequestResource;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnStatus;

import org.springframework.stereotype.Component;

/**
 * Mapper responsible for mapping {@link ReturnRequest} objects to and from web resources.
 */
@Component
public class ReturnWebMapper implements WebRequestMapper<ReturnRequest, RequestReturnResource>,
        WebResponseMapper<ReturnRequest, ReturnRequestResource> {

    @Override
    public Optional<ReturnRequest> mapToDomainObject(final RequestReturnResource resource) {

        return Optional.ofNullable(resource)
                .map(
                        request -> ReturnRequest.builder()
                                .orderNumber(request.orderNumber())
                                .sku(request.sku())
                                .quantity(request.quantity() == null ? 0 : request.quantity())
                                .reason(request.reason())
                                .status(ReturnStatus.REQUESTED)
                                .requestedDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
                                .refundAmount(BigDecimal.ZERO)
                                .build());
    }

    @Override
    public Optional<ReturnRequestResource> mapToResource(final ReturnRequest returnRequest) {

        return Optional.ofNullable(returnRequest)
                .map(
                        domain -> ReturnRequestResource.builder()
                                .returnNumber(domain.getReturnNumber())
                                .orderNumber(domain.getOrderNumber())
                                .sku(domain.getSku())
                                .quantity(domain.getQuantity())
                                .reason(domain.getReason())
                                .status(domain.getStatus().name())
                                .requestedDate(domain.getRequestedDate())
                                .decidedDate(domain.getDecidedDate())
                                .refundAmount(domain.getRefundAmount())
                                .build());
    }

}
