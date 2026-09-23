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
import {
  isDefinitiveShipmentAdvanceConflict,
  ShipmentsService,
} from '@app/shipments/shipments.service';

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
  private readonly pageSize = 20;
  private readonly pendingAdvanceOperations = new Map<
    string,
    { operationId: string; expectedStatus: ShipmentStatus }
  >();

  readonly shipments = signal<ShipmentModel[]>([]);
  readonly loading = signal(false);
  readonly advancingShipmentId = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly actionErrorMessage = signal<string | null>(null);
  readonly selectedStatus = signal<'ALL' | ShipmentStatus>('ALL');
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);

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
              ? this.shipmentsService.listShipments(this.page(), this.pageSize)
              : this.shipmentsService.listShipmentsByStatus(
                  status,
                  this.page(),
                  this.pageSize
                );
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
        this.totalPages.set(page.page?.totalPages ?? 0);
        this.totalElements.set(
          page.page?.totalElements ?? this.shipments().length
        );
      });
  }

  updateStatusFilter(status: 'ALL' | ShipmentStatus): void {
    this.selectedStatus.set(status);
    this.page.set(0);
    this.filterChanges.next(status);
  }

  previousPage(): void {
    if (this.page() === 0) return;
    this.page.update((value) => value - 1);
    this.filterChanges.next(this.selectedStatus());
  }

  nextPage(): void {
    if (this.page() + 1 >= this.totalPages()) return;
    this.page.update((value) => value + 1);
    this.filterChanges.next(this.selectedStatus());
  }

  advance(shipmentNumber: string): void {
    if (this.advancingShipmentId()) return;

    this.actionErrorMessage.set(null);
    this.advancingShipmentId.set(shipmentNumber);
    const current = this.shipments().find(
      (shipment) => shipment.shipmentNumber === shipmentNumber
    );
    if (!current) {
      this.advancingShipmentId.set(null);
      return;
    }

    const pending = this.pendingAdvanceOperations.get(shipmentNumber) ?? {
      operationId: crypto.randomUUID(),
      expectedStatus: current.status,
    };
    this.pendingAdvanceOperations.set(shipmentNumber, pending);

    this.shipmentsService
      .advanceShipmentStatus(
        shipmentNumber,
        pending.operationId,
        pending.expectedStatus
      )
      .pipe(
        finalize(() => this.advancingShipmentId.set(null)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: () => {
          this.pendingAdvanceOperations.delete(shipmentNumber);
          this.filterChanges.next(this.selectedStatus());
        },
        error: (error: unknown) => {
          if (isDefinitiveShipmentAdvanceConflict(error)) {
            this.pendingAdvanceOperations.delete(shipmentNumber);
            this.filterChanges.next(this.selectedStatus());
          }
          this.actionErrorMessage.set('Failed to advance shipment status.');
        },
      });
  }
}
