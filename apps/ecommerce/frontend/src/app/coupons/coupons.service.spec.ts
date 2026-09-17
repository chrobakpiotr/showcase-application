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

import { CouponsService } from '@app/coupons/coupons.service';
import { environment } from '@environments/environment';

describe('CouponsService', () => {
  let couponsService: CouponsService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        CouponsService,
        provideHttpClient(withXhr(), withInterceptorsFromDi()),
        provideHttpClientTesting(),
      ],
    });
    couponsService = TestBed.inject(CouponsService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should be created', () => {
    expect(couponsService).toBeTruthy();
  });

  it('lists coupons', () => {
    couponsService.listCoupons().subscribe();
    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/coupons`
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      page: { size: 10, totalElements: 0, totalPages: 0, number: 0 },
    });
  });

  it('lists coupons with activeOnly filter', () => {
    couponsService.listCoupons(true).subscribe();
    const req = httpTestingController.expectOne(
      (request) =>
        request.url === `${environment.apiPrefix}/coupons` &&
        request.params.get('activeOnly') === 'true'
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      page: { size: 10, totalElements: 0, totalPages: 0, number: 0 },
    });
  });

  it('creates a coupon', () => {
    couponsService
      .createCoupon({
        code: 'SAVE10',
        discountType: 'PERCENTAGE',
        discountValue: 10,
        minimumOrderAmount: null,
        maxRedemptions: null,
        expiresAt: null,
        active: true,
      })
      .subscribe();
    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/coupons`
    );
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('activates a coupon', () => {
    couponsService.activateCoupon('SAVE10').subscribe();
    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/coupons/SAVE10/activate`
    );
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('deactivates a coupon', () => {
    couponsService.deactivateCoupon('SAVE10').subscribe();
    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/coupons/SAVE10/deactivate`
    );
    expect(req.request.method).toBe('POST');
    req.flush({});
  });
});
