package com.cp.ecommerce.domain.returns.usecase;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.adapter.common.exception.ReturnRequestNotApprovableException;
import com.cp.ecommerce.adapter.common.exception.ReturnRequestNotRefundableException;
import com.cp.ecommerce.adapter.common.exception.ReturnRequestNotRejectableException;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnStatus;
import com.cp.ecommerce.domain.returns.port.incoming.GetReturnInPort;
import com.cp.ecommerce.domain.returns.port.incoming.ListReturnsInPort;
import com.cp.ecommerce.domain.returns.port.incoming.RequestReturnInPort;
import com.cp.ecommerce.domain.returns.port.incoming.ReturnModerationInPort;
import com.cp.ecommerce.domain.returns.port.outgoing.FindReturnRequestOutPort;
import com.cp.ecommerce.domain.returns.port.outgoing.FindReturnRequestsOutPort;
import com.cp.ecommerce.domain.returns.port.outgoing.GenerateReturnNumberOutPort;
import com.cp.ecommerce.domain.returns.port.outgoing.SaveReturnRequestOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for creating and moderating return requests.
 */
@UseCase
@RequiredArgsConstructor
public class ManageReturnUseCase implements RequestReturnInPort, GetReturnInPort, ListReturnsInPort, ReturnModerationInPort {

    private final FindReturnRequestOutPort findReturnRequestOutPort;

    private final FindReturnRequestsOutPort findReturnRequestsOutPort;

    private final SaveReturnRequestOutPort saveReturnRequestOutPort;

    private final GenerateReturnNumberOutPort generateReturnNumberOutPort;

    @Override
    public ReturnRequest requestReturn(
            final String orderNumber,
            final String sku,
            final int quantity,
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
        return saveReturnRequestOutPort.save(requested);
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

        final ReturnRequest existing = findReturnRequestOutPort.find(returnNumber);
        if (existing == null) {

            return null;
        }
        if (existing.getStatus() == ReturnStatus.REJECTED) {

            throw new ReturnRequestNotApprovableException(
                    "Return request '" + returnNumber + "' cannot be approved after rejection");
        }
        if (existing.getStatus() == ReturnStatus.APPROVED || existing.getStatus() == ReturnStatus.REFUNDED) {

            return existing;
        }
        return save(returnWithStatus(existing, ReturnStatus.APPROVED, new Date()));
    }

    @Override
    public ReturnRequest rejectReturn(final String returnNumber) {

        final ReturnRequest existing = findReturnRequestOutPort.find(returnNumber);
        if (existing == null) {

            return null;
        }
        if (existing.getStatus() == ReturnStatus.REJECTED) {

            return existing;
        }
        if (existing.getStatus() != ReturnStatus.REQUESTED) {

            throw new ReturnRequestNotRejectableException(
                    "Return request '" + returnNumber + "' cannot be rejected once it is " + existing.getStatus());
        }
        return save(returnWithStatus(existing, ReturnStatus.REJECTED, new Date()));
    }

    @Override
    public ReturnRequest markRefunded(final String returnNumber) {

        final ReturnRequest existing = findReturnRequestOutPort.find(returnNumber);
        if (existing == null) {

            return null;
        }
        if (existing.getStatus() == ReturnStatus.REFUNDED) {

            return existing;
        }
        if (existing.getStatus() != ReturnStatus.APPROVED) {

            throw new ReturnRequestNotRefundableException(
                    "Return request '" + returnNumber + "' cannot be marked refunded while it is " + existing.getStatus());
        }
        return save(returnWithStatus(existing, ReturnStatus.REFUNDED, existing.getDecidedDate()));
    }

    private ReturnRequest save(final ReturnRequest returnRequest) {

        returnRequest.assertValidationsEmpty();
        return saveReturnRequestOutPort.save(returnRequest);
    }

    private ReturnRequest returnWithStatus(final ReturnRequest existing, final ReturnStatus status, final Date decidedDate) {

        return ReturnRequest.builder()
                .returnNumber(existing.getReturnNumber())
                .orderNumber(existing.getOrderNumber())
                .sku(existing.getSku())
                .quantity(existing.getQuantity())
                .reason(existing.getReason())
                .status(status)
                .requestedDate(existing.getRequestedDate())
                .decidedDate(decidedDate)
                .refundAmount(existing.getRefundAmount())
                .build();
    }

}
