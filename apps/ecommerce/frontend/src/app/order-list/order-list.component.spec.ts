import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';

import {
  OrderDetailsModel,
  OrderPageModel,
} from '@app/order/order-details.model';
import { OrderListComponent } from '@app/order-list/order-list.component';
import { OrderService } from '@app/order/order.service';
import { AuthService } from '@app/auth/auth.service';
import { ReturnsService } from '@app/returns/returns.service';
import { ReturnCollectionModel, ReturnModel } from '@app/returns/return.model';
import {
  ShipmentCollectionModel,
  ShipmentModel,
} from '@app/shipments/shipment.model';
import { ShipmentsService } from '@app/shipments/shipments.service';

describe('OrderListComponent', () => {
  let fixture: ComponentFixture<OrderListComponent>;
  let component: OrderListComponent;
  let orderServiceSpy: jasmine.SpyObj<OrderService>;
  let returnsServiceSpy: jasmine.SpyObj<ReturnsService>;
  let shipmentsServiceSpy: jasmine.SpyObj<ShipmentsService>;

  const orderSummary: OrderDetailsModel = {
    orderNumber: 'ORDER-1',
    status: 'CONFIRMED',
    created: '2024-03-15T10:30:00.000Z',
    remarks: '',
    customer: {
      fullName: 'Jane Doe',
      email: 'jane.doe@example.com',
      phone: '',
      street: 'Main Street 1',
      postalCode: '12-345',
      city: 'Warsaw',
      countryCode: 'PL',
    },
    items: [
      {
        sku: 'SKU-1',
        productName: 'Mouse',
        unitPrice: 29.99,
        quantity: 2,
        subtotal: 59.98,
      },
    ],
    subtotal: 59.98,
    couponCode: null,
    discountAmount: 0,
    total: 59.98,
    paymentMethod: 'CARD',
    payment: {
      status: 'CAPTURED',
      method: 'CARD',
      amount: 59.98,
      gatewayReference: 'mock-gw-1',
    },
    _links: { cancel: { href: '/api/order/ORDER-1/cancel' } },
  };

  const multiItemOrderSummary: OrderDetailsModel = {
    ...orderSummary,
    items: [
      orderSummary.items[0],
      {
        sku: 'SKU-2',
        productName: 'Keyboard',
        unitPrice: 49.99,
        quantity: 1,
        subtotal: 49.99,
      },
    ],
    subtotal: 109.97,
    total: 109.97,
    payment: {
      status: 'CAPTURED',
      method: 'CARD',
      amount: 109.97,
      gatewayReference: 'mock-gw-2',
    },
  };

  const page: OrderPageModel = {
    _embedded: { orderDetailsResourceList: [orderSummary] },
    page: { size: 10, totalElements: 1, totalPages: 1, number: 0 },
  };

  const returnsPage: ReturnCollectionModel = {
    _embedded: {
      returnRequestResourceList: [
        {
          returnNumber: 'RETURN-1',
          orderNumber: 'ORDER-1',
          sku: 'SKU-1',
          quantity: 1,
          reason: 'Damaged',
          status: 'REQUESTED',
          requestedDate: '2024-03-15T10:30:00.000Z',
          decidedDate: null,
          refundAmount: 29.99,
        },
      ],
    },
  };

  const shipmentsPage: ShipmentCollectionModel = {
    _embedded: {
      shipmentResourceList: [
        {
          shipmentNumber: 'SHIP-1',
          orderNumber: 'ORDER-1',
          carrier: 'DHL',
          trackingNumber: 'DHL-TRACK-1',
          status: 'PENDING',
          dispatchedDate: null,
          estimatedDeliveryDate: null,
          deliveredDate: null,
          createdDate: '2024-03-15T10:30:00.000Z',
          _links: {
            'advance-status': { href: '/api/shipments/SHIP-1/advance' },
          },
        },
      ],
    },
  };

  function setup(roles: string[] = []): void {
    orderServiceSpy = jasmine.createSpyObj('OrderService', [
      'listOrders',
      'findOrder',
      'cancelOrder',
    ]);
    returnsServiceSpy = jasmine.createSpyObj('ReturnsService', [
      'listReturnsForOrder',
      'requestReturn',
    ]);
    shipmentsServiceSpy = jasmine.createSpyObj('ShipmentsService', [
      'listShipmentsForOrder',
      'createShipment',
      'advanceShipmentStatus',
    ]);
    orderServiceSpy.listOrders.and.returnValue(of(page));
    returnsServiceSpy.listReturnsForOrder.and.returnValue(of(returnsPage));
    shipmentsServiceSpy.listShipmentsForOrder.and.returnValue(
      of(shipmentsPage)
    );

    TestBed.configureTestingModule({
      imports: [OrderListComponent],
      providers: [
        { provide: OrderService, useValue: orderServiceSpy },
        { provide: ReturnsService, useValue: returnsServiceSpy },
        { provide: ShipmentsService, useValue: shipmentsServiceSpy },
        { provide: AuthService, useValue: { roles: signal(roles) } },
      ],
    });

    fixture = TestBed.createComponent(OrderListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should create the component', () => {
    setup();
    expect(component).toBeTruthy();
  });

  it('loads orders on init', () => {
    setup();
    expect(component.orders()).toEqual([orderSummary]);
    expect(component.totalPages()).toBe(1);
    expect(component.loading()).toBeFalse();
  });

  it('defaults to an empty list when there is no embedded content', () => {
    orderServiceSpy = jasmine.createSpyObj('OrderService', [
      'listOrders',
      'findOrder',
      'cancelOrder',
    ]);
    returnsServiceSpy = jasmine.createSpyObj('ReturnsService', [
      'listReturnsForOrder',
      'requestReturn',
    ]);
    shipmentsServiceSpy = jasmine.createSpyObj('ShipmentsService', [
      'listShipmentsForOrder',
      'createShipment',
      'advanceShipmentStatus',
    ]);
    orderServiceSpy.listOrders.and.returnValue(
      of({ page: { size: 10, totalElements: 0, totalPages: 0, number: 0 } })
    );
    returnsServiceSpy.listReturnsForOrder.and.returnValue(
      of({ _embedded: { returnRequestResourceList: [] } })
    );
    shipmentsServiceSpy.listShipmentsForOrder.and.returnValue(
      of({ _embedded: { shipmentResourceList: [] } })
    );
    TestBed.configureTestingModule({
      imports: [OrderListComponent],
      providers: [
        { provide: OrderService, useValue: orderServiceSpy },
        { provide: ReturnsService, useValue: returnsServiceSpy },
        { provide: ShipmentsService, useValue: shipmentsServiceSpy },
        { provide: AuthService, useValue: { roles: signal([]) } },
      ],
    });
    fixture = TestBed.createComponent(OrderListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.orders()).toEqual([]);
  });

  it('sets an error message when loading orders fails', () => {
    orderServiceSpy = jasmine.createSpyObj('OrderService', [
      'listOrders',
      'findOrder',
      'cancelOrder',
    ]);
    returnsServiceSpy = jasmine.createSpyObj('ReturnsService', [
      'listReturnsForOrder',
      'requestReturn',
    ]);
    shipmentsServiceSpy = jasmine.createSpyObj('ShipmentsService', [
      'listShipmentsForOrder',
      'createShipment',
      'advanceShipmentStatus',
    ]);
    orderServiceSpy.listOrders.and.returnValue(
      throwError(() => new Error('failed'))
    );
    TestBed.configureTestingModule({
      imports: [OrderListComponent],
      providers: [
        { provide: OrderService, useValue: orderServiceSpy },
        { provide: ReturnsService, useValue: returnsServiceSpy },
        { provide: ShipmentsService, useValue: shipmentsServiceSpy },
        { provide: AuthService, useValue: { roles: signal([]) } },
      ],
    });
    fixture = TestBed.createComponent(OrderListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.errorMessage()).toBe('Failed to load orders.');
    expect(component.loading()).toBeFalse();
  });

  it('selects an order and shows its details', () => {
    setup();
    orderServiceSpy.findOrder.and.returnValue(of(orderSummary));
    component.selectOrder('ORDER-1');

    expect(orderServiceSpy.findOrder).toHaveBeenCalledWith('ORDER-1');
    expect(component.selectedOrder()).toEqual(orderSummary);
  });

  it('loads returns for a selected order when the user can read returns', () => {
    setup(['RETURN_READ']);
    orderServiceSpy.findOrder.and.returnValue(of(orderSummary));

    component.selectOrder('ORDER-1');

    expect(returnsServiceSpy.listReturnsForOrder).toHaveBeenCalledWith(
      'ORDER-1'
    );
    expect(component.orderReturns()).toEqual(
      returnsPage._embedded!.returnRequestResourceList
    );
  });

  it('loads shipments for a selected order when the user can read shipments', () => {
    setup(['SHIPMENT_READ']);
    orderServiceSpy.findOrder.and.returnValue(of(orderSummary));

    component.selectOrder('ORDER-1');

    expect(shipmentsServiceSpy.listShipmentsForOrder).toHaveBeenCalledWith(
      'ORDER-1'
    );
    expect(component.orderShipments()).toEqual(
      shipmentsPage._embedded!.shipmentResourceList ?? []
    );
  });

  it('defaults order returns to an empty list when the embedded collection is missing', () => {
    setup(['RETURN_READ']);
    orderServiceSpy.findOrder.and.returnValue(of(orderSummary));
    returnsServiceSpy.listReturnsForOrder.and.returnValue(of({}));

    component.selectOrder('ORDER-1');

    expect(component.orderReturns()).toEqual([]);
  });

  it('defaults order shipments to an empty list when the embedded collection is missing', () => {
    setup(['SHIPMENT_READ']);
    orderServiceSpy.findOrder.and.returnValue(of(orderSummary));
    shipmentsServiceSpy.listShipmentsForOrder.and.returnValue(of({}));

    component.selectOrder('ORDER-1');

    expect(component.orderShipments()).toEqual([]);
  });

  it('sets an error message when loading order details fails', () => {
    setup();
    orderServiceSpy.findOrder.and.returnValue(
      throwError(() => new Error('failed'))
    );
    component.selectOrder('ORDER-1');

    expect(component.errorMessage()).toBe('Failed to load order details.');
  });

  it('sets an error message when loading order returns fails', () => {
    setup(['RETURN_READ']);
    orderServiceSpy.findOrder.and.returnValue(of(orderSummary));
    returnsServiceSpy.listReturnsForOrder.and.returnValue(
      throwError(() => new Error('failed'))
    );

    component.selectOrder('ORDER-1');

    expect(component.returnErrorMessage()).toBe(
      'Failed to load return requests.'
    );
  });

  it('sets an error message when loading shipments fails', () => {
    setup(['SHIPMENT_READ']);
    orderServiceSpy.findOrder.and.returnValue(of(orderSummary));
    shipmentsServiceSpy.listShipmentsForOrder.and.returnValue(
      throwError(() => new Error('failed'))
    );

    component.selectOrder('ORDER-1');

    expect(component.shipmentErrorMessage()).toBe('Failed to load shipments.');
  });

  it('closes the details view', () => {
    setup();
    orderServiceSpy.findOrder.and.returnValue(of(orderSummary));
    component.selectOrder('ORDER-1');
    component.closeDetails();

    expect(component.selectedOrder()).toBeNull();
    expect(component.orderReturns()).toEqual([]);
    expect(component.orderShipments()).toEqual([]);
  });

  it('cancels an order and refreshes the list', () => {
    setup(['RETURN_READ', 'SHIPMENT_READ']);
    const cancelled = { ...orderSummary, status: 'CANCELLED', _links: {} };
    orderServiceSpy.cancelOrder.and.returnValue(of(cancelled));
    component.cancelOrder('ORDER-1');

    expect(orderServiceSpy.cancelOrder).toHaveBeenCalledWith('ORDER-1');
    expect(component.selectedOrder()).toEqual(cancelled);
    expect(orderServiceSpy.listOrders).toHaveBeenCalledTimes(2);
    expect(returnsServiceSpy.listReturnsForOrder).toHaveBeenCalledWith(
      'ORDER-1'
    );
    expect(shipmentsServiceSpy.listShipmentsForOrder).toHaveBeenCalledWith(
      'ORDER-1'
    );
  });

  it('sets an error message when cancelling fails', () => {
    setup();
    orderServiceSpy.cancelOrder.and.returnValue(
      throwError(() => new Error('failed'))
    );
    component.cancelOrder('ORDER-1');

    expect(component.errorMessage()).toBe('Failed to cancel order.');
  });

  it('creates a return request', () => {
    setup(['RETURN_READ', 'RETURN_WRITE']);
    orderServiceSpy.findOrder.and.returnValue(of(orderSummary));
    returnsServiceSpy.requestReturn.and.returnValue(
      of(returnsPage._embedded!.returnRequestResourceList[0])
    );
    component.selectOrder('ORDER-1');
    component.returnForm.setValue({
      sku: 'SKU-1',
      quantity: 1,
      reason: 'Damaged',
    });

    component.requestReturn();

    expect(returnsServiceSpy.requestReturn).toHaveBeenCalledWith({
      orderNumber: 'ORDER-1',
      sku: 'SKU-1',
      quantity: 1,
      reason: 'Damaged',
    });
    expect(component.returnSuccessMessage()).toBe('Return request created.');
  });

  it('creates a return request with a defaulted quantity when the control value is null', () => {
    setup(['RETURN_READ', 'RETURN_WRITE']);
    orderServiceSpy.findOrder.and.returnValue(of(orderSummary));
    returnsServiceSpy.requestReturn.and.returnValue(
      of(returnsPage._embedded!.returnRequestResourceList[0])
    );
    component.selectOrder('ORDER-1');
    component.returnForm.controls.quantity.setValue(null);
    component.returnForm.controls.reason.setValue('Damaged');
    component.returnForm.controls.sku.setValue('SKU-1');
    component.returnForm.controls.quantity.clearValidators();
    component.returnForm.controls.quantity.updateValueAndValidity();

    component.requestReturn();

    expect(returnsServiceSpy.requestReturn).toHaveBeenCalledWith({
      orderNumber: 'ORDER-1',
      sku: 'SKU-1',
      quantity: 0,
      reason: 'Damaged',
    });
  });

  it('does not create a return request when the form is invalid', () => {
    setup(['RETURN_READ', 'RETURN_WRITE']);
    component.returnForm.setValue({ sku: '', quantity: null, reason: '' });

    component.requestReturn();

    expect(returnsServiceSpy.requestReturn).not.toHaveBeenCalled();
  });

  it('does not create a return request when no order is selected', () => {
    setup(['RETURN_READ', 'RETURN_WRITE']);
    component.returnForm.setValue({
      sku: 'SKU-1',
      quantity: 1,
      reason: 'Damaged',
    });

    component.requestReturn();

    expect(returnsServiceSpy.requestReturn).not.toHaveBeenCalled();
  });

  it('sets an error message when requested quantity exceeds remaining returnable quantity', () => {
    setup(['RETURN_READ', 'RETURN_WRITE']);
    orderServiceSpy.findOrder.and.returnValue(of(orderSummary));
    component.selectOrder('ORDER-1');
    component.returnForm.setValue({
      sku: 'SKU-1',
      quantity: 2,
      reason: 'Damaged',
    });

    component.requestReturn();

    expect(component.returnErrorMessage()).toBe(
      'Requested quantity exceeds remaining returnable quantity.'
    );
  });

  it('sets an error message when creating a return request fails', () => {
    setup(['RETURN_READ', 'RETURN_WRITE']);
    orderServiceSpy.findOrder.and.returnValue(of(orderSummary));
    returnsServiceSpy.requestReturn.and.returnValue(
      throwError(() => new Error('failed'))
    );
    component.selectOrder('ORDER-1');
    component.orderReturns.set([]);
    component.returnForm.setValue({
      sku: 'SKU-1',
      quantity: 1,
      reason: 'Damaged',
    });

    component.requestReturn();

    expect(component.returnErrorMessage()).toBe(
      'Failed to create return request.'
    );
  });

  it('calculates remaining quantity excluding rejected returns', () => {
    setup(['RETURN_READ']);
    orderServiceSpy.findOrder.and.returnValue(of(orderSummary));
    component.selectOrder('ORDER-1');
    component.orderReturns.set([
      {
        ...(returnsPage._embedded!.returnRequestResourceList[0] as ReturnModel),
        quantity: 1,
        status: 'REJECTED',
      },
    ]);

    expect(component.remainingQuantity('SKU-1')).toBe(2);
  });

  it('reports zero remaining quantity for an unknown sku', () => {
    setup(['RETURN_READ']);
    expect(component.remainingQuantity('UNKNOWN')).toBe(0);
  });

  it('keeps order returns empty when the user cannot read returns', () => {
    setup();
    orderServiceSpy.findOrder.and.returnValue(of(orderSummary));

    component.selectOrder('ORDER-1');

    expect(returnsServiceSpy.listReturnsForOrder).not.toHaveBeenCalled();
    expect(component.orderReturns()).toEqual([]);
  });

  it('keeps order shipments empty when the user cannot read shipments', () => {
    setup();
    orderServiceSpy.findOrder.and.returnValue(of(orderSummary));

    component.selectOrder('ORDER-1');

    expect(shipmentsServiceSpy.listShipmentsForOrder).not.toHaveBeenCalled();
    expect(component.orderShipments()).toEqual([]);
  });

  it('initializes the return form with the first order item when no item is returnable yet', () => {
    setup(['RETURN_READ', 'RETURN_WRITE']);
    orderServiceSpy.findOrder.and.returnValue(of(orderSummary));

    component.selectOrder('ORDER-1');

    expect(component.returnForm.controls.sku.value).toBe('SKU-1');
  });

  it('switches the selected sku to another returnable item after loading returns', () => {
    setup(['RETURN_READ', 'RETURN_WRITE']);
    orderServiceSpy.findOrder.and.returnValue(of(multiItemOrderSummary));
    returnsServiceSpy.listReturnsForOrder.and.returnValue(
      of({
        _embedded: {
          returnRequestResourceList: [
            {
              ...returnsPage._embedded!.returnRequestResourceList[0],
              quantity: 2,
            },
          ],
        },
      })
    );

    component.selectOrder('ORDER-1');

    expect(component.hasReturnableItems()).toBeTrue();
    expect(component.returnForm.controls.sku.value).toBe('SKU-2');
  });

  it('reports whether the user can manage returns', () => {
    setup(['RETURN_READ', 'RETURN_WRITE']);
    expect(component.canReadReturns).toBeTrue();
    expect(component.canWriteReturns).toBeTrue();
  });

  it('reports whether the user can manage shipments', () => {
    setup(['SHIPMENT_READ', 'SHIPMENT_WRITE']);
    expect(component.canReadShipments).toBeTrue();
    expect(component.canWriteShipments).toBeTrue();
  });

  it('creates a shipment', () => {
    setup(['SHIPMENT_READ', 'SHIPMENT_WRITE']);
    orderServiceSpy.findOrder.and.returnValue(of(orderSummary));
    shipmentsServiceSpy.createShipment.and.returnValue(
      of(shipmentsPage._embedded!.shipmentResourceList![0])
    );
    shipmentsServiceSpy.listShipmentsForOrder.and.returnValue(
      of({ _embedded: { shipmentResourceList: [] } })
    );
    component.selectOrder('ORDER-1');
    component.shipmentForm.setValue({ carrier: 'DHL' });

    component.createShipment();

    expect(shipmentsServiceSpy.createShipment).toHaveBeenCalledWith({
      orderNumber: 'ORDER-1',
      carrier: 'DHL',
    });
    expect(component.shipmentSuccessMessage()).toBe('Shipment created.');
  });

  it('does not create a shipment when the form is invalid', () => {
    setup(['SHIPMENT_READ', 'SHIPMENT_WRITE']);
    component.shipmentForm.setValue({ carrier: '' });

    component.createShipment();

    expect(shipmentsServiceSpy.createShipment).not.toHaveBeenCalled();
  });

  it('does not create a shipment when no order is selected', () => {
    setup(['SHIPMENT_READ', 'SHIPMENT_WRITE']);
    component.shipmentForm.setValue({ carrier: 'DHL' });

    component.createShipment();

    expect(shipmentsServiceSpy.createShipment).not.toHaveBeenCalled();
  });

  it('sets an error message when creating a shipment fails', () => {
    setup(['SHIPMENT_READ', 'SHIPMENT_WRITE']);
    orderServiceSpy.findOrder.and.returnValue(of(orderSummary));
    shipmentsServiceSpy.createShipment.and.returnValue(
      throwError(() => new Error('failed'))
    );
    shipmentsServiceSpy.listShipmentsForOrder.and.returnValue(
      of({ _embedded: { shipmentResourceList: [] } })
    );
    component.selectOrder('ORDER-1');
    component.orderShipments.set([]);
    component.shipmentForm.setValue({ carrier: 'DHL' });

    component.createShipment();

    expect(component.shipmentErrorMessage()).toBe('Failed to create shipment.');
  });

  it('advances a shipment status', () => {
    setup(['SHIPMENT_READ', 'SHIPMENT_WRITE']);
    orderServiceSpy.findOrder.and.returnValue(of(orderSummary));
    shipmentsServiceSpy.advanceShipmentStatus.and.returnValue(
      of(shipmentsPage._embedded!.shipmentResourceList![0] as ShipmentModel)
    );
    component.selectOrder('ORDER-1');

    component.advanceShipmentStatus('SHIP-1');

    expect(shipmentsServiceSpy.advanceShipmentStatus).toHaveBeenCalled();
    const args =
      shipmentsServiceSpy.advanceShipmentStatus.calls.mostRecent().args;
    expect(args[0]).toBe('SHIP-1');
    expect(args[1]).toBeTruthy();
    expect(args[2]).toBe('PENDING');
    expect(component.shipmentSuccessMessage()).toBe(
      'Shipment status advanced.'
    );
  });

  it('reuses the same pending shipment operation identity after a failed order-detail advance', () => {
    setup(['SHIPMENT_READ', 'SHIPMENT_WRITE']);
    orderServiceSpy.findOrder.and.returnValue(of(orderSummary));
    shipmentsServiceSpy.advanceShipmentStatus.and.returnValue(
      throwError(() => new Error('lost response'))
    );
    component.selectOrder('ORDER-1');

    component.advanceShipmentStatus('SHIP-1');
    const firstArgs =
      shipmentsServiceSpy.advanceShipmentStatus.calls.mostRecent().args;

    component.advanceShipmentStatus('SHIP-1');
    const secondArgs =
      shipmentsServiceSpy.advanceShipmentStatus.calls.mostRecent().args;

    expect(shipmentsServiceSpy.advanceShipmentStatus).toHaveBeenCalledTimes(2);
    expect(firstArgs[0]).toBe('SHIP-1');
    expect(firstArgs[1]).toBeTruthy();
    expect(firstArgs[2]).toBe('PENDING');
    expect(secondArgs[1]).toBe(firstArgs[1]);
    expect(secondArgs[2]).toBe(firstArgs[2]);
  });

  it('sets an error message when advancing a shipment fails', () => {
    setup(['SHIPMENT_READ', 'SHIPMENT_WRITE']);
    orderServiceSpy.findOrder.and.returnValue(of(orderSummary));
    shipmentsServiceSpy.advanceShipmentStatus.and.returnValue(
      throwError(() => new Error('failed'))
    );
    component.selectOrder('ORDER-1');

    component.advanceShipmentStatus('SHIP-1');

    expect(component.shipmentErrorMessage()).toBe(
      'Failed to advance shipment status.'
    );
  });

  it('does not advance a shipment when no order is selected', () => {
    setup(['SHIPMENT_READ', 'SHIPMENT_WRITE']);

    component.advanceShipmentStatus('SHIP-1');

    expect(shipmentsServiceSpy.advanceShipmentStatus).not.toHaveBeenCalled();
  });

  it('reports no returnable items when no order is selected', () => {
    setup(['RETURN_READ']);
    expect(component.hasReturnableItems()).toBeFalse();
  });

  it('initializes the return form with an empty sku when the selected order has no items', () => {
    setup(['RETURN_READ', 'RETURN_WRITE']);
    orderServiceSpy.findOrder.and.returnValue(
      of({
        ...orderSummary,
        items: [],
      })
    );

    component.selectOrder('ORDER-1');

    expect(component.returnForm.controls.sku.value).toBe('');
  });

  it('advances to the next page', () => {
    setup();
    component.totalPages.set(3);
    component.page.set(0);

    component.nextPage();

    expect(component.page()).toBe(1);
    expect(orderServiceSpy.listOrders).toHaveBeenCalledWith(1, 10);
  });

  it('does not advance past the last page', () => {
    setup();
    component.totalPages.set(1);
    component.page.set(0);

    component.nextPage();

    expect(component.page()).toBe(0);
  });

  it('goes back to the previous page', () => {
    setup();
    component.page.set(1);

    component.previousPage();

    expect(component.page()).toBe(0);
  });

  it('does not go before the first page', () => {
    setup();
    component.page.set(0);

    component.previousPage();

    expect(component.page()).toBe(0);
  });

  it('does not advance an unknown shipment for a selected order', () => {
    setup(['SHIPMENT_WRITE']);
    orderServiceSpy.findOrder.and.returnValue(of(orderSummary));
    component.selectOrder('ORDER-1');

    component.advanceShipmentStatus('SHIP-404');

    expect(shipmentsServiceSpy.advanceShipmentStatus).not.toHaveBeenCalled();
  });
});
