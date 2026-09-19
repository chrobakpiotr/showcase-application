package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.incoming.ExportOrderInPort;
import com.cp.ecommerce.domain.order.port.outgoing.StoreOrderExportOutPort;
import com.cp.ecommerce.foundation.annotation.UseCase;

import lombok.RequiredArgsConstructor;

/**
 * Use case for exporting a processed order to durable storage.
 */
@RequiredArgsConstructor
@UseCase
public class ExportOrderUseCase implements ExportOrderInPort {

    private final StoreOrderExportOutPort storeOrderExportOutPort;

    @Override
    public void exportOrder(final Order order) {

        storeOrderExportOutPort.store(order);
    }

}
