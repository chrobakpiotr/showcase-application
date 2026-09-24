import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@environments/environment';
import {
  OrderDetailsModel,
  OrderPageModel,
} from '@app/order/order-details.model';
import { OrderRecoveryTimelineModel } from '@app/order/order-recovery-timeline.model';
import { OrderRequestModel } from '@app/order/order-request.model';
import { OrderResponseModel } from '@app/order/order-response.model';

@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly httpClient = inject(HttpClient);

  private readonly httpOptions = {
    headers: new HttpHeaders({
      'Content-Type': 'application/json',
    }),
  };

  placeOrder(
    body: OrderRequestModel,
    idempotencyKey: string
  ): Observable<OrderResponseModel> {
    return this.httpClient.post<OrderResponseModel>(
      `${environment.apiPrefix}/order`,
      body,
      {
        headers: this.httpOptions.headers.set(
          'Idempotency-Key',
          idempotencyKey
        ),
      }
    );
  }

  listOrders(page: number, size: number): Observable<OrderPageModel> {
    return this.httpClient.get<OrderPageModel>(
      `${environment.apiPrefix}/order`,
      { params: { page, size } }
    );
  }

  findOrder(orderNumber: string): Observable<OrderDetailsModel> {
    return this.httpClient.get<OrderDetailsModel>(
      `${environment.apiPrefix}/order/${orderNumber}`
    );
  }

  findRecoveryTimeline(
    orderNumber: string,
    page = 0,
    size = 50
  ): Observable<OrderRecoveryTimelineModel> {
    return this.httpClient.get<OrderRecoveryTimelineModel>(
      `${environment.apiPrefix}/order/${orderNumber}/recovery-timeline`,
      { params: { page, size } }
    );
  }

  cancelOrder(orderNumber: string): Observable<OrderDetailsModel> {
    return this.httpClient.post<OrderDetailsModel>(
      `${environment.apiPrefix}/order/${orderNumber}/cancel`,
      {}
    );
  }
}
