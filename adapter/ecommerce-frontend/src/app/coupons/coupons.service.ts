import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@environments/environment';
import {
  CouponModel,
  CouponPageModel,
  CouponRequestModel,
} from '@app/coupons/coupon.model';

@Injectable({ providedIn: 'root' })
export class CouponsService {
  private readonly httpClient = inject(HttpClient);

  listCoupons(activeOnly?: boolean): Observable<CouponPageModel> {
    let params = new HttpParams();
    if (activeOnly !== undefined) {
      params = params.set('activeOnly', activeOnly);
    }
    return this.httpClient.get<CouponPageModel>(
      `${environment.apiPrefix}/coupons`,
      { params }
    );
  }

  createCoupon(request: CouponRequestModel): Observable<CouponModel> {
    return this.httpClient.post<CouponModel>(
      `${environment.apiPrefix}/coupons`,
      request
    );
  }

  activateCoupon(code: string): Observable<CouponModel> {
    return this.httpClient.post<CouponModel>(
      `${environment.apiPrefix}/coupons/${code}/activate`,
      {}
    );
  }

  deactivateCoupon(code: string): Observable<CouponModel> {
    return this.httpClient.post<CouponModel>(
      `${environment.apiPrefix}/coupons/${code}/deactivate`,
      {}
    );
  }
}
