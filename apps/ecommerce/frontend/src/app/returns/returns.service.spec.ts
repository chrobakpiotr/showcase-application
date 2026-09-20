import { TestBed } from '@angular/core/testing';

import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import {
  provideHttpClient,
  withInterceptorsFromDi,
  withXhr,
} from '@angular/common/http';

import { ReturnsService } from '@app/returns/returns.service';
import { ReturnCollectionModel, ReturnModel } from '@app/returns/return.model';
import { environment } from '@environments/environment';

describe('ReturnsService', () => {
  let returnsService: ReturnsService;
  let httpTestingController: HttpTestingController;

  const returnRequest: ReturnModel = {
    returnNumber: 'RETURN-1',
    orderNumber: 'ORD-1',
    sku: 'SKU-1',
    quantity: 1,
    reason: 'Damaged',
    status: 'REQUESTED',
    requestedDate: '2024-03-15T10:30:00.000Z',
    decidedDate: null,
    refundAmount: 29.99,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ReturnsService,
        provideHttpClient(withXhr(), withInterceptorsFromDi()),
        provideHttpClientTesting(),
      ],
    });
    returnsService = TestBed.inject(ReturnsService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should be created', () => {
    expect(returnsService).toBeTruthy();
  });

  it('lists returns', () => {
    const response: ReturnCollectionModel = {
      _embedded: { returnRequestResourceList: [returnRequest] },
    };

    returnsService
      .listReturns()
      .subscribe((data) => expect(data).toBe(response));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/returns`
    );
    expect(req.request.method).toBe('GET');
    req.flush(response);
  });

  it('lists pending returns', () => {
    const response: ReturnCollectionModel = {
      _embedded: { returnRequestResourceList: [returnRequest] },
    };

    returnsService
      .listPendingReturns()
      .subscribe((data) => expect(data).toBe(response));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/returns/pending`
    );
    expect(req.request.method).toBe('GET');
    req.flush(response);
  });

  it('lists returns for an order', () => {
    const response: ReturnCollectionModel = {
      _embedded: { returnRequestResourceList: [returnRequest] },
    };

    returnsService
      .listReturnsForOrder('ORD-1')
      .subscribe((data) => expect(data).toBe(response));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/returns/order/ORD-1`
    );
    expect(req.request.method).toBe('GET');
    req.flush(response);
  });

  it('creates a return request', () => {
    returnsService
      .requestReturn({
        orderNumber: 'ORD-1',
        sku: 'SKU-1',
        quantity: 1,
        reason: 'Damaged',
      })
      .subscribe((data) => expect(data).toBe(returnRequest));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/returns`
    );
    expect(req.request.method).toBe('POST');
    req.flush(returnRequest);
  });

  it('approves a return request', () => {
    returnsService
      .approveReturn('RETURN-1')
      .subscribe((data) => expect(data).toBe(returnRequest));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/returns/RETURN-1/approve`
    );
    expect(req.request.method).toBe('POST');
    req.flush(returnRequest);
  });

  it('rejects a return request', () => {
    returnsService
      .rejectReturn('RETURN-1')
      .subscribe((data) => expect(data).toBe(returnRequest));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/returns/RETURN-1/reject`
    );
    expect(req.request.method).toBe('POST');
    req.flush(returnRequest);
  });

  it('covers explicit and default paging parameters for return reads', () => {
    const response: ReturnCollectionModel = {
      _embedded: { returnRequestResourceList: [returnRequest] },
    };

    returnsService.listReturns(2, 7).subscribe();
    let req = httpTestingController.expectOne(
      `${environment.apiPrefix}/returns?page=2&size=7`
    );
    expect(req.request.method).toBe('GET');
    req.flush(response);

    returnsService.listReturns(3).subscribe();
    req = httpTestingController.expectOne(
      `${environment.apiPrefix}/returns?page=3&size=20`
    );
    req.flush(response);

    returnsService.listPendingReturns(4, 9).subscribe();
    req = httpTestingController.expectOne(
      `${environment.apiPrefix}/returns/pending?page=4&size=9`
    );
    req.flush(response);

    returnsService.listPendingReturns(5).subscribe();
    req = httpTestingController.expectOne(
      `${environment.apiPrefix}/returns/pending?page=5&size=20`
    );
    req.flush(response);

    returnsService.listReturnsForOrder('ORD-1', 6, 11).subscribe();
    req = httpTestingController.expectOne(
      `${environment.apiPrefix}/returns/order/ORD-1?page=6&size=11`
    );
    req.flush(response);

    returnsService.listReturnsForOrder('ORD-1', 7).subscribe();
    req = httpTestingController.expectOne(
      `${environment.apiPrefix}/returns/order/ORD-1?page=7&size=20`
    );
    req.flush(response);
  });
});
