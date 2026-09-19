import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '@app/auth/auth.service';
import { DashboardComponent } from '@app/dashboard/dashboard.component';

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let component: DashboardComponent;

  function setup(username = 'admin', roles: string[] = []): void {
    TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: { username: signal(username), roles: signal(roles) },
        },
      ],
    });

    fixture = TestBed.createComponent(DashboardComponent);
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

  it('always shows role-agnostic cards', () => {
    setup('admin', []);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('a[href="/cart"]')).toBeTruthy();
    expect(compiled.querySelector('a[href="/wishlist"]')).toBeTruthy();
    expect(compiled.querySelector('a[href="/reviews"]')).toBeTruthy();
  });

  it('hides role-gated cards without the required role', () => {
    setup('viewer', []);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('a[href="/order"]')).toBeFalsy();
    expect(compiled.querySelector('a[href="/orders"]')).toBeFalsy();
    expect(compiled.querySelector('a[href="/analytics"]')).toBeFalsy();
    expect(compiled.querySelector('a[href="/catalog"]')).toBeFalsy();
    expect(compiled.querySelector('a[href="/recommendations"]')).toBeFalsy();
    expect(compiled.querySelector('a[href="/inventory"]')).toBeFalsy();
    expect(compiled.querySelector('a[href="/returns"]')).toBeFalsy();
    expect(compiled.querySelector('a[href="/notifications"]')).toBeFalsy();
    expect(compiled.querySelector('a[href="/shipments"]')).toBeFalsy();
    expect(compiled.querySelector('a[href="/coupons"]')).toBeFalsy();
  });

  it('shows role-gated cards with the required roles', () => {
    setup('admin', [
      'ORDER_WRITE',
      'ORDER_READ',
      'CATALOG_READ',
      'INVENTORY_READ',
      'RETURN_READ',
      'NOTIFICATION_READ',
      'SHIPMENT_READ',
      'COUPON_READ',
    ]);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('a[href="/order"]')).toBeTruthy();
    expect(compiled.querySelector('a[href="/orders"]')).toBeTruthy();
    expect(compiled.querySelector('a[href="/analytics"]')).toBeTruthy();
    expect(compiled.querySelector('a[href="/catalog"]')).toBeTruthy();
    expect(compiled.querySelector('a[href="/recommendations"]')).toBeTruthy();
    expect(compiled.querySelector('a[href="/inventory"]')).toBeTruthy();
    expect(compiled.querySelector('a[href="/returns"]')).toBeTruthy();
    expect(compiled.querySelector('a[href="/notifications"]')).toBeTruthy();
    expect(compiled.querySelector('a[href="/shipments"]')).toBeTruthy();
    expect(compiled.querySelector('a[href="/coupons"]')).toBeTruthy();
  });

  it('reports role-agnostic capabilities as visible', () => {
    setup('admin', []);
    expect(
      component.isVisible({
        path: '/cart',
        title: 'Cart',
        description: 'Cart',
      })
    ).toBeTrue();
  });

  it('supports write-only capability visibility', () => {
    setup('writer', ['ORDER_WRITE']);
    expect(
      component.isVisible({
        path: '/order',
        title: 'Place an order',
        description: 'Place an order',
        writeRole: 'ORDER_WRITE',
      })
    ).toBeTrue();
  });
});
