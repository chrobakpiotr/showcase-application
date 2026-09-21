package com.cp.ecommerce.domain.returns.port.incoming;

import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnRequestCommand;

/**
 * Incoming port for creating a new return request.
 */
public interface RequestReturnInPort {

    ReturnRequest requestReturn(ReturnRequestCommand command);

    /**
     * Creates an RMA whose amount must be allocated from the immutable full-line payable entitlement.
     */
    ReturnRequest requestReturnFromLineEntitlement(ReturnRequestCommand command);

}
