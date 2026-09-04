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

// Phone pattern mirrors the backend's Contact.PHONE_PATTERN ("^$|[- +()0-9]+"): either blank, or digits with
// optional spaces/parentheses/dashes/plus sign.
const PHONE_PATTERN = /^$|^[- +()0-9]+$/;

function createLineItemGroup(): FormGroup {
  // Field lengths/bounds mirror OrderLineItem's Bean Validation constraints (ValidationConstants).
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

  // Remarks max length mirrors the REMARK column in OrderEntity (length = 800). Customer/address field lengths
  // mirror the domain's Contact/Address validation constraints (ValidationConstants).
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

  placeOrder(): void {
    if (this.orderForm.invalid || this.submitting()) return;
    this.submitting.set(true);
    this.orderNumber.set(null);
    this.errorMessage.set(null);
    const { remarks, customer, items, paymentMethod } =
      this.orderForm.getRawValue();
    this.orderService
      .placeOrder(
        remarks,
        customer,
        items as OrderLineItemRequestModel[],
        paymentMethod
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
        error: () => {
          this.submitting.set(false);
          this.errorMessage.set('Failed to place order. Please try again.');
        },
      });
  }
}
