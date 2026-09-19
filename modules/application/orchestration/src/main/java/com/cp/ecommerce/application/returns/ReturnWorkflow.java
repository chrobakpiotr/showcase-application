package com.cp.ecommerce.application.returns;

import com.cp.ecommerce.domain.returns.ReturnRequest;

public interface ReturnWorkflow {

    ReturnRequest requestReturn(String orderNumber, String sku, int quantity, String reason);

    ReturnRequest approveReturn(String returnNumber);

    ReturnRequest rejectReturn(String returnNumber);
}
