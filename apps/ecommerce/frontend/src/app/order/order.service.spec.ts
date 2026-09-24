import { TestBed } from '@angular/core/testing';

import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { OrderService } from '@app/order/order.service';
import {
  OrderDetailsModel,
  OrderPageModel,
} from '@app/order/order-details.model';
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
      .placeOrder(
        {
          remarks: 'test remarks',
          customer: CUSTOMER,
          items: ITEMS,
          paymentMethod: 'CARD',
          couponCode: 'SAVE10',
          created: new Date(0),
        },
        'attempt-1'
      )
      .subscribe((data) => expect(data).toBe(orderResponse));

    //then
    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/order`
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Idempotency-Key')).toBe('attempt-1');
    expect(req.request.body.created).toEqual(new Date(0));
    expect(req.request.body.customer).toEqual(CUSTOMER);
    expect(req.request.body.items).toEqual(ITEMS);
    expect(req.request.body.paymentMethod).toBe('CARD');
    expect(req.request.body.couponCode).toBe('SAVE10');

    req.flush(orderResponse);
  });

  it('omits coupon code when not provided', () => {
    const orderResponse = {
      orderNumber: '20220915123015',
    } as OrderResponseModel;

    orderService
      .placeOrder(
        {
          remarks: 'test remarks',
          customer: CUSTOMER,
          items: ITEMS,
          paymentMethod: 'CARD',
          couponCode: null,
          created: new Date(0),
        },
        'attempt-2'
      )
      .subscribe((data) => expect(data).toBe(orderResponse));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/order`
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body.couponCode).toBeNull();

    req.flush(orderResponse);
  });

  it('lists orders with page and size params', () => {
    const page: OrderPageModel = {
      page: { size: 10, totalElements: 0, totalPages: 0, number: 0 },
    };

    orderService.listOrders(0, 10).subscribe((data) => expect(data).toBe(page));

    const req = httpTestingController.expectOne(
      (request) =>
        request.url === `${environment.apiPrefix}/order` &&
        request.params.get('page') === '0' &&
        request.params.get('size') === '10'
    );
    expect(req.request.method).toBe('GET');
    req.flush(page);
  });

  it('finds an order by number', () => {
    const orderDetails = { orderNumber: '20220915123015' } as OrderDetailsModel;

    orderService
      .findOrder('20220915123015')
      .subscribe((data) => expect(data).toBe(orderDetails));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/order/20220915123015`
    );
    expect(req.request.method).toBe('GET');
    req.flush(orderDetails);
  });

  it('reads a bounded recovery timeline page', () => {
    const timeline = {
      orderNumber: 'ORD-1001',
      page: 0,
      size: 50,
      items: [],
    };

    orderService
      .findRecoveryTimeline('ORD-1001')
      .subscribe((data) => expect(data).toBe(timeline));

    const req = httpTestingController.expectOne(
      (request) =>
        request.url ===
          `${environment.apiPrefix}/order/ORD-1001/recovery-timeline` &&
        request.params.get('page') === '0' &&
        request.params.get('size') === '50'
    );
    expect(req.request.method).toBe('GET');
    req.flush(timeline);
  });

  it('reads an explicit recovery timeline page', () => {
    const timeline = {
      orderNumber: 'ORD-1001',
      page: 2,
      size: 25,
      items: [],
    };

    orderService
      .findRecoveryTimeline('ORD-1001', 2, 25)
      .subscribe((data) => expect(data).toBe(timeline));

    const req = httpTestingController.expectOne(
      (request) =>
        request.url ===
          `${environment.apiPrefix}/order/ORD-1001/recovery-timeline` &&
        request.params.get('page') === '2' &&
        request.params.get('size') === '25'
    );
    expect(req.request.method).toBe('GET');
    req.flush(timeline);
  });

  it('cancels an order', () => {
    const orderDetails = { orderNumber: '20220915123015' } as OrderDetailsModel;

    orderService
      .cancelOrder('20220915123015')
      .subscribe((data) => expect(data).toBe(orderDetails));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/order/20220915123015/cancel`
    );
    expect(req.request.method).toBe('POST');
    req.flush(orderDetails);
  });
});
