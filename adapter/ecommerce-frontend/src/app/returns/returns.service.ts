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

  listReturns(): Observable<ReturnCollectionModel> {
    return this.httpClient.get<ReturnCollectionModel>(
      `${environment.apiPrefix}/returns`
    );
  }

  listPendingReturns(): Observable<ReturnCollectionModel> {
    return this.httpClient.get<ReturnCollectionModel>(
      `${environment.apiPrefix}/returns/pending`
    );
  }

  listReturnsForOrder(orderNumber: string): Observable<ReturnCollectionModel> {
    return this.httpClient.get<ReturnCollectionModel>(
      `${environment.apiPrefix}/returns/order/${orderNumber}`
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
