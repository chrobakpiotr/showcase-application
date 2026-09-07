package com.cp.ecommerce.domain.returns.port.incoming;

import com.cp.ecommerce.domain.returns.ReturnRequest;

/**
 * Incoming port for approving, rejecting and finalizing return requests.
 */
public interface ReturnModerationInPort {

    ReturnRequest approveReturn(String returnNumber);

    ReturnRequest rejectReturn(String returnNumber);

    ReturnRequest markRefunded(String returnNumber);

}
