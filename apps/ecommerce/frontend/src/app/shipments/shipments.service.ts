import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@environments/environment';
import {
  CreateShipmentModel,
  ShipmentCollectionModel,
  ShipmentModel,
  ShipmentStatus,
} from '@app/shipments/shipment.model';

export interface PendingShipmentAdvanceOperation {
  operationId: string;
  expectedStatus: ShipmentStatus;
}

export function isDefinitiveShipmentAdvanceConflict(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 409;
}

@Injectable({ providedIn: 'root' })
export class ShipmentsService {
  private readonly httpClient = inject(HttpClient);
  private readonly pendingAdvanceOperations = new Map<
    string,
    PendingShipmentAdvanceOperation
  >();

  getOrCreatePendingAdvanceOperation(
    shipmentNumber: string,
    expectedStatus: ShipmentStatus
  ): PendingShipmentAdvanceOperation {
    const existing = this.pendingAdvanceOperations.get(shipmentNumber);
    if (existing) return existing;

    const created = {
      operationId: crypto.randomUUID(),
      expectedStatus,
    };
    this.pendingAdvanceOperations.set(shipmentNumber, created);
    return created;
  }

  clearPendingAdvanceOperation(shipmentNumber: string): void {
    this.pendingAdvanceOperations.delete(shipmentNumber);
  }

  listShipments(
    page?: number,
    size?: number
  ): Observable<ShipmentCollectionModel> {
    const suffix = page === undefined ? '' : `?page=${page}&size=${size ?? 20}`;
    return this.httpClient.get<ShipmentCollectionModel>(
      `${environment.apiPrefix}/shipments${suffix}`
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
    status: ShipmentStatus,
    page?: number,
    size?: number
  ): Observable<ShipmentCollectionModel> {
    const suffix = page === undefined ? '' : `?page=${page}&size=${size ?? 20}`;
    return this.httpClient.get<ShipmentCollectionModel>(
      `${environment.apiPrefix}/shipments/status/${status}${suffix}`
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

  advanceShipmentStatus(
    shipmentNumber: string,
    operationId?: string,
    expectedStatus?: ShipmentStatus
  ): Observable<ShipmentModel> {
    const options =
      operationId && expectedStatus
        ? {
            headers: {
              'Idempotency-Key': operationId,
              'X-Expected-Shipment-Status': expectedStatus,
            },
          }
        : {};
    return this.httpClient.post<ShipmentModel>(
      `${environment.apiPrefix}/shipments/${shipmentNumber}/advance`,
      {},
      options
    );
  }
}
