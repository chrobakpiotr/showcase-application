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
import { ShipmentsService } from '@app/shipments/shipments.service';

describe('ShipmentsService', () => {
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
    TestBed.configureTestingModule({
      providers: [
        ShipmentsService,
        provideHttpClient(withXhr(), withInterceptorsFromDi()),
        provideHttpClientTesting(),
      ],
    });
    shipmentsService = TestBed.inject(ShipmentsService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should be created', () => {
    expect(shipmentsService).toBeTruthy();
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
    expect(req.request.headers.has('Idempotency-Key')).toBeFalse();
    expect(req.request.headers.has('X-Expected-Shipment-Status')).toBeFalse();
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
