import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ComponentFixture,
  TestBed,
  fakeAsync,
  tick,
} from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';

import { OrderComponent } from '@app/order/order.component';
import { ORDER_ATTEMPT_STORAGE_KEY } from '@app/order/order-attempt.store';
import { OrderService } from '@app/order/order.service';
import { CustomerRequestModel } from '@app/order/customer-request.model';
import { OrderLineItemRequestModel } from '@app/order/order-line-item-request.model';
import { SupportAssistantService } from '@app/support-assistant/support-assistant.service';

const VALID_CUSTOMER: CustomerRequestModel = {
  fullName: 'Jane Doe',
  email: 'jane.doe@example.com',
  phone: '+1 555 123 4567',
  street: 'Main Street 1',
  postalCode: '12-345',
  city: 'Warsaw',
  countryCode: 'PL',
};

const VALID_ITEM: OrderLineItemRequestModel = {
  sku: 'SKU-1234',
  productName: 'Wireless Mouse',
  unitPrice: 29.99,
  quantity: 2,
};

describe('OrderComponent', () => {
  let fixture: ComponentFixture<OrderComponent>;
  let component: OrderComponent;
  let placeOrderSpy: jasmine.Spy;

  function setup(): void {
    placeOrderSpy = jasmine.createSpy('placeOrder');
    TestBed.configureTestingModule({
      imports: [OrderComponent],
      providers: [
        {
          provide: OrderService,
          useValue: { placeOrder: placeOrderSpy },
        },
        {
          // The embedded <app-support-assistant /> widget injects SupportAssistantService, which itself needs
          // HttpClient - stubbed out here since OrderComponent's own tests aren't about the support widget.
          provide: SupportAssistantService,
          useValue: { askQuestion: jasmine.createSpy('askQuestion') },
        },
      ],
    });
    fixture = TestBed.createComponent(OrderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function fillValidForm(remarks: string): void {
    component.orderForm.setValue({
      remarks,
      customer: VALID_CUSTOMER,
      items: [VALID_ITEM],
      paymentMethod: 'CARD',
      couponCode: '',
    });
  }

  beforeEach(() => {
    sessionStorage.removeItem(ORDER_ATTEMPT_STORAGE_KEY);
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    sessionStorage.removeItem(ORDER_ATTEMPT_STORAGE_KEY);
  });

  it('should create the component', () => {
    setup();
    expect(component).toBeTruthy();
  });

  it('should not submit when form is invalid', () => {
    setup();
    component.placeOrder();
    expect(placeOrderSpy).not.toHaveBeenCalled();
  });

  it('should not submit when customer details are missing', () => {
    setup();
    component.remarksControl.setValue('valid remarks');
    component.placeOrder();
    expect(placeOrderSpy).not.toHaveBeenCalled();
  });

  it('should not submit when the only line item is incomplete', () => {
    setup();
    component.remarksControl.setValue('valid remarks');
    component.customerForm.setValue(VALID_CUSTOMER);
    component.placeOrder();
    expect(placeOrderSpy).not.toHaveBeenCalled();
  });

  it('should not submit when already submitting', () => {
    setup();
    fillValidForm('valid remarks');
    component.submitting.set(true);
    component.placeOrder();
    expect(placeOrderSpy).not.toHaveBeenCalled();
  });

  it('should call orderService.placeOrder() with remarks, customer, line items and payment method', fakeAsync(() => {
    setup();
    placeOrderSpy.and.returnValue(of({ orderNumber: '123' }));
    fillValidForm('my remarks');
    component.placeOrder();
    tick();
    expect(placeOrderSpy).toHaveBeenCalledWith(
      {
        remarks: 'my remarks',
        customer: VALID_CUSTOMER,
        items: [VALID_ITEM],
        paymentMethod: 'CARD',
        couponCode: null,
        created: jasmine.any(Date),
      },
      jasmine.any(String)
    );
  }));

  it('should add and remove line items, never dropping below one row', () => {
    setup();
    expect(component.itemGroups.length).toBe(1);

    component.addItem();
    expect(component.itemGroups.length).toBe(2);

    component.removeItem(0);
    expect(component.itemGroups.length).toBe(1);

    component.removeItem(0);
    expect(component.itemGroups.length).toBe(1);
  });

  it('on success with orderNumber: sets orderNumber signal and shows it', fakeAsync(() => {
    setup();
    placeOrderSpy.and.returnValue(of({ orderNumber: 'ORD-001' }));
    fillValidForm('test');
    component.placeOrder();
    tick();
    fixture.detectChanges();
    expect(component.orderNumber()).toBe('ORD-001');
    const compiled = fixture.nativeElement as HTMLElement;
    const orderNumberEl = compiled.querySelector(
      '[data-testid="order-number"]'
    );
    expect(orderNumberEl?.textContent).toContain('ORD-001');
  }));

  it('on success with empty orderNumber: shows "Order outcome is unknown. Retry this same attempt." message', fakeAsync(() => {
    setup();
    placeOrderSpy.and.returnValue(of({ orderNumber: '' }));
    fillValidForm('test');
    component.placeOrder();
    tick();
    fixture.detectChanges();
    expect(component.errorMessage()).toBe(
      'Order outcome is unknown. Retry this same attempt.'
    );
    const compiled = fixture.nativeElement as HTMLElement;
    const alert = compiled.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain(
      'Order outcome is unknown. Retry this same attempt.'
    );
  }));

  it('on HTTP error: shows "Failed to place order." message', fakeAsync(() => {
    setup();
    placeOrderSpy.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 500 }))
    );
    fillValidForm('test');
    component.placeOrder();
    tick();
    fixture.detectChanges();
    expect(component.errorMessage()).toBe(
      'Failed to place order. Please try again.'
    );
    const compiled = fixture.nativeElement as HTMLElement;
    const alert = compiled.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain(
      'Failed to place order. Please try again.'
    );
  }));

  it('shows loading indicator while submitting', () => {
    setup();
    fillValidForm('test');
    component.submitting.set(true);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const loading = compiled.querySelector('.loading');
    expect(loading).toBeTruthy();
  });

  it('shows validation message when remarks is touched and empty', () => {
    setup();
    component.remarksControl.markAsTouched();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.error')?.textContent).toContain(
      'Remarks are required.'
    );
  });

  it('shows maxlength validation message when remarks exceeds 800 chars', () => {
    setup();
    component.remarksControl.setValue('a'.repeat(801));
    component.remarksControl.markAsTouched();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.error')?.textContent).toContain(
      'Remarks must be at most 800 characters.'
    );
  });

  it('shows validation message when customer email is invalid', () => {
    setup();
    component.customerForm.controls.email.setValue('not-an-email');
    component.customerForm.controls.email.markAsTouched();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.error')?.textContent).toContain(
      'Email must be a valid address.'
    );
  });

  it('shows validation message when customer street is touched and empty', () => {
    setup();
    component.customerForm.controls.street.markAsTouched();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.error')?.textContent).toContain(
      'Street address is required.'
    );
  });

  it('marks form invalid when country code is missing', () => {
    setup();
    fillValidForm('test');
    component.customerForm.controls.countryCode.setValue('');
    expect(component.orderForm.invalid).toBeTrue();
  });

  it('marks form invalid when phone does not match the expected pattern', () => {
    setup();
    fillValidForm('test');
    component.customerForm.controls.phone.setValue('not-a-phone-number!!');
    expect(component.customerForm.controls.phone.invalid).toBeTrue();
  });

  it('fills a complete seeded demo order', () => {
    setup();
    component.addItem();
    component.orderNumber.set('ORD-OLD');
    component.errorMessage.set('old error');
    component.newOrder();
    component.fillDemoOrder();

    expect(component.orderForm.valid).toBeTrue();
    expect(component.itemGroups.length).toBe(1);
    expect(component.itemGroups[0].getRawValue()).toEqual({
      sku: 'DEMO-MOUSE-001',
      productName: 'Wireless Mouse',
      unitPrice: 39.9,
      quantity: 1,
    });
    expect(component.customerForm.controls.email.value).toContain(
      'demo.buyer+'
    );
    expect(component.orderNumber()).toBeNull();
    expect(component.errorMessage()).toBeNull();
  });

  it('surfaces RFC 9457 detail when order placement fails', fakeAsync(() => {
    setup();
    placeOrderSpy.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: {
              title: 'Insufficient Stock',
              detail: 'Not enough stock for DEMO-MOUSE-001',
              errorId: 'error-123',
            },
          })
      )
    );
    fillValidForm('test');

    component.placeOrder();
    tick();

    expect(component.errorMessage()).toBe(
      'Insufficient Stock: Not enough stock for DEMO-MOUSE-001 (error id: error-123)'
    );
  }));

  [
    {
      description: 'a non-HTTP error',
      error: new Error('network failure'),
      expected: 'Failed to place order. Please try again.',
    },
    {
      description: 'a plain-text HTTP error body',
      error: new HttpErrorResponse({
        status: 400,
        error: '  Invalid order request  ',
      }),
      expected: 'Invalid order request',
    },
    {
      description: 'an empty problem object',
      error: new HttpErrorResponse({ status: 500, error: {} }),
      expected: 'Failed to place order. Please try again.',
    },
    {
      description: 'a problem with only a title',
      error: new HttpErrorResponse({
        status: 409,
        error: { title: 'Order conflict' },
      }),
      expected: 'Order conflict',
    },
    {
      description: 'a problem with only detail',
      error: new HttpErrorResponse({
        status: 422,
        error: { detail: 'Quantity must be positive' },
      }),
      expected: 'Quantity must be positive',
    },
  ].forEach(({ description, error, expected }) => {
    it(`uses the right fallback for ${description}`, fakeAsync(() => {
      setup();
      placeOrderSpy.and.returnValue(throwError(() => error));
      fillValidForm('test');

      component.placeOrder();
      tick();

      expect(component.errorMessage()).toBe(expected);
    }));
  });
  it('retains the complete attempt through unknown errors and blocks edits until replay succeeds', () => {
    setup();
    fillValidForm('original');
    const pending = new Subject<{ orderNumber: string }>();
    placeOrderSpy.and.returnValue(pending);
    component.newOrder();
    component.placeOrder();
    const first = structuredClone(placeOrderSpy.calls.mostRecent().args);
    component.placeOrder();
    component.fillDemoOrder();
    component.addItem();
    component.removeItem(0);
    component.newOrder();
    expect(placeOrderSpy).toHaveBeenCalledTimes(1);
    expect(component.remarksControl.value).toBe('original');
    expect(component.itemGroups.length).toBe(1);
    pending.error(new HttpErrorResponse({ status: 0 }));
    expect(component.uncertain()).toBeTrue();
    component.remarksControl.setValue(
      'programmatic edit cannot alter snapshot'
    );
    component.customerForm.controls.email.setValue('edited@example.com');
    component.itemGroups[0].get('quantity')!.setValue(99);
    for (const status of [409, 401, 400, 500]) {
      placeOrderSpy.and.returnValue(
        throwError(() => new HttpErrorResponse({ status }))
      );
      component.placeOrder();
      expect(placeOrderSpy.calls.mostRecent().args).toEqual(first);
      expect(component.uncertain()).toBeTrue();
      component.newOrder();
    }
    placeOrderSpy.and.returnValue(of({ orderNumber: 'ORD-replayed' }));
    component.placeOrder();
    expect(component.orderNumber()).toBe('ORD-replayed');
    expect(component.uncertain()).toBeFalse();
    const count = placeOrderSpy.calls.count();
    component.placeOrder();
    component.fillDemoOrder();
    expect(placeOrderSpy.calls.count()).toBe(count);
    component.newOrder();
    component.placeOrder();
    expect(placeOrderSpy.calls.mostRecent().args[1]).not.toBe(first[1]);
  });

  it('permits a corrected new attempt after a definitive original rejection', () => {
    setup();
    fillValidForm('invalid business input');
    component.orderForm.controls.couponCode.setValue('SAVE10');
    placeOrderSpy.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 400 }))
    );
    component.placeOrder();
    const oldKey = placeOrderSpy.calls.mostRecent().args[1];
    expect(component.uncertain()).toBeFalse();
    component.remarksControl.setValue('corrected');
    placeOrderSpy.and.returnValue(of({ orderNumber: 'ORD-new' }));
    component.placeOrder();
    expect(placeOrderSpy.calls.mostRecent().args[0].remarks).toBe('corrected');
    expect(placeOrderSpy.calls.mostRecent().args[0].couponCode).toBe('SAVE10');
    expect(placeOrderSpy.calls.mostRecent().args[1]).not.toBe(oldKey);
  });

  for (const type of [
    'urn:problem-type:insufficient-stock',
    'urn:problem-type:stock-level-conflict',
  ]) {
    it(`allows correcting an initial ${type} but preserves a previously unknown attempt`, () => {
      setup();
      fillValidForm('stock rejection');
      const rejected = new HttpErrorResponse({ status: 409, error: { type } });
      placeOrderSpy.and.returnValue(throwError(() => rejected));
      component.placeOrder();
      const oldKey = placeOrderSpy.calls.mostRecent().args[1];
      expect(component.editingLocked()).toBeFalse();
      component.itemGroups[0].get('quantity')!.setValue(2);
      placeOrderSpy.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 0 }))
      );
      component.placeOrder();
      const retry = structuredClone(placeOrderSpy.calls.mostRecent().args);
      expect(retry[1]).not.toBe(oldKey);
      expect(retry[0].items[0].quantity).toBe(2);
      placeOrderSpy.and.returnValue(throwError(() => rejected));
      component.placeOrder();
      expect(component.editingLocked()).toBeTrue();
      expect(placeOrderSpy.calls.mostRecent().args).toEqual(retry);
    });
  }

  for (const error of [
    null,
    'conflict',
    {},
    { type: 'urn:problem-type:idempotency-key-conflict' },
  ]) {
    it(`keeps an unrecognized initial 409 locked: ${JSON.stringify(
      error
    )}`, () => {
      setup();
      fillValidForm('conflict');
      placeOrderSpy.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 409, error }))
      );
      component.placeOrder();
      expect(component.uncertain()).toBeTrue();
    });
  }

  it('treats malformed successful responses as an unresolved attempt', () => {
    setup();
    fillValidForm('test');
    for (const response of [
      null,
      {},
      { orderNumber: 123 },
      { orderNumber: '  ' },
    ]) {
      placeOrderSpy.and.returnValue(of(response));
      component.placeOrder();
      expect(component.uncertain()).toBeTrue();
      expect(component.orderNumber()).toBeNull();
    }
  });

  it('restores an unresolved attempt after a component reload and reuses the same key', () => {
    setup();
    fillValidForm('survive reload');
    const pending = new Subject<{ orderNumber: string }>();
    placeOrderSpy.and.returnValue(pending);

    component.placeOrder();
    const firstKey = placeOrderSpy.calls.mostRecent().args[1] as string;
    pending.error(new HttpErrorResponse({ status: 0 }));

    expect(component.uncertain()).toBeTrue();
    expect(sessionStorage.getItem(ORDER_ATTEMPT_STORAGE_KEY)).toBeTruthy();

    fixture.destroy();
    fixture = TestBed.createComponent(OrderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.uncertain()).toBeTrue();
    expect(component.remarksControl.value).toBe('survive reload');
    expect(component.itemGroups[0].getRawValue()).toEqual(VALID_ITEM);

    placeOrderSpy.and.returnValue(of({ orderNumber: 'ORD-restored' }));
    component.placeOrder();

    expect(placeOrderSpy.calls.mostRecent().args[1]).toBe(firstKey);
    expect(component.orderNumber()).toBe('ORD-restored');
    expect(sessionStorage.getItem(ORDER_ATTEMPT_STORAGE_KEY)).toBeNull();
  });

  it('clears a structurally corrupt recovered payload instead of locking checkout', () => {
    sessionStorage.setItem(
      ORDER_ATTEMPT_STORAGE_KEY,
      JSON.stringify({
        schemaVersion: 1,
        key: 'corrupt-attempt',
        payload: {
          created: '2026-09-17T10:00:00.000Z',
        },
        expiresAt: Date.now() + 60_000,
      })
    );

    setup();

    expect(component.uncertain()).toBeFalse();
    expect(component.editingLocked()).toBeFalse();
    expect(sessionStorage.getItem(ORDER_ATTEMPT_STORAGE_KEY)).toBeNull();
  });

  it('restores a non-empty coupon code with the unresolved attempt', () => {
    sessionStorage.setItem(
      ORDER_ATTEMPT_STORAGE_KEY,
      JSON.stringify({
        schemaVersion: 1,
        key: 'coupon-attempt',
        payload: {
          remarks: 'coupon retry',
          created: '2026-09-17T10:00:00.000Z',
          customer: VALID_CUSTOMER,
          items: [VALID_ITEM],
          paymentMethod: 'CARD',
          couponCode: 'SAVE10',
        },
        expiresAt: Date.now() + 60_000,
      })
    );

    setup();

    expect(component.uncertain()).toBeTrue();
    expect(component.orderForm.controls.couponCode.value).toBe('SAVE10');
  });
});
