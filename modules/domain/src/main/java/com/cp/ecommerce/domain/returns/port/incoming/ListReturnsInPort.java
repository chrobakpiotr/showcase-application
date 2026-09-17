package com.cp.ecommerce.domain.returns.port.incoming;

import java.util.List;

import com.cp.ecommerce.domain.returns.ReturnRequest;

/**
 * Incoming port for listing return requests.
 */
public interface ListReturnsInPort {

    List<ReturnRequest> listReturns();

    List<ReturnRequest> listPendingReturns();

    List<ReturnRequest> listReturnsForOrder(String orderNumber);

}
