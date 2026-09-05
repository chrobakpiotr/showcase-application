import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import {
  OrderDetailsModel,
  OrderPageModel,
} from '@app/order/order-details.model';
import { OrderListComponent } from '@app/order-list/order-list.component';
import { OrderService } from '@app/order/order.service';

describe('OrderListComponent', () => {
  let fixture: ComponentFixture<OrderListComponent>;
  let component: OrderListComponent;
  let orderServiceSpy: jasmine.SpyObj<OrderService>;

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

  const page: OrderPageModel = {
    _embedded: { orderDetailsResourceList: [orderSummary] },
    page: { size: 10, totalElements: 1, totalPages: 1, number: 0 },
  };

  function setup(): void {
    orderServiceSpy = jasmine.createSpyObj('OrderService', [
      'listOrders',
      'findOrder',
      'cancelOrder',
    ]);
    orderServiceSpy.listOrders.and.returnValue(of(page));

    TestBed.configureTestingModule({
      imports: [OrderListComponent],
      providers: [{ provide: OrderService, useValue: orderServiceSpy }],
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
    orderServiceSpy.listOrders.and.returnValue(
      of({ page: { size: 10, totalElements: 0, totalPages: 0, number: 0 } })
    );
    TestBed.configureTestingModule({
      imports: [OrderListComponent],
      providers: [{ provide: OrderService, useValue: orderServiceSpy }],
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
    orderServiceSpy.listOrders.and.returnValue(
      throwError(() => new Error('failed'))
    );
    TestBed.configureTestingModule({
      imports: [OrderListComponent],
      providers: [{ provide: OrderService, useValue: orderServiceSpy }],
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

  it('sets an error message when loading order details fails', () => {
    setup();
    orderServiceSpy.findOrder.and.returnValue(
      throwError(() => new Error('failed'))
    );
    component.selectOrder('ORDER-1');

    expect(component.errorMessage()).toBe('Failed to load order details.');
  });

  it('closes the details view', () => {
    setup();
    orderServiceSpy.findOrder.and.returnValue(of(orderSummary));
    component.selectOrder('ORDER-1');
    component.closeDetails();

    expect(component.selectedOrder()).toBeNull();
  });

  it('cancels an order and refreshes the list', () => {
    setup();
    const cancelled = { ...orderSummary, status: 'CANCELLED', _links: {} };
    orderServiceSpy.cancelOrder.and.returnValue(of(cancelled));
    component.cancelOrder('ORDER-1');

    expect(orderServiceSpy.cancelOrder).toHaveBeenCalledWith('ORDER-1');
    expect(component.selectedOrder()).toEqual(cancelled);
    expect(orderServiceSpy.listOrders).toHaveBeenCalledTimes(2);
  });

  it('sets an error message when cancelling fails', () => {
    setup();
    orderServiceSpy.cancelOrder.and.returnValue(
      throwError(() => new Error('failed'))
    );
    component.cancelOrder('ORDER-1');

    expect(component.errorMessage()).toBe('Failed to cancel order.');
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
});
