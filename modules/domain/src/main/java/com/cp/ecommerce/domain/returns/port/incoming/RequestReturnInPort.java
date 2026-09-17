package com.cp.ecommerce.domain.returns.port.incoming;

import java.math.BigDecimal;

import com.cp.ecommerce.domain.returns.ReturnRequest;

/**
 * Incoming port for creating a new return request.
 */
public interface RequestReturnInPort {

    ReturnRequest requestReturn(String orderNumber, String sku, int quantity, String reason, BigDecimal refundAmount);

}
