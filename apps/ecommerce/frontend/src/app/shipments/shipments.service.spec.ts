import { signal } from '@angular/core';
import { AuthService } from '@app/auth/auth.service';
import { HttpErrorResponse } from '@angular/common/http';
import { isDefinitiveShipmentAdvanceConflict } from '@app/shipments/shipments.service';
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

import { environment } from '@environments/environment';
import {
  ShipmentCollectionModel,
  ShipmentModel,
} from '@app/shipments/shipment.model';
import {
  ShipmentAdvanceOperationStore,
  ShipmentsService,
} from '@app/shipments/shipments.service';

describe('ShipmentsService', () => {
  const auth = { username: signal('operator'), isAuthenticated: signal(true) };
  const storageKey = 'showcase.shipment-advance.v1:operator:SHIP-1';
  let shipmentsService: ShipmentsService;
  let httpTestingController: HttpTestingController;

  const shipment: ShipmentModel = {
    shipmentNumber: 'SHIP-1',
    orderNumber: 'ORD-1',
    carrier: 'DHL',
    trackingNumber: 'DHL-1234',
    status: 'PENDING',
    dispatchedDate: null,
    estimatedDeliveryDate: null,
    deliveredDate: null,
    createdDate: '2024-03-15T10:30:00.000Z',
    _links: { 'advance-status': { href: '/api/shipments/SHIP-1/advance' } },
  };

  beforeEach(() => {
    sessionStorage.removeItem(storageKey);
    auth.username.set('operator');
    auth.isAuthenticated.set(true);
    TestBed.configureTestingModule({
      providers: [
        ShipmentsService,
        { provide: AuthService, useValue: auth },
        provideHttpClient(withXhr(), withInterceptorsFromDi()),
        provideHttpClientTesting(),
      ],
    });
    shipmentsService = TestBed.inject(ShipmentsService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    sessionStorage.removeItem(storageKey);
  });

  it('should be created', () => {
    expect(shipmentsService).toBeTruthy();
  });

  it('retains unresolved operation identity until explicitly cleared', () => {
    const first = shipmentsService.getOrCreatePendingAdvanceOperation(
      'SHIP-1',
      'PENDING'
    );
    const replay = shipmentsService.getOrCreatePendingAdvanceOperation(
      'SHIP-1',
      'DISPATCHED'
    );

    expect(replay.operationId).toBe(first.operationId);
    expect(replay.expectedStatus).toBe('PENDING');

    shipmentsService.clearPendingAdvanceOperation('SHIP-1', first);
    const next = shipmentsService.getOrCreatePendingAdvanceOperation(
      'SHIP-1',
      'DISPATCHED'
    );

    expect(next.operationId).not.toBe(first.operationId);
    expect(next.expectedStatus).toBe('DISPATCHED');
  });

  it('reuses the persisted identity and original status after service recreation', () => {
    const first = shipmentsService.getOrCreatePendingAdvanceOperation(
      'SHIP-1',
      'PENDING'
    );
    const reloaded = TestBed.runInInjectionContext(
      () => new ShipmentsService()
    );
    expect(
      reloaded.getOrCreatePendingAdvanceOperation('SHIP-1', 'DISPATCHED')
    ).toEqual(first);
    expect(sessionStorage.getItem(storageKey)).not.toBeNull();
  });

  it('fails closed for corrupt storage and unauthenticated identity', () => {
    sessionStorage.setItem(storageKey, '{');
    expect(() =>
      shipmentsService.getOrCreatePendingAdvanceOperation('SHIP-1', 'PENDING')
    ).toThrow();
    expect(sessionStorage.getItem(storageKey)).toBe('{');
    sessionStorage.removeItem(storageKey);
    auth.isAuthenticated.set(false);
    expect(() =>
      shipmentsService.getOrCreatePendingAdvanceOperation('SHIP-1', 'PENDING')
    ).toThrow();
  });

  it('preserves generic and unknown 409 errors while recognizing only typed codes', () => {
    for (const error of [undefined, {}, { code: 'UNKNOWN' }, { code: 42 }]) {
      expect(
        isDefinitiveShipmentAdvanceConflict(
          new HttpErrorResponse({ status: 409, error })
        )
      ).toBe(false);
    }
    for (const code of [
      'SHIPMENT_STALE_STATUS',
      'SHIPMENT_OPERATION_FINGERPRINT_CONFLICT',
    ]) {
      expect(
        isDefinitiveShipmentAdvanceConflict(
          new HttpErrorResponse({ status: 409, error: { code } })
        )
      ).toBe(true);
    }
  });

  it('clears only the exact operation and original authenticated scope', () => {
    const original = shipmentsService.getOrCreatePendingAdvanceOperation(
      'SHIP-1',
      'PENDING'
    );
    shipmentsService.clearPendingAdvanceOperation('SHIP-1', {
      ...original,
      operationId: crypto.randomUUID(),
    });
    expect(
      shipmentsService.getOrCreatePendingAdvanceOperation(
        'SHIP-1',
        'DISPATCHED'
      ).operationId
    ).toBe(original.operationId);
    shipmentsService.clearPendingAdvanceOperation('SHIP-1', original);
    const newer = shipmentsService.getOrCreatePendingAdvanceOperation(
      'SHIP-1',
      'DISPATCHED'
    );
    shipmentsService.clearPendingAdvanceOperation('SHIP-1', original);
    expect(
      shipmentsService.getOrCreatePendingAdvanceOperation(
        'SHIP-1',
        'IN_TRANSIT'
      )
    ).toEqual(newer);
    auth.username.set('another-operator');
    const other = shipmentsService.getOrCreatePendingAdvanceOperation(
      'SHIP-1',
      'PENDING'
    );
    shipmentsService.clearPendingAdvanceOperation('SHIP-1', newer);
    expect(
      shipmentsService.getOrCreatePendingAdvanceOperation(
        'SHIP-1',
        'DISPATCHED'
      )
    ).toEqual(other);
    sessionStorage.removeItem(
      'showcase.shipment-advance.v1:another-operator:SHIP-1'
    );
  });

  it('rejects malformed record shapes, identities, versions, UUIDs and statuses without replacing them', () => {
    const valid = {
      version: 1,
      username: 'operator',
      shipmentNumber: 'SHIP-1',
      operationId: crypto.randomUUID(),
      expectedStatus: 'PENDING',
    };
    for (const value of [
      null,
      [],
      { ...valid, version: 2 },
      { ...valid, username: 'other' },
      { ...valid, shipmentNumber: 'OTHER' },
      { ...valid, operationId: 'invalid' },
      { ...valid, expectedStatus: 'UNKNOWN' },
      { ...valid, extra: true },
    ]) {
      const raw = JSON.stringify(value);
      sessionStorage.setItem(storageKey, raw);
      expect(() =>
        shipmentsService.getOrCreatePendingAdvanceOperation('SHIP-1', 'PENDING')
      ).toThrow();
      expect(sessionStorage.getItem(storageKey)).toBe(raw);
    }
    sessionStorage.removeItem(storageKey);
    auth.username.set(' ');
    expect(() =>
      shipmentsService.getOrCreatePendingAdvanceOperation('SHIP-1', 'PENDING')
    ).toThrow();
  });

  it('blocks submission when session storage is unavailable', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('storage unavailable');
    });
    expect(() =>
      shipmentsService.getOrCreatePendingAdvanceOperation('SHIP-1', 'PENDING')
    ).toThrow();
    httpTestingController.expectNone(
      `${environment.apiPrefix}/shipments/SHIP-1/advance`
    );
  });

  it('rejects empty shipment operation identity components', () => {
    const store = new ShipmentAdvanceOperationStore();

    expect(() => store.getOrCreate(' ', 'SHIP-1', 'PENDING')).toThrow();
    expect(() => store.getOrCreate('operator', ' ', 'PENDING')).toThrow();
  });

  it('lists shipments', () => {
    const response: ShipmentCollectionModel = {
      _embedded: { shipmentResourceList: [shipment] },
    };

    shipmentsService
      .listShipments()
      .subscribe((data) => expect(data).toBe(response));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/shipments`
    );
    expect(req.request.method).toBe('GET');
    req.flush(response);
  });

  it('lists shipments for an order', () => {
    const response: ShipmentCollectionModel = {
      _embedded: { shipmentResourceList: [shipment] },
    };

    shipmentsService
      .listShipmentsForOrder('ORD-1')
      .subscribe((data) => expect(data).toBe(response));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/shipments/order/ORD-1`
    );
    expect(req.request.method).toBe('GET');
    req.flush(response);
  });

  it('lists shipments by status', () => {
    const response: ShipmentCollectionModel = {
      _embedded: { shipmentResourceList: [shipment] },
    };

    shipmentsService
      .listShipmentsByStatus('PENDING')
      .subscribe((data) => expect(data).toBe(response));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/shipments/status/PENDING`
    );
    expect(req.request.method).toBe('GET');
    req.flush(response);
  });

  it('gets a shipment by number', () => {
    shipmentsService
      .getShipment('SHIP-1')
      .subscribe((data) => expect(data).toBe(shipment));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/shipments/SHIP-1`
    );
    expect(req.request.method).toBe('GET');
    req.flush(shipment);
  });

  it('creates a shipment', () => {
    shipmentsService
      .createShipment({ orderNumber: 'ORD-1', carrier: 'DHL' })
      .subscribe((data) => expect(data).toBe(shipment));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/shipments`
    );
    expect(req.request.method).toBe('POST');
    req.flush(shipment);
  });

  it('advances a shipment status', () => {
    shipmentsService
      .advanceShipmentStatus('SHIP-1')
      .subscribe((data) => expect(data).toBe(shipment));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/shipments/SHIP-1/advance`
    );
    expect(req.request.method).toBe('POST');
    req.flush(shipment);
  });

  it('covers shipment paging defaults and idempotency header branches', () => {
    const response: ShipmentCollectionModel = {
      _embedded: { shipmentResourceList: [shipment] },
    };

    shipmentsService.listShipments(2, 7).subscribe();
    let req = httpTestingController.expectOne(
      `${environment.apiPrefix}/shipments?page=2&size=7`
    );
    expect(req.request.method).toBe('GET');
    req.flush(response);

    shipmentsService.listShipments(3).subscribe();
    req = httpTestingController.expectOne(
      `${environment.apiPrefix}/shipments?page=3&size=20`
    );
    req.flush(response);

    shipmentsService.listShipmentsByStatus('DISPATCHED', 4, 9).subscribe();
    req = httpTestingController.expectOne(
      `${environment.apiPrefix}/shipments/status/DISPATCHED?page=4&size=9`
    );
    req.flush(response);

    shipmentsService.listShipmentsByStatus('DELIVERED', 5).subscribe();
    req = httpTestingController.expectOne(
      `${environment.apiPrefix}/shipments/status/DELIVERED?page=5&size=20`
    );
    req.flush(response);

    shipmentsService
      .advanceShipmentStatus('SHIP-1', 'op-without-status')
      .subscribe();
    req = httpTestingController.expectOne(
      `${environment.apiPrefix}/shipments/SHIP-1/advance`
    );
    expect(req.request.headers.has('Idempotency-Key')).toBe(false);
    expect(req.request.headers.has('X-Expected-Shipment-Status')).toBe(false);
    req.flush(shipment);

    shipmentsService
      .advanceShipmentStatus('SHIP-1', 'op-2', 'PENDING')
      .subscribe();
    req = httpTestingController.expectOne(
      `${environment.apiPrefix}/shipments/SHIP-1/advance`
    );
    expect(req.request.headers.get('Idempotency-Key')).toBe('op-2');
    expect(req.request.headers.get('X-Expected-Shipment-Status')).toBe(
      'PENDING'
    );
    req.flush(shipment);
  });
});
