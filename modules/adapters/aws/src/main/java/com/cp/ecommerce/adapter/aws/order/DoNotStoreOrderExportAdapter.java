package com.cp.ecommerce.adapter.aws.order;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.StoreOrderExportOutPort;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link StoreOrderExportOutPort} with S3 storage disabled.
 */
@Slf4j
@WebAdapter
@ConditionalOnProperty(name = "service.aws.s3.enabled", havingValue = "false", matchIfMissing = true)
public class DoNotStoreOrderExportAdapter implements StoreOrderExportOutPort {

    @Override
    public void store(final Order order) {

        log.info("S3 export disabled for order {}, export will not be stored.", order.getOrderNumber());
    }

}
