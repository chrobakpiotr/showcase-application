import type { Mock } from 'vitest';
import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
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
  beforeEach(() => {
    vi.useFakeTimers({ advanceTimeDelta: 1, shouldAdvanceTime: true });
  });
  afterEach(() => {
    vi.useRealTimers();
  });
  let fixture: ComponentFixture<OrderComponent>;
  let component: OrderComponent;
  let placeOrderSpy: Mock;
  let timelineSpy: Mock;

  function setup(): void {
    placeOrderSpy = vi.fn().mockName('placeOrder');
    timelineSpy = vi
      .fn()
      .mockName('findRecoveryTimeline')
      .mockReturnValue(
        of({ orderNumber: 'ORD-DEFAULT', page: 0, size: 50, items: [] })
      );
    TestBed.configureTestingModule({
      imports: [OrderComponent],
      providers: [
        {
          provide: OrderService,
          useValue: {
            placeOrder: placeOrderSpy,
            findRecoveryTimeline: timelineSpy,
          },
        },
        {
          // The embedded <app-support-assistant /> widget injects SupportAssistantService, which itself needs
          // HttpClient - stubbed out here since OrderComponent's own tests aren't about the support widget.
          provide: SupportAssistantService,
          useValue: { askQuestion: vi.fn().mockName('askQuestion') },
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

  it('should call orderService.placeOrder() with remarks, customer, line items and payment method', async () => {
    setup();
    placeOrderSpy.mockReturnValue(of({ orderNumber: '123' }));
    fillValidForm('my remarks');
    component.placeOrder();
    await vi.advanceTimersByTimeAsync(0);
    expect(placeOrderSpy).toHaveBeenCalledWith(
      {
        remarks: 'my remarks',
        customer: VALID_CUSTOMER,
        items: [VALID_ITEM],
        paymentMethod: 'CARD',
        couponCode: null,
        created: expect.any(Date),
      },
      expect.any(String)
    );
  });

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

  it('on success with orderNumber: sets orderNumber signal and shows it', async () => {
    setup();
    placeOrderSpy.mockReturnValue(of({ orderNumber: 'ORD-001' }));
    fillValidForm('test');
    component.placeOrder();
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();
    expect(component.orderNumber()).toBe('ORD-001');
    const compiled = fixture.nativeElement as HTMLElement;
    const orderNumberEl = compiled.querySelector(
      '[data-testid="order-number"]'
    );
    expect(orderNumberEl?.textContent).toContain('ORD-001');
  });

  it('loads and renders the read-only recovery timeline after placement', async () => {
    setup();
    placeOrderSpy.mockReturnValue(of({ orderNumber: 'ORD-RECOVERY' }));
    timelineSpy.mockReturnValue(
      of({
        orderNumber: 'ORD-RECOVERY',
        page: 0,
        size: 50,
        items: [
          {
            source: 'PLACEMENT_DISPATCH',
            type: 'CONFIRMATION_EMAIL',
            state: 'COMPLETED',
            occurredAt: '2026-09-24T12:00:00Z',
            referenceId: 'ORDER-CONFIRMATION:ORD-RECOVERY',
            summary: 'PLACEMENT_DISPATCH CONFIRMATION_EMAIL state SENT',
          },
        ],
      })
    );

    fillValidForm('timeline');
    component.placeOrder();
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();

    expect(timelineSpy).toHaveBeenCalledWith('ORD-RECOVERY');
    expect(component.recoveryTimeline()).toHaveLength(1);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(
      compiled.querySelector('[data-testid="recovery-timeline"]')?.textContent
    ).toContain('CONFIRMATION_EMAIL');
    expect(compiled.textContent).not.toContain('claim');
    expect(compiled.textContent).not.toContain('stack trace');
  });

  it('guards refresh without an order and while a timeline request is already running', async () => {
    setup();
    timelineSpy.mockClear();

    component.refreshRecoveryTimeline();
    await vi.advanceTimersByTimeAsync(0);
    expect(timelineSpy).not.toHaveBeenCalled();

    component.orderNumber.set('ORD-REFRESH');
    component.recoveryTimelineLoading.set(true);
    component.refreshRecoveryTimeline();
    await vi.advanceTimersByTimeAsync(0);
    expect(timelineSpy).not.toHaveBeenCalled();

    component.recoveryTimelineLoading.set(false);
    timelineSpy.mockReturnValue(
      of({
        orderNumber: 'ORD-REFRESH',
        page: 0,
        size: 50,
        items: [
          {
            source: 'FULFILLMENT',
            type: 'RABBITMQ',
            state: 'COMPLETED',
            occurredAt: '2026-09-24T12:00:00Z',
            referenceId: 'ORDER-FULFILLMENT:ORD-REFRESH',
            summary: 'FULFILLMENT RABBITMQ state RECEIVED',
          },
        ],
      })
    );

    component.refreshRecoveryTimeline();
    await vi.advanceTimersByTimeAsync(0);

    expect(timelineSpy).toHaveBeenCalledTimes(1);
    expect(timelineSpy).toHaveBeenCalledWith('ORD-REFRESH');
    expect(component.recoveryTimelineLoading()).toBe(false);
    expect(component.recoveryTimeline()).toHaveLength(1);
  });

  it('treats a missing timeline items collection as an empty projection', async () => {
    setup();
    placeOrderSpy.mockReturnValue(of({ orderNumber: 'ORD-EMPTY-TIMELINE' }));
    timelineSpy.mockReturnValue(
      of({
        orderNumber: 'ORD-EMPTY-TIMELINE',
        page: 0,
        size: 50,
        items: undefined,
      })
    );
    fillValidForm('empty timeline fallback');

    component.placeOrder();
    await vi.advanceTimersByTimeAsync(0);

    expect(component.recoveryTimelineLoading()).toBe(false);
    expect(component.recoveryTimeline()).toEqual([]);
    expect(component.recoveryTimelineError()).toBeNull();
  });

  it('surfaces a timeline read failure without changing the successful order result', async () => {
    setup();
    placeOrderSpy.mockReturnValue(of({ orderNumber: 'ORD-TIMELINE-ERROR' }));
    timelineSpy.mockReturnValue(
      throwError(() => new Error('recovery projection unavailable'))
    );
    fillValidForm('timeline error');

    component.placeOrder();
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();

    expect(component.orderNumber()).toBe('ORD-TIMELINE-ERROR');
    expect(component.recoveryTimelineLoading()).toBe(false);
    expect(component.recoveryTimeline()).toEqual([]);
    expect(component.recoveryTimelineError()).toBe(
      'Recovery timeline is temporarily unavailable.'
    );
    const compiled = fixture.nativeElement as HTMLElement;
    expect(
      compiled.querySelector('[data-testid="recovery-timeline"]')?.textContent
    ).toContain('Recovery timeline is temporarily unavailable.');
  });

  it('on success with empty orderNumber: shows "Order outcome is unknown. Retry this same attempt." message', async () => {
    setup();
    placeOrderSpy.mockReturnValue(of({ orderNumber: '' }));
    fillValidForm('test');
    component.placeOrder();
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();
    expect(component.errorMessage()).toBe(
      'Order outcome is unknown. Retry this same attempt.'
    );
    const compiled = fixture.nativeElement as HTMLElement;
    const alert = compiled.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain(
      'Order outcome is unknown. Retry this same attempt.'
    );
  });

  it('on HTTP error: shows "Failed to place order." message', async () => {
    setup();
    placeOrderSpy.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500 }))
    );
    fillValidForm('test');
    component.placeOrder();
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();
    expect(component.errorMessage()).toBe(
      'Failed to place order. Please try again.'
    );
    const compiled = fixture.nativeElement as HTMLElement;
    const alert = compiled.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain(
      'Failed to place order. Please try again.'
    );
  });

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
    expect(component.orderForm.invalid).toBe(true);
  });

  it('marks form invalid when phone does not match the expected pattern', () => {
    setup();
    fillValidForm('test');
    component.customerForm.controls.phone.setValue('not-a-phone-number!!');
    expect(component.customerForm.controls.phone.invalid).toBe(true);
  });

  it('fills a complete seeded demo order', () => {
    setup();
    component.addItem();
    component.orderNumber.set('ORD-OLD');
    component.errorMessage.set('old error');
    component.newOrder();
    component.fillDemoOrder();

    expect(component.orderForm.valid).toBe(true);
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

  it('surfaces RFC 9457 detail when order placement fails', async () => {
    setup();
    placeOrderSpy.mockReturnValue(
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
    await vi.advanceTimersByTimeAsync(0);

    expect(component.errorMessage()).toBe(
      'Insufficient Stock: Not enough stock for DEMO-MOUSE-001 (error id: error-123)'
    );
  });

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
    it(`uses the right fallback for ${description}`, async () => {
      setup();
      placeOrderSpy.mockReturnValue(throwError(() => error));
      fillValidForm('test');

      component.placeOrder();
      await vi.advanceTimersByTimeAsync(0);

      expect(component.errorMessage()).toBe(expected);
    });
  });
  it('retains the complete attempt through unknown errors and blocks edits until replay succeeds', () => {
    setup();
    fillValidForm('original');
    const pending = new Subject<{
      orderNumber: string;
    }>();
    placeOrderSpy.mockReturnValue(pending);
    component.newOrder();
    component.placeOrder();
    const first = structuredClone(vi.mocked(placeOrderSpy).mock.lastCall!);
    component.placeOrder();
    component.fillDemoOrder();
    component.addItem();
    component.removeItem(0);
    component.newOrder();
    expect(placeOrderSpy).toHaveBeenCalledTimes(1);
    expect(component.remarksControl.value).toBe('original');
    expect(component.itemGroups.length).toBe(1);
    pending.error(new HttpErrorResponse({ status: 0 }));
    expect(component.uncertain()).toBe(true);
    component.remarksControl.setValue(
      'programmatic edit cannot alter snapshot'
    );
    component.customerForm.controls.email.setValue('edited@example.com');
    component.itemGroups[0].get('quantity')!.setValue(99);
    for (const status of [409, 401, 400, 500]) {
      placeOrderSpy.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status }))
      );
      component.placeOrder();
      expect(vi.mocked(placeOrderSpy).mock.lastCall!).toEqual(first);
      expect(component.uncertain()).toBe(true);
      component.newOrder();
    }
    placeOrderSpy.mockReturnValue(of({ orderNumber: 'ORD-replayed' }));
    component.placeOrder();
    expect(component.orderNumber()).toBe('ORD-replayed');
    expect(component.uncertain()).toBe(false);
    const count = vi.mocked(placeOrderSpy).mock.calls.length;
    component.placeOrder();
    component.fillDemoOrder();
    expect(vi.mocked(placeOrderSpy).mock.calls.length).toBe(count);
    component.newOrder();
    component.placeOrder();
    expect(vi.mocked(placeOrderSpy).mock.lastCall![1]).not.toBe(first[1]);
  });

  it('permits a corrected new attempt after a definitive original rejection', () => {
    setup();
    fillValidForm('invalid business input');
    component.orderForm.controls.couponCode.setValue('SAVE10');
    placeOrderSpy.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 400 }))
    );
    component.placeOrder();
    const oldKey = vi.mocked(placeOrderSpy).mock.lastCall![1];
    expect(component.uncertain()).toBe(false);
    component.remarksControl.setValue('corrected');
    placeOrderSpy.mockReturnValue(of({ orderNumber: 'ORD-new' }));
    component.placeOrder();
    expect(vi.mocked(placeOrderSpy).mock.lastCall![0].remarks).toBe(
      'corrected'
    );
    expect(vi.mocked(placeOrderSpy).mock.lastCall![0].couponCode).toBe(
      'SAVE10'
    );
    expect(vi.mocked(placeOrderSpy).mock.lastCall![1]).not.toBe(oldKey);
  });

  for (const type of [
    'urn:problem-type:insufficient-stock',
    'urn:problem-type:stock-level-conflict',
  ]) {
    it(`allows correcting an initial ${type} but preserves a previously unknown attempt`, () => {
      setup();
      fillValidForm('stock rejection');
      const rejected = new HttpErrorResponse({ status: 409, error: { type } });
      placeOrderSpy.mockReturnValue(throwError(() => rejected));
      component.placeOrder();
      const oldKey = vi.mocked(placeOrderSpy).mock.lastCall![1];
      expect(component.editingLocked()).toBe(false);
      component.itemGroups[0].get('quantity')!.setValue(2);
      placeOrderSpy.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 0 }))
      );
      component.placeOrder();
      const retry = structuredClone(vi.mocked(placeOrderSpy).mock.lastCall!);
      expect(retry[1]).not.toBe(oldKey);
      expect(retry[0].items[0].quantity).toBe(2);
      placeOrderSpy.mockReturnValue(throwError(() => rejected));
      component.placeOrder();
      expect(component.editingLocked()).toBe(true);
      expect(vi.mocked(placeOrderSpy).mock.lastCall!).toEqual(retry);
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
      placeOrderSpy.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 409, error }))
      );
      component.placeOrder();
      expect(component.uncertain()).toBe(true);
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
      placeOrderSpy.mockReturnValue(of(response));
      component.placeOrder();
      expect(component.uncertain()).toBe(true);
      expect(component.orderNumber()).toBeNull();
    }
  });

  it('restores an unresolved attempt after a component reload and reuses the same key', () => {
    setup();
    fillValidForm('survive reload');
    const pending = new Subject<{
      orderNumber: string;
    }>();
    placeOrderSpy.mockReturnValue(pending);

    component.placeOrder();
    const firstKey = vi.mocked(placeOrderSpy).mock.lastCall![1] as string;
    pending.error(new HttpErrorResponse({ status: 0 }));

    expect(component.uncertain()).toBe(true);
    expect(sessionStorage.getItem(ORDER_ATTEMPT_STORAGE_KEY)).toBeTruthy();

    fixture.destroy();
    fixture = TestBed.createComponent(OrderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.uncertain()).toBe(true);
    expect(component.remarksControl.value).toBe('survive reload');
    expect(component.itemGroups[0].getRawValue()).toEqual(VALID_ITEM);

    placeOrderSpy.mockReturnValue(of({ orderNumber: 'ORD-restored' }));
    component.placeOrder();

    expect(vi.mocked(placeOrderSpy).mock.lastCall![1]).toBe(firstKey);
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
        expiresAt: Date.now() + 60000,
      })
    );

    setup();

    expect(component.uncertain()).toBe(false);
    expect(component.editingLocked()).toBe(false);
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
        expiresAt: Date.now() + 60000,
      })
    );

    setup();

    expect(component.uncertain()).toBe(true);
    expect(component.orderForm.controls.couponCode.value).toBe('SAVE10');
  });
});
