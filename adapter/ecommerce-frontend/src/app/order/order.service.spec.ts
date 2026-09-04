import { TestBed } from '@angular/core/testing';

import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { OrderService } from '@app/order/order.service';
import { OrderLineItemRequestModel } from '@app/order/order-line-item-request.model';
import { OrderResponseModel } from '@app/order/order-response.model';
import { CustomerRequestModel } from '@app/order/customer-request.model';
import { environment } from '@environments/environment';
import {
  provideHttpClient,
  withInterceptorsFromDi,
  withXhr,
} from '@angular/common/http';

const CUSTOMER: CustomerRequestModel = {
  fullName: 'Jane Doe',
  email: 'jane.doe@example.com',
  phone: '+1 555 123 4567',
  street: 'Main Street 1',
  postalCode: '12-345',
  city: 'Warsaw',
  countryCode: 'PL',
};

const ITEMS: OrderLineItemRequestModel[] = [
  {
    sku: 'SKU-1234',
    productName: 'Wireless Mouse',
    unitPrice: 29.99,
    quantity: 2,
  },
];

describe('OrderService', () => {
  let orderService: OrderService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [],
      providers: [
        OrderService,
        provideHttpClient(withXhr(), withInterceptorsFromDi()),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
    orderService = TestBed.inject(OrderService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should be created', () => {
    expect(orderService).toBeTruthy();
  });

  it('should call backend order service and get response', () => {
    //given
    const orderResponse = {
      orderNumber: '20220915123015',
    } as OrderResponseModel;

    //when
    orderService
      .placeOrder('test remarks', CUSTOMER, ITEMS, 'CARD')
      .subscribe((data) => expect(data).toBe(orderResponse));

    //then
    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/order`
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body.customer).toEqual(CUSTOMER);
    expect(req.request.body.items).toEqual(ITEMS);
    expect(req.request.body.paymentMethod).toBe('CARD');

    req.flush(orderResponse);
  });
});
