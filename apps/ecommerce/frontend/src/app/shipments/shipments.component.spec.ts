import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, Subject, throwError } from 'rxjs';

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
      'DISPATCHED',
      0,
      20
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

    expect(shipmentsServiceSpy.advanceShipmentStatus).toHaveBeenCalled();
    expect(
      shipmentsServiceSpy.advanceShipmentStatus.calls.mostRecent().args[0]
    ).toBe('SHIP-1');
    expect(shipmentsServiceSpy.listShipments).toHaveBeenCalledTimes(2);
  });

  it('reuses the same pending operation identity after a failed shipment advance', () => {
    setup(['SHIPMENT_READ', 'SHIPMENT_WRITE']);
    shipmentsServiceSpy.advanceShipmentStatus.and.returnValue(
      throwError(() => new Error('lost response'))
    );

    component.advance('SHIP-1');
    const firstArgs =
      shipmentsServiceSpy.advanceShipmentStatus.calls.mostRecent().args;

    component.advance('SHIP-1');
    const secondArgs =
      shipmentsServiceSpy.advanceShipmentStatus.calls.mostRecent().args;

    expect(shipmentsServiceSpy.advanceShipmentStatus).toHaveBeenCalledTimes(2);
    expect(firstArgs[0]).toBe('SHIP-1');
    expect(firstArgs[1]).toBeTruthy();
    expect(firstArgs[2]).toBe('PENDING');
    expect(secondArgs[1]).toBe(firstArgs[1]);
    expect(secondArgs[2]).toBe(firstArgs[2]);
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

  it('keeps the newest filter result when an older request finishes later', () => {
    setup(['SHIPMENT_READ']);
    const stale = new Subject<{
      _embedded?: { shipmentResourceList?: ShipmentModel[] };
    }>();
    const latest = new Subject<{
      _embedded?: { shipmentResourceList?: ShipmentModel[] };
    }>();
    shipmentsServiceSpy.listShipmentsByStatus.and.callFake((status) =>
      status === 'DISPATCHED' ? stale : latest
    );

    component.updateStatusFilter('DISPATCHED');
    component.updateStatusFilter('DELIVERED');

    stale.next({
      _embedded: {
        shipmentResourceList: [{ ...shipment, status: 'DISPATCHED' }],
      },
    });
    stale.complete();
    expect(component.shipments()).toEqual([shipment]);

    const delivered = {
      ...shipment,
      shipmentNumber: 'SHIP-2',
      status: 'DELIVERED' as const,
    };
    latest.next({ _embedded: { shipmentResourceList: [delivered] } });
    latest.complete();

    expect(component.shipments()).toEqual([delivered]);
    expect(component.loading()).toBeFalse();
  });

  it('does not advance a shipment that is not present in the current page', () => {
    setup(['SHIPMENT_READ', 'SHIPMENT_WRITE']);

    component.advance('SHIP-404');

    expect(shipmentsServiceSpy.advanceShipmentStatus).not.toHaveBeenCalled();
    expect(component.advancingShipmentId()).toBeNull();
  });

  it('blocks duplicate shipment advances until the first mutation completes', () => {
    setup(['SHIPMENT_READ', 'SHIPMENT_WRITE']);
    const pending = new Subject<ShipmentModel>();
    shipmentsServiceSpy.advanceShipmentStatus.and.returnValue(pending);

    component.advance('SHIP-1');
    component.advance('SHIP-1');

    expect(shipmentsServiceSpy.advanceShipmentStatus).toHaveBeenCalledTimes(1);
    expect(component.advancingShipmentId()).toBe('SHIP-1');

    pending.next(shipment);
    pending.complete();

    expect(component.advancingShipmentId()).toBeNull();
  });

  it('keeps loading false after a failed filtered request', () => {
    setup(['SHIPMENT_READ']);
    shipmentsServiceSpy.listShipmentsByStatus.and.returnValue(
      throwError(() => new Error('failed'))
    );

    component.updateStatusFilter('DISPATCHED');

    expect(component.errorMessage()).toBe('Failed to load shipments.');
    expect(component.loading()).toBeFalse();
  });

  it('covers shipment pagination in both directions and at boundaries', () => {
    setup(['SHIPMENT_READ']);
    shipmentsServiceSpy.listShipments.calls.reset();

    component.page.set(0);
    component.totalPages.set(3);
    component.previousPage();
    expect(shipmentsServiceSpy.listShipments).not.toHaveBeenCalled();

    component.nextPage();
    expect(component.page()).toBe(1);
    expect(shipmentsServiceSpy.listShipments).toHaveBeenCalledWith(1, 20);

    component.previousPage();
    expect(component.page()).toBe(0);
    expect(shipmentsServiceSpy.listShipments).toHaveBeenCalledWith(0, 20);

    shipmentsServiceSpy.listShipments.calls.reset();
    component.page.set(2);
    component.totalPages.set(3);
    component.nextPage();
    expect(component.page()).toBe(2);
    expect(shipmentsServiceSpy.listShipments).not.toHaveBeenCalled();
  });
});
