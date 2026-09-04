import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';

import { OrderService } from '@app/order/order.service';
import { SupportAssistantComponent } from '@app/support-assistant/support-assistant.component';

// Phone pattern mirrors the backend's Contact.PHONE_PATTERN ("^$|[- +()0-9]+"): either blank, or digits with
// optional spaces/parentheses/dashes/plus sign.
const PHONE_PATTERN = /^$|^[- +()0-9]+$/;

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
  });

  get remarksControl() {
    return this.orderForm.controls.remarks;
  }

  get customerForm() {
    return this.orderForm.controls.customer;
  }

  placeOrder(): void {
    if (this.orderForm.invalid || this.submitting()) return;
    this.submitting.set(true);
    this.orderNumber.set(null);
    this.errorMessage.set(null);
    const { remarks, customer } = this.orderForm.getRawValue();
    this.orderService.placeOrder(remarks, customer).subscribe({
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
