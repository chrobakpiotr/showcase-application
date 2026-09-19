package com.cp.ecommerce.domain.returns.port.outgoing;

import java.util.List;

import com.cp.ecommerce.domain.returns.PageQuery;
import com.cp.ecommerce.domain.returns.PagedResult;
import com.cp.ecommerce.domain.returns.ReturnRequest;

/**
 * Outgoing port for listing return requests.
 */
public interface FindReturnRequestsOutPort {

    List<ReturnRequest> findAll();

    List<ReturnRequest> findPending();

    List<ReturnRequest> findByOrderNumber(String orderNumber);

    PagedResult<ReturnRequest> findAll(PageQuery pageQuery);

    PagedResult<ReturnRequest> findPending(PageQuery pageQuery);

    PagedResult<ReturnRequest> findByOrderNumber(String orderNumber, PageQuery pageQuery);

}
