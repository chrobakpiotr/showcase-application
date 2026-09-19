package com.cp.ecommerce.domain.returns.port.incoming;

import java.util.List;

import com.cp.ecommerce.domain.returns.PageQuery;
import com.cp.ecommerce.domain.returns.PagedResult;
import com.cp.ecommerce.domain.returns.ReturnRequest;

/**
 * Incoming port for listing return requests.
 */
public interface ListReturnsInPort {

    List<ReturnRequest> listReturns();

    List<ReturnRequest> listPendingReturns();

    List<ReturnRequest> listReturnsForOrder(String orderNumber);

    PagedResult<ReturnRequest> listReturns(PageQuery pageQuery);

    PagedResult<ReturnRequest> listPendingReturns(PageQuery pageQuery);

    PagedResult<ReturnRequest> listReturnsForOrder(String orderNumber, PageQuery pageQuery);

}
