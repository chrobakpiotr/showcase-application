package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.StoreOrderExportOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

/**
 * Tests for {@link ExportOrderUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class ExportOrderUseCaseTest {

    @Mock
    private transient StoreOrderExportOutPort storeOrderExportOutPort;

    @InjectMocks
    private transient ExportOrderUseCase exportOrderUseCase;

    @Test
    void shouldDelegateOrderExport() {

        final Order order = TestDomainObjectFactory.validOrder();

        exportOrderUseCase.exportOrder(order);

        verify(storeOrderExportOutPort).store(order);
    }

}
