import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@environments/environment';
import {
  RequestReturnModel,
  ReturnCollectionModel,
  ReturnModel,
} from '@app/returns/return.model';

@Injectable({ providedIn: 'root' })
export class ReturnsService {
  private readonly httpClient = inject(HttpClient);

  listReturns(page?: number, size?: number): Observable<ReturnCollectionModel> {
    const suffix = page === undefined ? '' : `?page=${page}&size=${size ?? 20}`;
    return this.httpClient.get<ReturnCollectionModel>(
      `${environment.apiPrefix}/returns${suffix}`
    );
  }

  listPendingReturns(
    page?: number,
    size?: number
  ): Observable<ReturnCollectionModel> {
    const suffix = page === undefined ? '' : `?page=${page}&size=${size ?? 20}`;
    return this.httpClient.get<ReturnCollectionModel>(
      `${environment.apiPrefix}/returns/pending${suffix}`
    );
  }

  listReturnsForOrder(
    orderNumber: string,
    page?: number,
    size?: number
  ): Observable<ReturnCollectionModel> {
    const suffix = page === undefined ? '' : `?page=${page}&size=${size ?? 20}`;
    return this.httpClient.get<ReturnCollectionModel>(
      `${environment.apiPrefix}/returns/order/${orderNumber}${suffix}`
    );
  }

  requestReturn(request: RequestReturnModel): Observable<ReturnModel> {
    return this.httpClient.post<ReturnModel>(
      `${environment.apiPrefix}/returns`,
      request
    );
  }

  approveReturn(returnNumber: string): Observable<ReturnModel> {
    return this.httpClient.post<ReturnModel>(
      `${environment.apiPrefix}/returns/${returnNumber}/approve`,
      {}
    );
  }

  rejectReturn(returnNumber: string): Observable<ReturnModel> {
    return this.httpClient.post<ReturnModel>(
      `${environment.apiPrefix}/returns/${returnNumber}/reject`,
      {}
    );
  }
}
