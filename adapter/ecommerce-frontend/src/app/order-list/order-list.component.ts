import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';

import { OrderDetailsModel } from '@app/order/order-details.model';
import { OrderService } from '@app/order/order.service';

const PAGE_SIZE = 10;

@Component({
  selector: 'app-order-list',
  templateUrl: './order-list.component.html',
  styleUrls: ['./order-list.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe],
})
export class OrderListComponent implements OnInit {
  private readonly orderService = inject(OrderService);

  readonly orders = signal<OrderDetailsModel[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly selectedOrder = signal<OrderDetailsModel | null>(null);

  ngOnInit(): void {
    this.loadOrders();
  }

  selectOrder(orderNumber: string): void {
    this.errorMessage.set(null);
    this.orderService.findOrder(orderNumber).subscribe({
      next: (order) => this.selectedOrder.set(order),
      error: () => this.errorMessage.set('Failed to load order details.'),
    });
  }

  closeDetails(): void {
    this.selectedOrder.set(null);
  }

  cancelOrder(orderNumber: string): void {
    this.errorMessage.set(null);
    this.orderService.cancelOrder(orderNumber).subscribe({
      next: (order) => {
        this.selectedOrder.set(order);
        this.loadOrders();
      },
      error: () => this.errorMessage.set('Failed to cancel order.'),
    });
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
}
