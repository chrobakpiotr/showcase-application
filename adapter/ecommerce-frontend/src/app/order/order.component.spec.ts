import { HttpErrorResponse } from '@angular/common/http';
import {
  ComponentFixture,
  TestBed,
  fakeAsync,
  tick,
} from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { OrderComponent } from '@app/order/order.component';
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
    });
  }

  afterEach(() => {
    TestBed.resetTestingModule();
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

  it('should call orderService.placeOrder() with remarks, customer and line items', fakeAsync(() => {
    setup();
    placeOrderSpy.and.returnValue(of({ orderNumber: '123' }));
    fillValidForm('my remarks');
    component.placeOrder();
    tick();
    expect(placeOrderSpy).toHaveBeenCalledWith('my remarks', VALID_CUSTOMER, [
      VALID_ITEM,
    ]);
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

  it('on success with empty orderNumber: shows "You already have an order." message', fakeAsync(() => {
    setup();
    placeOrderSpy.and.returnValue(of({ orderNumber: '' }));
    fillValidForm('test');
    component.placeOrder();
    tick();
    fixture.detectChanges();
    expect(component.errorMessage()).toBe('You already have an order.');
    const compiled = fixture.nativeElement as HTMLElement;
    const alert = compiled.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('You already have an order.');
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
});
