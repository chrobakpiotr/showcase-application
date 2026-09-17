package com.cp.ecommerce.domain.returns.port.outgoing;

import com.cp.ecommerce.domain.returns.ReturnRequest;

/**
 * Outgoing port for saving a return request.
 */
public interface SaveReturnRequestOutPort {

    ReturnRequest save(ReturnRequest returnRequest);

}
