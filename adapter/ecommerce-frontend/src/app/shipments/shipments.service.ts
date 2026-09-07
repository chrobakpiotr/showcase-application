import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@environments/environment';
import {
  CreateShipmentModel,
  ShipmentCollectionModel,
  ShipmentModel,
  ShipmentStatus,
} from '@app/shipments/shipment.model';

@Injectable({ providedIn: 'root' })
export class ShipmentsService {
  private readonly httpClient = inject(HttpClient);

  listShipments(): Observable<ShipmentCollectionModel> {
    return this.httpClient.get<ShipmentCollectionModel>(
      `${environment.apiPrefix}/shipments`
    );
  }

  listShipmentsForOrder(
    orderNumber: string
  ): Observable<ShipmentCollectionModel> {
    return this.httpClient.get<ShipmentCollectionModel>(
      `${environment.apiPrefix}/shipments/order/${orderNumber}`
    );
  }

  listShipmentsByStatus(
    status: ShipmentStatus
  ): Observable<ShipmentCollectionModel> {
    return this.httpClient.get<ShipmentCollectionModel>(
      `${environment.apiPrefix}/shipments/status/${status}`
    );
  }

  getShipment(shipmentNumber: string): Observable<ShipmentModel> {
    return this.httpClient.get<ShipmentModel>(
      `${environment.apiPrefix}/shipments/${shipmentNumber}`
    );
  }

  createShipment(request: CreateShipmentModel): Observable<ShipmentModel> {
    return this.httpClient.post<ShipmentModel>(
      `${environment.apiPrefix}/shipments`,
      request
    );
  }

  advanceShipmentStatus(shipmentNumber: string): Observable<ShipmentModel> {
    return this.httpClient.post<ShipmentModel>(
      `${environment.apiPrefix}/shipments/${shipmentNumber}/advance`,
      {}
    );
  }
}
