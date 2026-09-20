package com.cp.ecommerce.domain.returns.port.outgoing;

import com.cp.ecommerce.domain.returns.ReturnRequest;

/**
 * Atomic persistence boundary for return entitlement and moderation state.
 */
public interface ManageReturnRequestStateOutPort {

    ReturnRequest create(ReturnRequest returnRequest, int orderedQuantity);

    ReturnRequest createFromLineEntitlement(ReturnRequest returnRequest, int orderedQuantity);

    ReturnRequest approve(String returnNumber);

    ReturnRequest reject(String returnNumber);

    ReturnRequest markRefunded(String returnNumber);

}
