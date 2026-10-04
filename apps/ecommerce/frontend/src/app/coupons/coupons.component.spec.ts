import type { MockedObject } from "vitest";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { signal } from "@angular/core";
import { of, throwError } from "rxjs";

import { AuthService } from "@app/auth/auth.service";
import { CouponsComponent } from "@app/coupons/coupons.component";
import { CouponModel } from "@app/coupons/coupon.model";
import { CouponsService } from "@app/coupons/coupons.service";
import { asMockedObject } from "../../test-support/mock-object";


describe("CouponsComponent", () => {
  let fixture: ComponentFixture<CouponsComponent>;
  let component: CouponsComponent;
  let couponsServiceSpy: MockedObject<CouponsService>;

  const coupon: CouponModel = {
    code: "SAVE10",
    discountType: "PERCENTAGE",
    discountValue: 10,
    minimumOrderAmount: 50,
    maxRedemptions: 100,
    redemptionCount: 1,
    expiresAt: "2026-12-31T00:00:00.000Z",
    active: true,
  };

  function setup(roles: string[] = []): void {
    couponsServiceSpy = asMockedObject<CouponsService>({
      listCoupons: vi.fn().mockName("CouponsService.listCoupons"),
      createCoupon: vi.fn().mockName("CouponsService.createCoupon"),
      activateCoupon: vi.fn().mockName("CouponsService.activateCoupon"),
      deactivateCoupon: vi.fn().mockName("CouponsService.deactivateCoupon"),
    });
    couponsServiceSpy.listCoupons.mockReturnValue(
      of({
        _embedded: { couponDetailsResourceList: [coupon] },
        page: { size: 10, totalElements: 1, totalPages: 1, number: 0 },
      }),
    );
    couponsServiceSpy.createCoupon.mockReturnValue(of(coupon));
    couponsServiceSpy.activateCoupon.mockReturnValue(
      of({ ...coupon, active: true }),
    );
    couponsServiceSpy.deactivateCoupon.mockReturnValue(
      of({ ...coupon, active: false }),
    );
    TestBed.configureTestingModule({
      imports: [CouponsComponent],
      providers: [
        { provide: CouponsService, useValue: couponsServiceSpy },
        { provide: AuthService, useValue: { roles: signal(roles) } },
      ],
    });
    fixture = TestBed.createComponent(CouponsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it("should create the component", () => {
    setup();
    expect(component).toBeTruthy();
  });

  it("does not load coupons without read role", () => {
    setup();
    expect(couponsServiceSpy.listCoupons).not.toHaveBeenCalled();
    expect(component.canRead).toBe(false);
    expect(component.canWrite).toBe(false);
  });

  it("loads coupons with read role", () => {
    setup(["COUPON_READ"]);
    expect(couponsServiceSpy.listCoupons).toHaveBeenCalled();
    expect(component.coupons()).toEqual([coupon]);
  });

  it("falls back to an empty list when response has no embedded coupons", () => {
    setup(["COUPON_READ"]);
    couponsServiceSpy.listCoupons.mockClear();
    couponsServiceSpy.listCoupons.mockReturnValue(
      of({ page: { size: 10, totalElements: 0, totalPages: 0, number: 0 } }),
    );

    component.loadCoupons();

    expect(component.coupons()).toEqual([]);
  });

  it("sets error on load failure", () => {
    couponsServiceSpy = asMockedObject<CouponsService>({
      listCoupons: vi.fn().mockName("CouponsService.listCoupons"),
      createCoupon: vi.fn().mockName("CouponsService.createCoupon"),
      activateCoupon: vi.fn().mockName("CouponsService.activateCoupon"),
      deactivateCoupon: vi.fn().mockName("CouponsService.deactivateCoupon"),
    });
    couponsServiceSpy.listCoupons.mockReturnValue(
      throwError(() => new Error("failed")),
    );
    TestBed.configureTestingModule({
      imports: [CouponsComponent],
      providers: [
        { provide: CouponsService, useValue: couponsServiceSpy },
        { provide: AuthService, useValue: { roles: signal(["COUPON_READ"]) } },
      ],
    });
    fixture = TestBed.createComponent(CouponsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    expect(component.errorMessage()).toBe("Failed to load coupons.");
  });

  it("creates a coupon with write role", () => {
    setup(["COUPON_READ", "COUPON_WRITE"]);
    component.createForm.setValue({
      code: "save10",
      discountType: "PERCENTAGE",
      discountValue: 10,
      minimumOrderAmount: null,
      maxRedemptions: null,
      expiresAt: "",
      active: true,
    });

    component.createCoupon();

    expect(couponsServiceSpy.createCoupon).toHaveBeenCalledWith(
      expect.objectContaining({ code: "SAVE10", expiresAt: null }),
    );
    expect(component.createSuccess()).toBe(true);
    expect(component.createForm.getRawValue()).toEqual({
      code: "",
      discountType: "PERCENTAGE",
      discountValue: 10,
      minimumOrderAmount: null,
      maxRedemptions: null,
      expiresAt: "",
      active: true,
    });
  });

  it("converts expiresAt to iso string when creating a coupon", () => {
    setup(["COUPON_READ", "COUPON_WRITE"]);
    component.createForm.setValue({
      code: "save10",
      discountType: "PERCENTAGE",
      discountValue: 10,
      minimumOrderAmount: null,
      maxRedemptions: null,
      expiresAt: "2026-12-31T00:00",
      active: true,
    });

    component.createCoupon();

    expect(couponsServiceSpy.createCoupon).toHaveBeenCalledWith(
      expect.objectContaining({
        expiresAt: new Date("2026-12-31T00:00").toISOString(),
      }),
    );
  });

  it("does not create when user cannot write", () => {
    setup(["COUPON_READ"]);
    component.createForm.setValue({
      code: "SAVE10",
      discountType: "PERCENTAGE",
      discountValue: 10,
      minimumOrderAmount: null,
      maxRedemptions: null,
      expiresAt: "",
      active: true,
    });

    component.createCoupon();

    expect(couponsServiceSpy.createCoupon).not.toHaveBeenCalled();
  });

  it("does not create when invalid", () => {
    setup(["COUPON_READ", "COUPON_WRITE"]);
    component.createForm.controls.code.setValue("");
    component.createCoupon();
    expect(couponsServiceSpy.createCoupon).not.toHaveBeenCalled();
  });

  it("sets error on create failure", () => {
    setup(["COUPON_READ", "COUPON_WRITE"]);
    couponsServiceSpy.createCoupon.mockReturnValue(
      throwError(() => new Error("failed")),
    );
    component.createForm.setValue({
      code: "SAVE10",
      discountType: "PERCENTAGE",
      discountValue: 10,
      minimumOrderAmount: null,
      maxRedemptions: null,
      expiresAt: "",
      active: true,
    });
    component.createCoupon();
    expect(component.errorMessage()).toBe("Failed to create coupon.");
  });

  it("deactivates an active coupon", () => {
    setup(["COUPON_READ", "COUPON_WRITE"]);
    component.toggleActive(coupon);
    expect(couponsServiceSpy.deactivateCoupon).toHaveBeenCalledWith("SAVE10");
  });

  it("activates an inactive coupon", () => {
    setup(["COUPON_READ", "COUPON_WRITE"]);
    component.toggleActive({ ...coupon, active: false });
    expect(couponsServiceSpy.activateCoupon).toHaveBeenCalledWith("SAVE10");
  });

  it("does not toggle when user cannot write", () => {
    setup(["COUPON_READ"]);
    component.toggleActive(coupon);
    expect(couponsServiceSpy.activateCoupon).not.toHaveBeenCalled();
    expect(couponsServiceSpy.deactivateCoupon).not.toHaveBeenCalled();
  });

  it("sets error on toggle failure", () => {
    setup(["COUPON_READ", "COUPON_WRITE"]);
    couponsServiceSpy.deactivateCoupon.mockReturnValue(
      throwError(() => new Error("failed")),
    );
    component.toggleActive(coupon);
    expect(component.errorMessage()).toBe("Failed to update coupon status.");
  });
});
