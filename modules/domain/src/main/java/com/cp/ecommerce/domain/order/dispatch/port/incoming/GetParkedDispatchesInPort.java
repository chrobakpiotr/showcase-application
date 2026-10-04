package com.cp.ecommerce.domain.order.dispatch.port.incoming;

import com.cp.ecommerce.domain.order.dispatch.ParkedDispatchPage;
import com.cp.ecommerce.domain.order.dispatch.ParkedDispatchQuery;

public interface GetParkedDispatchesInPort {

    ParkedDispatchPage getParkedDispatches(ParkedDispatchQuery query);
}
