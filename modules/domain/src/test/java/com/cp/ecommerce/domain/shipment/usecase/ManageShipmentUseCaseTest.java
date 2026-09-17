package com.cp.ecommerce.domain.shipment.usecase;

import java.util.List;

import com.cp.ecommerce.adapter.common.exception.BusinessRuleException;
import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;
import com.cp.ecommerce.domain.shipment.port.outgoing.FindShipmentOutPort;
import com.cp.ecommerce.domain.shipment.port.outgoing.FindShipmentsOutPort;
import com.cp.ecommerce.domain.shipment.port.outgoing.GenerateShipmentNumberOutPort;
import com.cp.ecommerce.domain.shipment.port.outgoing.GenerateTrackingNumberOutPort;
import com.cp.ecommerce.domain.shipment.port.outgoing.SaveShipmentOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link ManageShipmentUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class ManageShipmentUseCaseTest {

    @InjectMocks
    private transient ManageShipmentUseCase manageShipmentUseCase;

    @Mock
    private transient SaveShipmentOutPort saveShipmentOutPort;

    @Mock
    private transient FindShipmentOutPort findShipmentOutPort;

    @Mock
    private transient FindShipmentsOutPort findShipmentsOutPort;

    @Mock
    private transient GenerateShipmentNumberOutPort generateShipmentNumberOutPort;

    @Mock
    private transient GenerateTrackingNumberOutPort generateTrackingNumberOutPort;

    @Test
    void shouldCreatePendingShipmentWithGeneratedIdentifiers() {

        final ArgumentCaptor<Shipment> captor = ArgumentCaptor.forClass(Shipment.class);
        given(generateShipmentNumberOutPort.generate()).willReturn(TestDomainObjectFactory.TEST_SHIPMENT_NUMBER);
        given(generateTrackingNumberOutPort.generate("DHL")).willReturn(TestDomainObjectFactory.TEST_TRACKING_NUMBER);
        given(findShipmentOutPort.findByOrderNumber(TestDomainObjectFactory.TEST_ORDER_NUMBER)).willReturn(null);
        given(saveShipmentOutPort.save(captor.capture())).willAnswer(invocation -> invocation.getArgument(0));

        final Shipment result = manageShipmentUseCase.createShipment(TestDomainObjectFactory.TEST_ORDER_NUMBER, "DHL");

        assertThat(result.getShipmentNumber()).isEqualTo(TestDomainObjectFactory.TEST_SHIPMENT_NUMBER);
        assertThat(result.getTrackingNumber()).isEqualTo(TestDomainObjectFactory.TEST_TRACKING_NUMBER);
        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.PENDING);
        assertThat(captor.getValue().getCreatedDate()).isNotNull();
    }

    @Test
    void shouldRejectDuplicateShipmentForOrder() {

        given(findShipmentOutPort.findByOrderNumber(TestDomainObjectFactory.TEST_ORDER_NUMBER))
                .willReturn(TestDomainObjectFactory.validShipment());

        assertThatThrownBy(() -> manageShipmentUseCase.createShipment(TestDomainObjectFactory.TEST_ORDER_NUMBER, "DHL"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(TestDomainObjectFactory.TEST_ORDER_NUMBER);
    }

    @Test
    void shouldAdvancePendingShipmentToDispatched() {

        given(findShipmentOutPort.findByShipmentNumber(TestDomainObjectFactory.TEST_SHIPMENT_NUMBER))
                .willReturn(TestDomainObjectFactory.validShipment());
        given(saveShipmentOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Shipment result = manageShipmentUseCase.advanceShipmentStatus(TestDomainObjectFactory.TEST_SHIPMENT_NUMBER);

        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.DISPATCHED);
        assertThat(result.getDispatchedDate()).isNotNull();
        assertThat(result.getEstimatedDeliveryDate()).isAfter(result.getDispatchedDate());
    }

    @Test
    void shouldAdvanceDispatchedShipmentToInTransit() {

        final Shipment dispatchedShipment = TestDomainObjectFactory.validDispatchedShipment();
        given(findShipmentOutPort.findByShipmentNumber(TestDomainObjectFactory.TEST_SHIPMENT_NUMBER))
                .willReturn(dispatchedShipment);
        given(saveShipmentOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Shipment result = manageShipmentUseCase.advanceShipmentStatus(TestDomainObjectFactory.TEST_SHIPMENT_NUMBER);

        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(result.getDispatchedDate()).isEqualTo(dispatchedShipment.getDispatchedDate());
        assertThat(result.getDeliveredDate()).isNull();
    }

    @Test
    void shouldAdvanceInTransitShipmentToDelivered() {

        given(findShipmentOutPort.findByShipmentNumber(TestDomainObjectFactory.TEST_SHIPMENT_NUMBER))
                .willReturn(TestDomainObjectFactory.validInTransitShipment());
        given(saveShipmentOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Shipment result = manageShipmentUseCase.advanceShipmentStatus(TestDomainObjectFactory.TEST_SHIPMENT_NUMBER);

        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(result.getDeliveredDate()).isNotNull();
    }

    @Test
    void shouldRejectAdvanceOfDeliveredShipment() {

        given(findShipmentOutPort.findByShipmentNumber(TestDomainObjectFactory.TEST_SHIPMENT_NUMBER))
                .willReturn(TestDomainObjectFactory.validDeliveredShipment());

        assertThatThrownBy(() -> manageShipmentUseCase.advanceShipmentStatus(TestDomainObjectFactory.TEST_SHIPMENT_NUMBER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("DELIVERED");
    }

    @Test
    void shouldReturnNullWhenShipmentDoesNotExist() {

        given(findShipmentOutPort.findByShipmentNumber(TestDomainObjectFactory.TEST_SHIPMENT_NUMBER)).willReturn(null);

        assertThat(manageShipmentUseCase.advanceShipmentStatus(TestDomainObjectFactory.TEST_SHIPMENT_NUMBER)).isNull();
    }

    @Test
    void shouldListShipments() {

        given(findShipmentsOutPort.findAll()).willReturn(List.of(TestDomainObjectFactory.validShipment()));

        assertThat(manageShipmentUseCase.listShipments()).hasSize(1);
    }

    @Test
    void shouldListShipmentsForOrder() {

        given(findShipmentsOutPort.findByOrderNumber(TestDomainObjectFactory.TEST_ORDER_NUMBER))
                .willReturn(List.of(TestDomainObjectFactory.validShipment()));

        assertThat(manageShipmentUseCase.listShipmentsForOrder(TestDomainObjectFactory.TEST_ORDER_NUMBER)).hasSize(1);
    }

    @Test
    void shouldListShipmentsByStatus() {

        given(findShipmentsOutPort.findByStatus(ShipmentStatus.PENDING))
                .willReturn(List.of(TestDomainObjectFactory.validShipment()));

        assertThat(manageShipmentUseCase.listShipmentsByStatus(ShipmentStatus.PENDING)).hasSize(1);
    }

    @Test
    void shouldDelegateSingleRead() {

        final Shipment shipment = TestDomainObjectFactory.validShipment();
        given(findShipmentOutPort.findByShipmentNumber(TestDomainObjectFactory.TEST_SHIPMENT_NUMBER)).willReturn(shipment);

        assertThat(manageShipmentUseCase.getShipment(TestDomainObjectFactory.TEST_SHIPMENT_NUMBER)).isSameAs(shipment);
        verify(findShipmentOutPort).findByShipmentNumber(TestDomainObjectFactory.TEST_SHIPMENT_NUMBER);
    }

}
