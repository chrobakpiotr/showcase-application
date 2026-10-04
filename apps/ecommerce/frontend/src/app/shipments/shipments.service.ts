import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { AuthService } from '@app/auth/auth.service';

import { environment } from '@environments/environment';
import {
  CreateShipmentModel,
  ShipmentCollectionModel,
  ShipmentModel,
  ShipmentStatus,
} from '@app/shipments/shipment.model';

export interface PendingShipmentAdvanceOperation {
  username: string;
  operationId: string;
  expectedStatus: ShipmentStatus;
}

interface StoredShipmentAdvanceOperation
  extends PendingShipmentAdvanceOperation {
  version: 1;
  shipmentNumber: string;
}

export function isDefinitiveShipmentAdvanceConflict(error: unknown): boolean {
  if (!(error instanceof HttpErrorResponse) || error.status !== 409)
    return false;
  const body: unknown = error.error;
  if (body === null || typeof body !== 'object' || Array.isArray(body))
    return false;
  const code = (body as Record<string, unknown>)['code'];
  return (
    code === 'SHIPMENT_STALE_STATUS' ||
    code === 'SHIPMENT_OPERATION_FINGERPRINT_CONFLICT'
  );
}

export class ShipmentAdvanceOperationStore {
  private key(username: string, shipmentNumber: string): string {
    if (!username.trim() || !shipmentNumber.trim()) {
      throw new Error(
        'Shipment operation requires an authenticated identity and shipment.'
      );
    }
    return `showcase.shipment-advance.v1:${encodeURIComponent(
      username
    )}:${encodeURIComponent(shipmentNumber)}`;
  }

  private read(
    username: string,
    shipmentNumber: string
  ): StoredShipmentAdvanceOperation | null {
    const raw = sessionStorage.getItem(this.key(username, shipmentNumber));
    if (raw === null) return null;
    const parsed: unknown = JSON.parse(raw);
    if (
      parsed === null ||
      typeof parsed !== 'object' ||
      Array.isArray(parsed)
    ) {
      throw new Error('Invalid pending shipment operation.');
    }
    const value = parsed as Record<string, unknown>;
    const keys = [
      'version',
      'username',
      'shipmentNumber',
      'operationId',
      'expectedStatus',
    ];
    if (
      Object.keys(value).length !== keys.length ||
      !keys.every((key) => Object.prototype.hasOwnProperty.call(value, key)) ||
      value['version'] !== 1 ||
      value['username'] !== username ||
      value['shipmentNumber'] !== shipmentNumber ||
      typeof value['operationId'] !== 'string' ||
      !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
        value['operationId']
      ) ||
      !['PENDING', 'DISPATCHED', 'IN_TRANSIT', 'DELIVERED'].includes(
        String(value['expectedStatus'])
      ) ||
      typeof value['expectedStatus'] !== 'string'
    ) {
      throw new Error('Invalid pending shipment operation.');
    }
    return value as unknown as StoredShipmentAdvanceOperation;
  }

  getOrCreate(
    username: string,
    shipmentNumber: string,
    expectedStatus: ShipmentStatus
  ): PendingShipmentAdvanceOperation {
    const existing = this.read(username, shipmentNumber);
    if (existing) return existing;
    const created: StoredShipmentAdvanceOperation = {
      version: 1,
      username,
      shipmentNumber,
      operationId: crypto.randomUUID(),
      expectedStatus,
    };
    sessionStorage.setItem(
      this.key(username, shipmentNumber),
      JSON.stringify(created)
    );
    return created;
  }

  clear(username: string, shipmentNumber: string, operationId: string): void {
    const existing = this.read(username, shipmentNumber);
    if (existing?.operationId === operationId) {
      sessionStorage.removeItem(this.key(username, shipmentNumber));
    }
  }
}

@Injectable({ providedIn: 'root' })
export class ShipmentsService {
  private readonly httpClient = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly pendingAdvanceOperations =
    new ShipmentAdvanceOperationStore();

  getOrCreatePendingAdvanceOperation(
    shipmentNumber: string,
    expectedStatus: ShipmentStatus
  ): PendingShipmentAdvanceOperation {
    if (
      !this.authService.isAuthenticated() ||
      !this.authService.username().trim()
    ) {
      throw new Error('Shipment operation requires an authenticated identity.');
    }
    return this.pendingAdvanceOperations.getOrCreate(
      this.authService.username(),
      shipmentNumber,
      expectedStatus
    );
  }

  clearPendingAdvanceOperation(
    shipmentNumber: string,
    operation: PendingShipmentAdvanceOperation
  ): void {
    this.pendingAdvanceOperations.clear(
      operation.username,
      shipmentNumber,
      operation.operationId
    );
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
