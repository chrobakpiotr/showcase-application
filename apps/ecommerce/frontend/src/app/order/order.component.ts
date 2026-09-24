import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  OnInit,
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
import {
  OrderAttempt,
  OrderAttemptStore,
} from '@app/order/order-attempt.store';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { OrderRecoveryTimelineEntryModel } from '@app/order/order-recovery-timeline.model';
import { OrderRequestModel } from '@app/order/order-request.model';
import { OrderService } from '@app/order/order.service';
import {
  PAYMENT_METHODS,
  PaymentMethod,
} from '@app/order/payment-method.model';
import { ProblemDetailsAdapter } from '@app/http/problem-details.adapter';
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
  imports: [ReactiveFormsModule, SupportAssistantComponent, RouterLink],
})
export class OrderComponent implements OnInit {
  private readonly orderService = inject(OrderService);
  private readonly attemptStore = inject(OrderAttemptStore);
  private readonly problemDetails = inject(ProblemDetailsAdapter);

  private readonly destroyRef = inject(DestroyRef);
  private attempt: OrderAttempt | null = null;
  readonly uncertain = signal(false);
  readonly editingLocked = computed(
    () => this.submitting() || this.uncertain() || !!this.orderNumber()
  );

  readonly submitting = signal(false);
  readonly orderNumber = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly recoveryTimeline = signal<OrderRecoveryTimelineEntryModel[]>([]);
  readonly recoveryTimelineLoading = signal(false);
  readonly recoveryTimelineError = signal<string | null>(null);
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

  ngOnInit(): void {
    const restored = this.attemptStore.restore();
    if (!restored) return;

    try {
      this.restoreAttemptForm(restored.payload);
    } catch {
      this.attemptStore.clear();
      return;
    }

    this.attempt = restored;
    this.uncertain.set(true);
    this.errorMessage.set(
      'Recovered an unresolved order attempt. Retry this same attempt.'
    );
  }

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
    if (this.editingLocked()) return;
    this.itemsFormArray.push(createLineItemGroup());
  }

  removeItem(index: number): void {
    if (this.editingLocked()) return;
    if (this.itemsFormArray.length > 1) {
      this.itemsFormArray.removeAt(index);
    }
  }

  fillDemoOrder(): void {
    if (this.editingLocked()) return;
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

  newOrder(): void {
    if (this.submitting() || !this.orderNumber()) return;
    this.attempt = null;
    this.attemptStore.clear();
    this.orderNumber.set(null);
    this.errorMessage.set(null);
    this.uncertain.set(false);
    this.recoveryTimeline.set([]);
    this.recoveryTimelineError.set(null);
  }

  placeOrder(): void {
    if (this.submitting() || this.orderNumber()) return;
    if (!this.attempt) {
      if (this.orderForm.invalid) return;
      const { remarks, customer, items, paymentMethod, couponCode } =
        this.orderForm.getRawValue();
      this.attempt = {
        key: crypto.randomUUID(),
        payload: structuredClone({
          remarks,
          customer,
          items: items as OrderLineItemRequestModel[],
          paymentMethod,
          couponCode: couponCode || null,
          created: new Date(),
        }),
      };
      this.attemptStore.save(this.attempt);
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.orderService
      .placeOrder(this.attempt.payload, this.attempt.key)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.submitting.set(false);
          if (
            typeof response?.orderNumber !== 'string' ||
            !response.orderNumber.trim()
          ) {
            this.uncertain.set(true);
            this.errorMessage.set(
              'Order outcome is unknown. Retry this same attempt.'
            );
          } else {
            this.uncertain.set(false);
            this.orderNumber.set(response.orderNumber);
            this.attemptStore.clear();
            this.loadRecoveryTimeline(response.orderNumber);
          }
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          const problemType = this.problemDetails.type(error);
          if (
            !this.uncertain() &&
            error instanceof HttpErrorResponse &&
            ([400, 401, 403, 404, 422, 429].includes(error.status) ||
              (error.status === 409 &&
                problemType !== null &&
                [
                  'urn:problem-type:insufficient-stock',
                  'urn:problem-type:stock-level-conflict',
                ].includes(problemType)))
          ) {
            this.attempt = null;
            this.attemptStore.clear();
          } else {
            this.uncertain.set(true);
          }
          this.errorMessage.set(
            this.problemDetails.toMessage(
              error,
              'Failed to place order. Please try again.'
            )
          );
        },
      });
  }

  refreshRecoveryTimeline(): void {
    const currentOrderNumber = this.orderNumber();
    if (!currentOrderNumber || this.recoveryTimelineLoading()) return;
    this.loadRecoveryTimeline(currentOrderNumber);
  }

  private loadRecoveryTimeline(orderNumber: string): void {
    this.recoveryTimelineLoading.set(true);
    this.recoveryTimelineError.set(null);
    this.orderService
      .findRecoveryTimeline(orderNumber)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (timeline) => {
          this.recoveryTimelineLoading.set(false);
          this.recoveryTimeline.set(timeline.items ?? []);
        },
        error: () => {
          this.recoveryTimelineLoading.set(false);
          this.recoveryTimelineError.set(
            'Recovery timeline is temporarily unavailable.'
          );
        },
      });
  }

  private restoreAttemptForm(payload: OrderRequestModel): void {
    this.customerForm.setValue(payload.customer);
    this.itemsFormArray.clear();
    for (const item of payload.items) {
      const group = createLineItemGroup();
      group.setValue(item);
      this.itemsFormArray.push(group);
    }
    this.orderForm.controls.paymentMethod.setValue(payload.paymentMethod);
    this.orderForm.controls.couponCode.setValue(payload.couponCode ?? '');
    this.remarksControl.setValue(payload.remarks);
  }
}
