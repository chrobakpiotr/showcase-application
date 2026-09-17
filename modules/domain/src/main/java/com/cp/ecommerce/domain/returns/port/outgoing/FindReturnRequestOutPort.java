package com.cp.ecommerce.domain.returns.port.outgoing;

import com.cp.ecommerce.domain.returns.ReturnRequest;

/**
 * Outgoing port for finding a single return request.
 */
public interface FindReturnRequestOutPort {

    ReturnRequest find(String returnNumber);

}
