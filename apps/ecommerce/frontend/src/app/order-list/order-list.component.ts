import {
  ChangeDetectionStrategy,
  Component,
  computed,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';

import { AuthService } from '@app/auth/auth.service';
import { OrderDetailsModel } from '@app/order/order-details.model';
import { OrderService } from '@app/order/order.service';
import { ReturnModel } from '@app/returns/return.model';
import { ReturnsService } from '@app/returns/returns.service';
import { ShipmentModel } from '@app/shipments/shipment.model';
import { ShipmentsService } from '@app/shipments/shipments.service';

const PAGE_SIZE = 10;

@Component({
  selector: 'app-order-list',
  templateUrl: './order-list.component.html',
  styleUrls: ['./order-list.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule],
})
export class OrderListComponent implements OnInit {
  private readonly orderService = inject(OrderService);
  private readonly returnsService = inject(ReturnsService);
  private readonly shipmentsService = inject(ShipmentsService);
  private readonly authService = inject(AuthService);

  readonly orders = signal<OrderDetailsModel[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly selectedOrder = signal<OrderDetailsModel | null>(null);
  readonly orderReturns = signal<ReturnModel[]>([]);
  readonly orderShipments = signal<ShipmentModel[]>([]);
  readonly returnErrorMessage = signal<string | null>(null);
  readonly returnSuccessMessage = signal<string | null>(null);
  readonly shipmentErrorMessage = signal<string | null>(null);
  readonly shipmentSuccessMessage = signal<string | null>(null);

  readonly returnForm = new FormGroup({
    sku: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    quantity: new FormControl<number | null>(1, {
      validators: [Validators.required, Validators.min(1)],
    }),
    reason: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(2000)],
    }),
  });

  readonly hasReturnableItems = computed(() =>
    (this.selectedOrder()?.items ?? []).some(
      (item) => this.remainingQuantity(item.sku) > 0
    )
  );

  readonly shipmentForm = new FormGroup({
    carrier: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(80)],
    }),
  });

  get canReadReturns(): boolean {
    return this.authService.roles().includes('RETURN_READ');
  }

  get canWriteReturns(): boolean {
    return this.authService.roles().includes('RETURN_WRITE');
  }

  get canReadShipments(): boolean {
    return this.authService.roles().includes('SHIPMENT_READ');
  }

  get canWriteShipments(): boolean {
    return this.authService.roles().includes('SHIPMENT_WRITE');
  }

  ngOnInit(): void {
    this.loadOrders();
  }

  selectOrder(orderNumber: string): void {
    this.errorMessage.set(null);
    this.returnErrorMessage.set(null);
    this.returnSuccessMessage.set(null);
    this.shipmentErrorMessage.set(null);
    this.shipmentSuccessMessage.set(null);
    this.orderService.findOrder(orderNumber).subscribe({
      next: (order) => {
        this.selectedOrder.set(order);
        this.orderReturns.set([]);
        this.orderShipments.set([]);
        this.initializeReturnForm(order);
        this.initializeShipmentForm();
        if (this.canReadReturns) {
          this.loadReturnsForOrder(order.orderNumber);
        } else {
          this.orderReturns.set([]);
        }
        if (this.canReadShipments) {
          this.loadShipmentsForOrder(order.orderNumber);
        } else {
          this.orderShipments.set([]);
        }
      },
      error: () => this.errorMessage.set('Failed to load order details.'),
    });
  }

  closeDetails(): void {
    this.selectedOrder.set(null);
    this.orderReturns.set([]);
    this.orderShipments.set([]);
    this.returnErrorMessage.set(null);
    this.returnSuccessMessage.set(null);
    this.shipmentErrorMessage.set(null);
    this.shipmentSuccessMessage.set(null);
  }

  cancelOrder(orderNumber: string): void {
    this.errorMessage.set(null);
    this.orderService.cancelOrder(orderNumber).subscribe({
      next: (order) => {
        this.selectedOrder.set(order);
        this.loadOrders();
        if (this.canReadReturns) {
          this.loadReturnsForOrder(order.orderNumber);
        }
        if (this.canReadShipments) {
          this.loadShipmentsForOrder(order.orderNumber);
        }
      },
      error: () => this.errorMessage.set('Failed to cancel order.'),
    });
  }

  createShipment(): void {
    const order = this.selectedOrder();
    if (!order || this.shipmentForm.invalid) return;

    this.shipmentErrorMessage.set(null);
    this.shipmentSuccessMessage.set(null);
    this.shipmentsService
      .createShipment({
        orderNumber: order.orderNumber,
        carrier: this.shipmentForm.controls.carrier.getRawValue(),
      })
      .subscribe({
        next: () => {
          this.shipmentSuccessMessage.set('Shipment created.');
          this.loadShipmentsForOrder(order.orderNumber);
        },
        error: () =>
          this.shipmentErrorMessage.set('Failed to create shipment.'),
      });
  }

  advanceShipmentStatus(shipmentNumber: string): void {
    const orderNumber = this.selectedOrder()?.orderNumber;
    if (!orderNumber) return;

    this.shipmentErrorMessage.set(null);
    this.shipmentSuccessMessage.set(null);
    const shipment = this.orderShipments().find(
      (candidate) => candidate.shipmentNumber === shipmentNumber
    );
    if (!shipment) return;

    this.shipmentsService
      .advanceShipmentStatus(
        shipmentNumber,
        crypto.randomUUID(),
        shipment.status
      )
      .subscribe({
        next: () => {
          this.shipmentSuccessMessage.set('Shipment status advanced.');
          this.loadShipmentsForOrder(orderNumber);
        },
        error: () =>
          this.shipmentErrorMessage.set('Failed to advance shipment status.'),
      });
  }

  requestReturn(): void {
    const order = this.selectedOrder();
    if (!order || this.returnForm.invalid) return;

    const { sku, quantity, reason } = this.returnForm.getRawValue();
    const requestedQuantity = quantity ?? 0;
    if (requestedQuantity > this.remainingQuantity(sku)) {
      this.returnErrorMessage.set(
        'Requested quantity exceeds remaining returnable quantity.'
      );
      return;
    }

    this.returnErrorMessage.set(null);
    this.returnSuccessMessage.set(null);
    this.returnsService
      .requestReturn({
        orderNumber: order.orderNumber,
        sku,
        quantity: requestedQuantity,
        reason,
      })
      .subscribe({
        next: () => {
          this.returnSuccessMessage.set('Return request created.');
          this.returnForm.controls.reason.setValue('');
          this.returnForm.controls.quantity.setValue(1);
          this.loadReturnsForOrder(order.orderNumber);
        },
        error: () =>
          this.returnErrorMessage.set('Failed to create return request.'),
      });
  }

  remainingQuantity(sku: string): number {
    const orderedQuantity =
      this.selectedOrder()?.items.find((item) => item.sku === sku)?.quantity ??
      0;
    const alreadyRequested = this.orderReturns()
      .filter((returnRequest) => returnRequest.sku === sku)
      .filter((returnRequest) => returnRequest.status !== 'REJECTED')
      .reduce((sum, returnRequest) => sum + returnRequest.quantity, 0);
    return Math.max(orderedQuantity - alreadyRequested, 0);
  }

  nextPage(): void {
    if (this.page() + 1 >= this.totalPages()) return;
    this.page.set(this.page() + 1);
    this.loadOrders();
  }

  previousPage(): void {
    if (this.page() === 0) return;
    this.page.set(this.page() - 1);
    this.loadOrders();
  }

  private loadOrders(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.orderService.listOrders(this.page(), PAGE_SIZE).subscribe({
      next: (page) => {
        this.loading.set(false);
        this.orders.set(page._embedded?.orderDetailsResourceList ?? []);
        this.totalPages.set(page.page.totalPages);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Failed to load orders.');
      },
    });
  }

  private initializeReturnForm(order: OrderDetailsModel): void {
    const firstReturnableItem = order.items.find(
      (item) => this.remainingQuantity(item.sku) > 0
    );
    this.returnForm.reset({
      sku: firstReturnableItem?.sku ?? order.items[0]?.sku ?? '',
      quantity: 1,
      reason: '',
    });
  }

  private initializeShipmentForm(): void {
    this.shipmentForm.reset({
      carrier: '',
    });
  }

  private loadReturnsForOrder(orderNumber: string): void {
    this.returnsService.listReturnsForOrder(orderNumber).subscribe({
      next: (page) => {
        this.orderReturns.set(page._embedded?.returnRequestResourceList ?? []);
        const selectedSku = this.returnForm.controls.sku.value;
        if (
          selectedSku &&
          this.remainingQuantity(selectedSku) === 0 &&
          this.hasReturnableItems()
        ) {
          const fallback = this.selectedOrder()?.items.find(
            (item) => this.remainingQuantity(item.sku) > 0
          );
          if (fallback) {
            this.returnForm.controls.sku.setValue(fallback.sku);
          }
        }
      },
      error: () =>
        this.returnErrorMessage.set('Failed to load return requests.'),
    });
  }

  private loadShipmentsForOrder(orderNumber: string): void {
    this.shipmentsService.listShipmentsForOrder(orderNumber).subscribe({
      next: (page) => {
        this.orderShipments.set(page._embedded?.shipmentResourceList ?? []);
      },
      error: () => this.shipmentErrorMessage.set('Failed to load shipments.'),
    });
  }
}
