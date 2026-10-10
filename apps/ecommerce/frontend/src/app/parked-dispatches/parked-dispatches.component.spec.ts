import type { MockedObject } from "vitest";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { signal } from "@angular/core";
import { of, throwError } from "rxjs";

import { AuthService } from "@app/auth/auth.service";
import {
  ParkedDispatchPage,
  ParkedDispatchesService,
} from "@app/parked-dispatches/parked-dispatches.service";
import { ParkedDispatchesComponent } from "@app/parked-dispatches/parked-dispatches.component";
import { asMockedObject } from "../../test-support/mock-object";

describe("ParkedDispatchesComponent", () => {
  let fixture: ComponentFixture<ParkedDispatchesComponent>;
  let component: ParkedDispatchesComponent;
  let service: MockedObject<ParkedDispatchesService>;

  const page: ParkedDispatchPage = {
    content: [
      {
        dispatchId: "dispatch-1",
        orderNumber: "ORDER-1",
        dispatchType: "CONFIRMATION_EMAIL",
        status: "PARKED",
        attempts: 8,
        createdAt: "2026-10-05T12:00:00Z",
        nextAttemptAt: null,
        reasonCode: "ATTEMPT_BUDGET_EXHAUSTED",
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    oldestAgeSeconds: 120,
  };

  function setup(roles: string[] = ["ORDER_READ"]): void {
    service = asMockedObject<ParkedDispatchesService>({
      listParkedDispatches: vi
        .fn()
        .mockName("ParkedDispatchesService.listParkedDispatches"),
    });
    service.listParkedDispatches.mockReturnValue(of(page));
    TestBed.configureTestingModule({
      imports: [ParkedDispatchesComponent],
      providers: [
        { provide: ParkedDispatchesService, useValue: service },
        { provide: AuthService, useValue: { roles: signal(roles) } },
      ],
    });
    fixture = TestBed.createComponent(ParkedDispatchesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  afterEach(() => TestBed.resetTestingModule());

  it("does not request parked dispatches without ORDER_READ", () => {
    setup([]);
    expect(service.listParkedDispatches).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain(
      "You do not have access",
    );
  });

  it("loads a safe read-only page with the oldest age", () => {
    setup();
    expect(service.listParkedDispatches).toHaveBeenCalledWith(0, 20);
    expect(fixture.nativeElement.textContent).toContain(
      "Delivery attempt limit reached",
    );
    expect(fixture.nativeElement.textContent).toContain("2 minutes");
    expect(
      fixture.nativeElement.querySelector(
        '[data-testid="parked-dispatch-redrive"]',
      ),
    ).toBeNull();
  });

  it("uses generic error text and permits retry", () => {
    setup();
    service.listParkedDispatches.mockReturnValue(
      throwError(() => new Error("provider secret")),
    );
    component.retry();
    fixture.detectChanges();
    expect(component.errorMessage()).toBe("Failed to load parked dispatches.");
    expect(fixture.nativeElement.textContent).not.toContain("provider secret");
    component.retry();
    expect(service.listParkedDispatches).toHaveBeenCalledTimes(3);
  });

  it("paginates within the returned page bounds", () => {
    setup();
    component.totalPages.set(2);
    service.listParkedDispatches.mockReturnValue(
      of({ ...page, page: 1, totalPages: 2 }),
    );
    component.nextPage();
    expect(service.listParkedDispatches).toHaveBeenLastCalledWith(1, 20);
    component.previousPage();
    expect(service.listParkedDispatches).toHaveBeenLastCalledWith(0, 20);
  });

  it("renders an empty state for an empty page", () => {
    setup();
    service.listParkedDispatches.mockReturnValue(
      of({
        ...page,
        content: undefined,
        totalElements: 0,
        totalPages: 0,
      } as unknown as ParkedDispatchPage),
    );
    component.retry();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(
      "No parked dispatches need attention.",
    );
  });

  it("formats queue ages and treats invalid timestamps as unknown", () => {
    setup();
    expect(component.formatAge(null)).toBe("Unknown");
    expect(component.formatAge(Number.NaN)).toBe("Unknown");
    expect(component.formatAge(-1)).toBe("Unknown");
    expect(component.formatAge(30)).toBe("30 seconds");
    expect(component.formatAge(1)).toBe("1 second");
    expect(component.formatAge(60)).toBe("1 minute");
    expect(component.formatAge(3600)).toBe("1 hour");
    expect(component.formatAge(7200)).toBe("2 hours");
    expect(component.formatAge(86400)).toBe("1 day");
    expect(component.formatAge(172800)).toBe("2 days");
    expect(component.ageOf("not-a-date")).toBe("Unknown");
    expect(component.ageOf("2999-01-01T00:00:00Z")).toBe("0 seconds");
  });

  it("falls back to a safe reason label for an unexpected value", () => {
    setup();
    expect(component.reasonLabel("UNRECOGNIZED" as never)).toBe("Other");
  });

  it("does not paginate past either boundary or while loading", () => {
    setup();
    service.listParkedDispatches.mockClear();
    component.previousPage();
    component.totalPages.set(1);
    component.nextPage();
    component.loading.set(true);
    component.totalPages.set(2);
    component.nextPage();
    expect(service.listParkedDispatches).not.toHaveBeenCalled();
    expect(component.page()).toBe(0);
  });
});
