import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import {
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';

import { OrderLineItemRequestModel } from '@app/order/order-line-item-request.model';
import { OrderService } from '@app/order/order.service';
import {
  PAYMENT_METHODS,
  PaymentMethod,
} from '@app/order/payment-method.model';
import { SupportAssistantComponent } from '@app/support-assistant/support-assistant.component';

const PHONE_PATTERN = /^$|^[- +()0-9]+$/;

const DEMO_ITEM: OrderLineItemRequestModel = {
  sku: 'DEMO-MOUSE-001',
  productName: 'Wireless Mouse',
  unitPrice: 39.9,
  quantity: 1,
};

function createLineItemGroup(): FormGroup {
  return new FormGroup({
    sku: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(40)],
    }),
    productName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(200)],
    }),
    unitPrice: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(0.01)],
    }),
    quantity: new FormControl<number | null>(1, {
      validators: [Validators.required, Validators.min(1)],
    }),
  });
}

@Component({
  selector: 'app-order',
  templateUrl: './order.component.html',
  styleUrls: ['./order.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, SupportAssistantComponent],
})
export class OrderComponent {
  private readonly orderService = inject(OrderService);

  readonly submitting = signal(false);
  readonly orderNumber = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly paymentMethods = PAYMENT_METHODS;

  readonly orderForm = new FormGroup({
    remarks: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(800)],
    }),
    customer: new FormGroup({
      fullName: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.maxLength(80)],
      }),
      email: new FormControl('', {
        nonNullable: true,
        validators: [
          Validators.required,
          Validators.email,
          Validators.maxLength(255),
        ],
      }),
      phone: new FormControl('', {
        nonNullable: true,
        validators: [
          Validators.pattern(PHONE_PATTERN),
          Validators.maxLength(25),
        ],
      }),
      street: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.maxLength(35)],
      }),
      postalCode: new FormControl('', {
        nonNullable: true,
        validators: [Validators.maxLength(35)],
      }),
      city: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.maxLength(300)],
      }),
      countryCode: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.maxLength(2)],
      }),
    }),
    items: new FormArray([createLineItemGroup()]),
    paymentMethod: new FormControl<PaymentMethod>('CARD', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    couponCode: new FormControl('', { nonNullable: true }),
  });

  get remarksControl() {
    return this.orderForm.controls.remarks;
  }

  get customerForm() {
    return this.orderForm.controls.customer;
  }

  get itemsFormArray() {
    return this.orderForm.controls.items;
  }

  get itemGroups() {
    return this.itemsFormArray.controls as FormGroup[];
  }

  addItem(): void {
    this.itemsFormArray.push(createLineItemGroup());
  }

  removeItem(index: number): void {
    if (this.itemsFormArray.length > 1) {
      this.itemsFormArray.removeAt(index);
    }
  }

  fillDemoOrder(): void {
    while (this.itemsFormArray.length > 1) {
      this.itemsFormArray.removeAt(this.itemsFormArray.length - 1);
    }

    this.customerForm.setValue({
      fullName: 'Demo Buyer',
      email: `demo.buyer+${Date.now()}@example.com`,
      phone: '+48 600 123 456',
      street: 'Demo Street 1',
      postalCode: '00-001',
      city: 'Warsaw',
      countryCode: 'PL',
    });
    this.itemGroups[0].setValue(DEMO_ITEM);
    this.orderForm.controls.paymentMethod.setValue('CARD');
    this.orderForm.controls.couponCode.setValue('');
    this.remarksControl.setValue('Demo order created from the Showcase UI');
    this.orderNumber.set(null);
    this.errorMessage.set(null);
  }

  placeOrder(): void {
    if (this.orderForm.invalid || this.submitting()) return;
    this.submitting.set(true);
    this.orderNumber.set(null);
    this.errorMessage.set(null);
    const { remarks, customer, items, paymentMethod, couponCode } =
      this.orderForm.getRawValue();
    this.orderService
      .placeOrder(
        remarks,
        customer,
        items as OrderLineItemRequestModel[],
        paymentMethod,
        couponCode || null
      )
      .subscribe({
        next: (response) => {
          this.submitting.set(false);
          if (!response.orderNumber) {
            this.errorMessage.set('You already have an order.');
          } else {
            this.orderNumber.set(response.orderNumber);
          }
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          this.errorMessage.set(this.toUserFacingError(error));
        },
      });
  }

  private toUserFacingError(error: unknown): string {
    const fallback = 'Failed to place order. Please try again.';
    if (!(error instanceof HttpErrorResponse)) return fallback;

    const body: unknown = error.error;
    if (typeof body === 'string' && body.trim()) {
      return body.trim();
    }
    if (!body || typeof body !== 'object') return fallback;

    const problem = body as Record<string, unknown>;
    const title =
      typeof problem['title'] === 'string' ? problem['title'].trim() : '';
    const detail =
      typeof problem['detail'] === 'string' ? problem['detail'].trim() : '';
    const errorId =
      typeof problem['errorId'] === 'string' ? problem['errorId'].trim() : '';

    if (!title && !detail) return fallback;

    const message = title && detail ? `${title}: ${detail}` : title || detail;
    return errorId ? `${message} (error id: ${errorId})` : message;
  }
}
