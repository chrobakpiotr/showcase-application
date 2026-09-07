import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';

import { AuthService } from '@app/auth/auth.service';
import { ShipmentModel, ShipmentStatus } from '@app/shipments/shipment.model';
import { ShipmentsService } from '@app/shipments/shipments.service';

@Component({
  selector: 'app-shipments',
  templateUrl: './shipments.component.html',
  styleUrls: ['./shipments.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe],
})
export class ShipmentsComponent implements OnInit {
  private readonly shipmentsService = inject(ShipmentsService);
  private readonly authService = inject(AuthService);

  readonly shipments = signal<ShipmentModel[]>([]);
  readonly errorMessage = signal<string | null>(null);
  readonly actionErrorMessage = signal<string | null>(null);
  readonly selectedStatus = signal<'ALL' | ShipmentStatus>('ALL');

  get canRead(): boolean {
    return this.authService.roles().includes('SHIPMENT_READ');
  }

  get canWrite(): boolean {
    return this.authService.roles().includes('SHIPMENT_WRITE');
  }

  ngOnInit(): void {
    if (this.canRead) {
      this.loadShipments();
    }
  }

  updateStatusFilter(status: 'ALL' | ShipmentStatus): void {
    this.selectedStatus.set(status);
    this.loadShipments();
  }

  advance(shipmentNumber: string): void {
    this.actionErrorMessage.set(null);
    this.shipmentsService.advanceShipmentStatus(shipmentNumber).subscribe({
      next: () => this.loadShipments(),
      error: () =>
        this.actionErrorMessage.set('Failed to advance shipment status.'),
    });
  }

  private loadShipments(): void {
    this.errorMessage.set(null);
    const selectedStatus = this.selectedStatus();
    const request =
      selectedStatus === 'ALL'
        ? this.shipmentsService.listShipments()
        : this.shipmentsService.listShipmentsByStatus(selectedStatus);
    request.subscribe({
      next: (page) =>
        this.shipments.set(page._embedded?.shipmentResourceList ?? []),
      error: () => this.errorMessage.set('Failed to load shipments.'),
    });
  }
}
