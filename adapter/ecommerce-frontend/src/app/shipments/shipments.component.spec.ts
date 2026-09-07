import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';

import { AuthService } from '@app/auth/auth.service';
import { ShipmentModel } from '@app/shipments/shipment.model';
import { ShipmentsComponent } from '@app/shipments/shipments.component';
import { ShipmentsService } from '@app/shipments/shipments.service';

describe('ShipmentsComponent', () => {
  let fixture: ComponentFixture<ShipmentsComponent>;
  let component: ShipmentsComponent;
  let shipmentsServiceSpy: jasmine.SpyObj<ShipmentsService>;

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

  function setup(roles: string[] = []): void {
    shipmentsServiceSpy = jasmine.createSpyObj('ShipmentsService', [
      'listShipments',
      'listShipmentsByStatus',
      'advanceShipmentStatus',
    ]);
    shipmentsServiceSpy.listShipments.and.returnValue(
      of({ _embedded: { shipmentResourceList: [shipment] } })
    );
    shipmentsServiceSpy.listShipmentsByStatus.and.returnValue(
      of({ _embedded: { shipmentResourceList: [shipment] } })
    );

    TestBed.configureTestingModule({
      imports: [ShipmentsComponent],
      providers: [
        { provide: ShipmentsService, useValue: shipmentsServiceSpy },
        { provide: AuthService, useValue: { roles: signal(roles) } },
      ],
    });

    fixture = TestBed.createComponent(ShipmentsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should create the component', () => {
    setup();
    expect(component).toBeTruthy();
  });

  it('does not load shipments without the SHIPMENT_READ role', () => {
    setup();
    expect(shipmentsServiceSpy.listShipments).not.toHaveBeenCalled();
    expect(component.canRead).toBeFalse();
  });

  it('loads shipments with the SHIPMENT_READ role', () => {
    setup(['SHIPMENT_READ']);
    expect(shipmentsServiceSpy.listShipments).toHaveBeenCalled();
    expect(component.shipments()).toEqual([shipment]);
  });

  it('defaults the list to an empty array when embedded collection is missing', () => {
    shipmentsServiceSpy = jasmine.createSpyObj('ShipmentsService', [
      'listShipments',
      'listShipmentsByStatus',
      'advanceShipmentStatus',
    ]);
    shipmentsServiceSpy.listShipments.and.returnValue(of({}));
    shipmentsServiceSpy.listShipmentsByStatus.and.returnValue(of({}));

    TestBed.configureTestingModule({
      imports: [ShipmentsComponent],
      providers: [
        { provide: ShipmentsService, useValue: shipmentsServiceSpy },
        {
          provide: AuthService,
          useValue: { roles: signal(['SHIPMENT_READ']) },
        },
      ],
    });

    fixture = TestBed.createComponent(ShipmentsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.shipments()).toEqual([]);
  });

  it('loads shipments filtered by status', () => {
    setup(['SHIPMENT_READ']);

    component.updateStatusFilter('DISPATCHED');

    expect(shipmentsServiceSpy.listShipmentsByStatus).toHaveBeenCalledWith(
      'DISPATCHED'
    );
    expect(component.selectedStatus()).toBe('DISPATCHED');
  });

  it('reports canWrite true with the SHIPMENT_WRITE role', () => {
    setup(['SHIPMENT_WRITE']);
    expect(component.canWrite).toBeTrue();
  });

  it('sets an error message when loading shipments fails', () => {
    shipmentsServiceSpy = jasmine.createSpyObj('ShipmentsService', [
      'listShipments',
      'listShipmentsByStatus',
      'advanceShipmentStatus',
    ]);
    shipmentsServiceSpy.listShipments.and.returnValue(
      throwError(() => new Error('failed'))
    );
    shipmentsServiceSpy.listShipmentsByStatus.and.returnValue(
      of({ _embedded: { shipmentResourceList: [] } })
    );
    TestBed.configureTestingModule({
      imports: [ShipmentsComponent],
      providers: [
        { provide: ShipmentsService, useValue: shipmentsServiceSpy },
        {
          provide: AuthService,
          useValue: { roles: signal(['SHIPMENT_READ']) },
        },
      ],
    });
    fixture = TestBed.createComponent(ShipmentsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.errorMessage()).toBe('Failed to load shipments.');
  });

  it('advances a shipment and reloads the list', () => {
    setup(['SHIPMENT_READ', 'SHIPMENT_WRITE']);
    shipmentsServiceSpy.advanceShipmentStatus.and.returnValue(of(shipment));

    component.advance('SHIP-1');

    expect(shipmentsServiceSpy.advanceShipmentStatus).toHaveBeenCalledWith(
      'SHIP-1'
    );
    expect(shipmentsServiceSpy.listShipments).toHaveBeenCalledTimes(2);
  });

  it('sets an error message when advancing fails', () => {
    setup(['SHIPMENT_READ', 'SHIPMENT_WRITE']);
    shipmentsServiceSpy.advanceShipmentStatus.and.returnValue(
      throwError(() => new Error('failed'))
    );

    component.advance('SHIP-1');

    expect(component.actionErrorMessage()).toBe(
      'Failed to advance shipment status.'
    );
  });
});
