package com.cp.ecommerce.domain.returns.port.incoming;

import com.cp.ecommerce.domain.returns.ReturnRequest;

/**
 * Incoming port for reading a single return request.
 */
public interface GetReturnInPort {

    ReturnRequest getReturn(String returnNumber);

}
