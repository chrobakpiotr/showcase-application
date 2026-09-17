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

import { ActivatedRoute } from '@angular/router';
import { AuthService } from '@app/auth/auth.service';
import { InventoryService } from '@app/inventory/inventory.service';
import { StockLevelModel } from '@app/inventory/stock-level.model';

type StockAction = 'receive' | 'reserve' | 'release' | 'fulfill';

@Component({
  selector: 'app-inventory',
  templateUrl: './inventory.component.html',
  styleUrls: ['./inventory.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
})
export class InventoryComponent {
  private readonly inventoryService = inject(InventoryService);
  private readonly authService = inject(AuthService);

  readonly stockLevel = signal<StockLevelModel | null>(null);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly lookupForm = new FormGroup({
    sku: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  readonly adjustmentForm = new FormGroup({
    quantity: new FormControl<number | null>(1, {
      validators: [Validators.required, Validators.min(1)],
    }),
  });

  constructor() {
    this.lookupForm.controls.sku.setValue(
      inject(ActivatedRoute).snapshot.queryParamMap.get('sku') ?? ''
    );
  }

  get canWrite(): boolean {
    return this.authService.roles().includes('INVENTORY_WRITE');
  }

  lookup(): void {
    if (this.lookupForm.invalid || this.loading()) return;
    const { sku } = this.lookupForm.getRawValue();
    this.loading.set(true);
    this.stockLevel.set(null);
    this.errorMessage.set(null);
    this.inventoryService.getStockLevel(sku).subscribe({
      next: (level) => {
        this.loading.set(false);
        this.stockLevel.set(level);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Failed to load stock level.');
      },
    });
  }

  adjust(action: StockAction): void {
    if (this.adjustmentForm.invalid || !this.stockLevel() || this.loading())
      return;
    const { quantity } = this.adjustmentForm.getRawValue();
    const sku = this.stockLevel()!.sku;
    this.errorMessage.set(null);
    this.loading.set(true);

    const method = {
      receive: 'receiveStock',
      reserve: 'reserveStock',
      release: 'releaseStock',
      fulfill: 'fulfillStock',
    } as const;
    const request$ = this.inventoryService[method[action]](sku, quantity!);

    request$.subscribe({
      next: (level) => {
        this.loading.set(false);
        this.stockLevel.set(level);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set(`Failed to ${action} stock for ${sku}.`);
      },
    });
  }
}
