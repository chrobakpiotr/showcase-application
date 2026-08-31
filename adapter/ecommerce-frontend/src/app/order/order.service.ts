import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@environments/environment';
import { CustomerRequestModel } from '@app/order/customer-request.model';
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
    remarks: string,
    customer: CustomerRequestModel
  ): Observable<OrderResponseModel> {
    const body: OrderRequestModel = { remarks, created: new Date(), customer };
    return this.httpClient.post<OrderResponseModel>(
      `${environment.apiPrefix}/order`,
      body,
      this.httpOptions
    );
  }
}
