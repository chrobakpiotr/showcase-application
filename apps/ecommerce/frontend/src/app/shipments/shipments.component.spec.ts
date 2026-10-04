import type { MockedObject } from "vitest";
import { HttpErrorResponse } from "@angular/common/http";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { signal } from "@angular/core";
import { of, Subject, throwError } from "rxjs";

import { AuthService } from "@app/auth/auth.service";
import { ShipmentModel } from "@app/shipments/shipment.model";
import { ShipmentsComponent } from "@app/shipments/shipments.component";
import {
  ShipmentsService,
  ShipmentAdvanceOperationStore,
} from "@app/shipments/shipments.service";
import { asMockedObject } from "../../test-support/mock-object";

describe("ShipmentsComponent", () => {
  let fixture: ComponentFixture<ShipmentsComponent>;
  let component: ShipmentsComponent;
  let shipmentsServiceSpy: MockedObject<ShipmentsService>;

  const shipment: ShipmentModel = {
    shipmentNumber: "SHIP-1",
    orderNumber: "ORD-1",
    carrier: "DHL",
    trackingNumber: "DHL-1234",
    status: "PENDING",
    dispatchedDate: null,
    estimatedDeliveryDate: null,
    deliveredDate: null,
    createdDate: "2024-03-15T10:30:00.000Z",
    _links: { "advance-status": { href: "/api/shipments/SHIP-1/advance" } },
  };

  function createShipmentsServiceSpy(): MockedObject<ShipmentsService> {
    const spy = {
      listShipments: vi.fn().mockName("ShipmentsService.listShipments"),
      listShipmentsByStatus: vi
        .fn()
        .mockName("ShipmentsService.listShipmentsByStatus"),
      advanceShipmentStatus: vi
        .fn()
        .mockName("ShipmentsService.advanceShipmentStatus"),
      getOrCreatePendingAdvanceOperation: vi
        .fn()
        .mockName("ShipmentsService.getOrCreatePendingAdvanceOperation"),
      clearPendingAdvanceOperation: vi
        .fn()
        .mockName("ShipmentsService.clearPendingAdvanceOperation"),
    };
    const store = new ShipmentAdvanceOperationStore();
    spy.getOrCreatePendingAdvanceOperation.mockImplementation(
      (shipmentNumber, expectedStatus) =>
        store.getOrCreate("operator", shipmentNumber, expectedStatus),
    );
    spy.clearPendingAdvanceOperation.mockImplementation(
      (shipmentNumber, pending) =>
        store.clear(pending.username, shipmentNumber, pending.operationId),
    );

    return asMockedObject<ShipmentsService>(spy);
  }

  function setup(roles: string[] = []): void {
    shipmentsServiceSpy = createShipmentsServiceSpy();
    shipmentsServiceSpy.listShipments.mockReturnValue(
      of({ _embedded: { shipmentResourceList: [shipment] } }),
    );
    shipmentsServiceSpy.listShipmentsByStatus.mockReturnValue(
      of({ _embedded: { shipmentResourceList: [shipment] } }),
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

  beforeEach(() =>
    sessionStorage.removeItem("showcase.shipment-advance.v1:operator:SHIP-1"),
  );

  afterEach(() => {
    sessionStorage.removeItem("showcase.shipment-advance.v1:operator:SHIP-1");
    TestBed.resetTestingModule();
  });

  it("should create the component", () => {
    setup();
    expect(component).toBeTruthy();
  });

  it("does not load shipments without the SHIPMENT_READ role", () => {
    setup();
    expect(shipmentsServiceSpy.listShipments).not.toHaveBeenCalled();
    expect(component.canRead).toBe(false);
  });

  it("loads shipments with the SHIPMENT_READ role", () => {
    setup(["SHIPMENT_READ"]);
    expect(shipmentsServiceSpy.listShipments).toHaveBeenCalled();
    expect(component.shipments()).toEqual([shipment]);
  });

  it("defaults the list to an empty array when embedded collection is missing", () => {
    shipmentsServiceSpy = createShipmentsServiceSpy();
    shipmentsServiceSpy.listShipments.mockReturnValue(of({}));
    shipmentsServiceSpy.listShipmentsByStatus.mockReturnValue(of({}));

    TestBed.configureTestingModule({
      imports: [ShipmentsComponent],
      providers: [
        { provide: ShipmentsService, useValue: shipmentsServiceSpy },
        {
          provide: AuthService,
          useValue: { roles: signal(["SHIPMENT_READ"]) },
        },
      ],
    });

    fixture = TestBed.createComponent(ShipmentsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.shipments()).toEqual([]);
  });

  it("loads shipments filtered by status", () => {
    setup(["SHIPMENT_READ"]);

    component.updateStatusFilter("DISPATCHED");

    expect(shipmentsServiceSpy.listShipmentsByStatus).toHaveBeenCalledWith(
      "DISPATCHED",
      0,
      20,
    );
    expect(component.selectedStatus()).toBe("DISPATCHED");
  });

  it("reports canWrite true with the SHIPMENT_WRITE role", () => {
    setup(["SHIPMENT_WRITE"]);
    expect(component.canWrite).toBe(true);
  });

  it("sets an error message when loading shipments fails", () => {
    shipmentsServiceSpy = createShipmentsServiceSpy();
    shipmentsServiceSpy.listShipments.mockReturnValue(
      throwError(() => new Error("failed")),
    );
    shipmentsServiceSpy.listShipmentsByStatus.mockReturnValue(
      of({ _embedded: { shipmentResourceList: [] } }),
    );
    TestBed.configureTestingModule({
      imports: [ShipmentsComponent],
      providers: [
        { provide: ShipmentsService, useValue: shipmentsServiceSpy },
        {
          provide: AuthService,
          useValue: { roles: signal(["SHIPMENT_READ"]) },
        },
      ],
    });
    fixture = TestBed.createComponent(ShipmentsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.errorMessage()).toBe("Failed to load shipments.");
  });

  it("advances a shipment and reloads the list", () => {
    setup(["SHIPMENT_READ", "SHIPMENT_WRITE"]);
    shipmentsServiceSpy.advanceShipmentStatus.mockReturnValue(of(shipment));

    component.advance("SHIP-1");

    expect(shipmentsServiceSpy.advanceShipmentStatus).toHaveBeenCalled();
    expect(
      vi.mocked(shipmentsServiceSpy.advanceShipmentStatus).mock.lastCall![0],
    ).toBe("SHIP-1");
    expect(shipmentsServiceSpy.listShipments).toHaveBeenCalledTimes(2);
  });

  it("reuses the same pending operation identity after a failed shipment advance", () => {
    setup(["SHIPMENT_READ", "SHIPMENT_WRITE"]);
    shipmentsServiceSpy.advanceShipmentStatus.mockReturnValue(
      throwError(() => new Error("lost response")),
    );

    component.advance("SHIP-1");
    const firstArgs = vi.mocked(shipmentsServiceSpy.advanceShipmentStatus).mock
      .lastCall!;

    component.advance("SHIP-1");
    const secondArgs = vi.mocked(shipmentsServiceSpy.advanceShipmentStatus).mock
      .lastCall!;

    expect(shipmentsServiceSpy.advanceShipmentStatus).toHaveBeenCalledTimes(2);
    expect(firstArgs[0]).toBe("SHIP-1");
    expect(firstArgs[1]).toBeTruthy();
    expect(firstArgs[2]).toBe("PENDING");
    expect(secondArgs[1]).toBe(firstArgs[1]);
    expect(secondArgs[2]).toBe(firstArgs[2]);
  });

  it("retains unresolved operation identity after component recreation", () => {
    setup(["SHIPMENT_READ", "SHIPMENT_WRITE"]);
    shipmentsServiceSpy.advanceShipmentStatus.mockReturnValue(
      throwError(() => new Error("committed but response lost")),
    );

    component.advance("SHIP-1");
    const firstArgs = vi.mocked(shipmentsServiceSpy.advanceShipmentStatus).mock
      .lastCall!;

    fixture.destroy();
    fixture = TestBed.createComponent(ShipmentsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    component.advance("SHIP-1");
    const retryArgs = vi.mocked(shipmentsServiceSpy.advanceShipmentStatus).mock
      .lastCall!;

    expect(retryArgs[1]).toBe(firstArgs[1]);
    expect(retryArgs[2]).toBe(firstArgs[2]);
    expect(retryArgs[2]).toBe("PENDING");
  });

  it("drops a rejected shipment-page operation and retries from refreshed status after 409", () => {
    setup(["SHIPMENT_READ", "SHIPMENT_WRITE"]);

    const dispatched = { ...shipment, status: "DISPATCHED" as const };
    shipmentsServiceSpy.listShipments.mockReturnValue(
      of({ _embedded: { shipmentResourceList: [dispatched] } }),
    );
    shipmentsServiceSpy.advanceShipmentStatus
      .mockReturnValueOnce(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 409,
              statusText: "Conflict",
              error: { code: "SHIPMENT_STALE_STATUS" },
            }),
        ),
      )
      .mockReturnValueOnce(of(dispatched));

    component.advance("SHIP-1");
    const firstArgs = vi.mocked(shipmentsServiceSpy.advanceShipmentStatus).mock
      .lastCall!;

    expect(shipmentsServiceSpy.listShipments).toHaveBeenCalledTimes(2);
    expect(component.shipments()[0].status).toBe("DISPATCHED");

    component.advance("SHIP-1");
    const secondArgs = vi.mocked(shipmentsServiceSpy.advanceShipmentStatus).mock
      .lastCall!;

    expect(secondArgs[1]).not.toBe(firstArgs[1]);
    expect(secondArgs[2]).toBe("DISPATCHED");
  });

  it("preserves unknown conflicts and blocks corrupt storage without submitting", () => {
    setup(["SHIPMENT_READ", "SHIPMENT_WRITE"]);
    shipmentsServiceSpy.advanceShipmentStatus.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409 })),
    );
    component.advance("SHIP-1");
    const first = vi.mocked(shipmentsServiceSpy.advanceShipmentStatus).mock
      .lastCall!;
    component.advance("SHIP-1");
    expect(
      vi.mocked(shipmentsServiceSpy.advanceShipmentStatus).mock.lastCall!,
    ).toEqual(first);
    expect(
      shipmentsServiceSpy.clearPendingAdvanceOperation,
    ).not.toHaveBeenCalled();
    expect(shipmentsServiceSpy.listShipments).toHaveBeenCalledTimes(1);
    sessionStorage.setItem("showcase.shipment-advance.v1:operator:SHIP-1", "{");
    component.advance("SHIP-1");
    expect(shipmentsServiceSpy.advanceShipmentStatus).toHaveBeenCalledTimes(2);
    expect(component.advancingShipmentId()).toBeNull();
  });

  it("sets an error message when advancing fails", () => {
    setup(["SHIPMENT_READ", "SHIPMENT_WRITE"]);
    shipmentsServiceSpy.advanceShipmentStatus.mockReturnValue(
      throwError(() => new Error("failed")),
    );

    component.advance("SHIP-1");

    expect(component.actionErrorMessage()).toBe(
      "Failed to advance shipment status.",
    );
  });

  it("keeps the newest filter result when an older request finishes later", () => {
    setup(["SHIPMENT_READ"]);
    const stale = new Subject<{
      _embedded?: {
        shipmentResourceList?: ShipmentModel[];
      };
    }>();
    const latest = new Subject<{
      _embedded?: {
        shipmentResourceList?: ShipmentModel[];
      };
    }>();
    shipmentsServiceSpy.listShipmentsByStatus.mockImplementation((status) =>
      status === "DISPATCHED" ? stale : latest,
    );

    component.updateStatusFilter("DISPATCHED");
    component.updateStatusFilter("DELIVERED");

    stale.next({
      _embedded: {
        shipmentResourceList: [{ ...shipment, status: "DISPATCHED" }],
      },
    });
    stale.complete();
    expect(component.shipments()).toEqual([shipment]);

    const delivered = {
      ...shipment,
      shipmentNumber: "SHIP-2",
      status: "DELIVERED" as const,
    };
    latest.next({ _embedded: { shipmentResourceList: [delivered] } });
    latest.complete();

    expect(component.shipments()).toEqual([delivered]);
    expect(component.loading()).toBe(false);
  });

  it("does not advance a shipment that is not present in the current page", () => {
    setup(["SHIPMENT_READ", "SHIPMENT_WRITE"]);

    component.advance("SHIP-404");

    expect(shipmentsServiceSpy.advanceShipmentStatus).not.toHaveBeenCalled();
    expect(component.advancingShipmentId()).toBeNull();
  });

  it("blocks duplicate shipment advances until the first mutation completes", () => {
    setup(["SHIPMENT_READ", "SHIPMENT_WRITE"]);
    const pending = new Subject<ShipmentModel>();
    shipmentsServiceSpy.advanceShipmentStatus.mockReturnValue(pending);

    component.advance("SHIP-1");
    component.advance("SHIP-1");

    expect(shipmentsServiceSpy.advanceShipmentStatus).toHaveBeenCalledTimes(1);
    expect(component.advancingShipmentId()).toBe("SHIP-1");

    pending.next(shipment);
    pending.complete();

    expect(component.advancingShipmentId()).toBeNull();
  });

  it("keeps loading false after a failed filtered request", () => {
    setup(["SHIPMENT_READ"]);
    shipmentsServiceSpy.listShipmentsByStatus.mockReturnValue(
      throwError(() => new Error("failed")),
    );

    component.updateStatusFilter("DISPATCHED");

    expect(component.errorMessage()).toBe("Failed to load shipments.");
    expect(component.loading()).toBe(false);
  });

  it("covers shipment pagination in both directions and at boundaries", () => {
    setup(["SHIPMENT_READ"]);
    shipmentsServiceSpy.listShipments.mockClear();

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

    shipmentsServiceSpy.listShipments.mockClear();
    component.page.set(2);
    component.totalPages.set(3);
    component.nextPage();
    expect(component.page()).toBe(2);
    expect(shipmentsServiceSpy.listShipments).not.toHaveBeenCalled();
  });
});
