import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  Subject,
  catchError,
  finalize,
  of,
  startWith,
  switchMap,
  tap,
} from 'rxjs';

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
  private readonly destroyRef = inject(DestroyRef);
  private readonly filterChanges = new Subject<'ALL' | ShipmentStatus>();

  readonly shipments = signal<ShipmentModel[]>([]);
  readonly loading = signal(false);
  readonly advancingShipmentId = signal<string | null>(null);
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
    if (!this.canRead) return;

    this.filterChanges
      .pipe(
        startWith(this.selectedStatus()),
        tap(() => {
          this.loading.set(true);
          this.errorMessage.set(null);
        }),
        switchMap((status) => {
          const request =
            status === 'ALL'
              ? this.shipmentsService.listShipments()
              : this.shipmentsService.listShipmentsByStatus(status);
          return request.pipe(
            catchError(() => {
              this.errorMessage.set('Failed to load shipments.');
              return of(null);
            })
          );
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((page) => {
        this.loading.set(false);
        if (page === null) return;
        this.shipments.set(page._embedded?.shipmentResourceList ?? []);
      });
  }

  updateStatusFilter(status: 'ALL' | ShipmentStatus): void {
    this.selectedStatus.set(status);
    this.filterChanges.next(status);
  }

  advance(shipmentNumber: string): void {
    if (this.advancingShipmentId()) return;

    this.actionErrorMessage.set(null);
    this.advancingShipmentId.set(shipmentNumber);
    this.shipmentsService
      .advanceShipmentStatus(shipmentNumber)
      .pipe(
        finalize(() => this.advancingShipmentId.set(null)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: () => this.filterChanges.next(this.selectedStatus()),
        error: () =>
          this.actionErrorMessage.set('Failed to advance shipment status.'),
      });
  }
}
