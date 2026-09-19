package com.cp.ecommerce.domain.returns.usecase;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.cp.ecommerce.domain.returns.PageQuery;
import com.cp.ecommerce.domain.returns.PagedResult;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnStatus;
import com.cp.ecommerce.domain.returns.port.incoming.GetReturnInPort;
import com.cp.ecommerce.domain.returns.port.incoming.ListReturnsInPort;
import com.cp.ecommerce.domain.returns.port.incoming.RequestReturnInPort;
import com.cp.ecommerce.domain.returns.port.incoming.ReturnModerationInPort;
import com.cp.ecommerce.domain.returns.port.outgoing.FindReturnRequestOutPort;
import com.cp.ecommerce.domain.returns.port.outgoing.FindReturnRequestsOutPort;
import com.cp.ecommerce.domain.returns.port.outgoing.GenerateReturnNumberOutPort;
import com.cp.ecommerce.domain.returns.port.outgoing.ManageReturnRequestStateOutPort;
import com.cp.ecommerce.foundation.annotation.UseCase;

import lombok.RequiredArgsConstructor;

/**
 * Use case for creating and moderating return requests.
 */
@UseCase
@RequiredArgsConstructor
public class ManageReturnUseCase implements RequestReturnInPort, GetReturnInPort, ListReturnsInPort, ReturnModerationInPort {

    private final FindReturnRequestOutPort findReturnRequestOutPort;

    private final FindReturnRequestsOutPort findReturnRequestsOutPort;

    private final ManageReturnRequestStateOutPort manageReturnRequestStateOutPort;

    private final GenerateReturnNumberOutPort generateReturnNumberOutPort;

    @Override
    public ReturnRequest requestReturn(
            final String orderNumber,
            final String sku,
            final int quantity,
            final int orderedQuantity,
            final String reason,
            final BigDecimal refundAmount) {

        final ReturnRequest requested = ReturnRequest.builder()
                .returnNumber(generateReturnNumberOutPort.generate())
                .orderNumber(orderNumber)
                .sku(sku)
                .quantity(quantity)
                .reason(reason)
                .status(ReturnStatus.REQUESTED)
                .requestedDate(new Date())
                .refundAmount(refundAmount)
                .build();
        requested.assertValidationsEmpty();
        return manageReturnRequestStateOutPort.create(requested, orderedQuantity);
    }

    @Override
    public ReturnRequest getReturn(final String returnNumber) {

        return findReturnRequestOutPort.find(returnNumber);
    }

    @Override
    public List<ReturnRequest> listReturns() {

        return findReturnRequestsOutPort.findAll();
    }

    @Override
    public List<ReturnRequest> listPendingReturns() {

        return findReturnRequestsOutPort.findPending();
    }

    @Override
    public List<ReturnRequest> listReturnsForOrder(final String orderNumber) {

        return findReturnRequestsOutPort.findByOrderNumber(orderNumber);
    }

    @Override
    public ReturnRequest approveReturn(final String returnNumber) {

        return manageReturnRequestStateOutPort.approve(returnNumber);
    }

    @Override
    public ReturnRequest rejectReturn(final String returnNumber) {

        return manageReturnRequestStateOutPort.reject(returnNumber);
    }

    @Override
    public ReturnRequest markRefunded(final String returnNumber) {

        return manageReturnRequestStateOutPort.markRefunded(returnNumber);
    }

    @Override
    public PagedResult<ReturnRequest> listReturns(final PageQuery pageQuery) {
        return findReturnRequestsOutPort.findAll(pageQuery);
    }

    @Override
    public PagedResult<ReturnRequest> listPendingReturns(final PageQuery pageQuery) {
        return findReturnRequestsOutPort.findPending(pageQuery);
    }

    @Override
    public PagedResult<ReturnRequest> listReturnsForOrder(final String orderNumber, final PageQuery pageQuery) {
        return findReturnRequestsOutPort.findByOrderNumber(orderNumber, pageQuery);
    }

}
