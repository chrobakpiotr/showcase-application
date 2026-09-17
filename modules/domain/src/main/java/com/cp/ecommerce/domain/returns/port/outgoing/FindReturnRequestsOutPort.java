package com.cp.ecommerce.domain.returns.port.outgoing;

import java.util.List;

import com.cp.ecommerce.domain.returns.ReturnRequest;

/**
 * Outgoing port for listing return requests.
 */
public interface FindReturnRequestsOutPort {

    List<ReturnRequest> findAll();

    List<ReturnRequest> findPending();

    List<ReturnRequest> findByOrderNumber(String orderNumber);

}
