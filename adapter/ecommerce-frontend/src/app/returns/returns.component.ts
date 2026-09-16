import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { CurrencyPipe } from '@angular/common';

import { AuthService } from '@app/auth/auth.service';
import { ReturnModel } from '@app/returns/return.model';
import { ReturnsService } from '@app/returns/returns.service';

@Component({
  selector: 'app-returns',
  templateUrl: './returns.component.html',
  styleUrls: ['./returns.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe],
})
export class ReturnsComponent implements OnInit {
  private readonly returnsService = inject(ReturnsService);
  private readonly authService = inject(AuthService);

  private readonly destroyRef = inject(DestroyRef);
  private returnsSubscription = Subscription.EMPTY;
  private pendingSubscription = Subscription.EMPTY;
  readonly moderating = signal(false);
  readonly loadingPending = signal(false);

  readonly returns = signal<ReturnModel[]>([]);
  readonly pendingReturns = signal<ReturnModel[]>([]);
  readonly errorMessage = signal<string | null>(null);
  readonly moderationErrorMessage = signal<string | null>(null);

  get canRead(): boolean {
    return this.authService.roles().includes('RETURN_READ');
  }

  get canWrite(): boolean {
    return this.authService.roles().includes('RETURN_WRITE');
  }

  ngOnInit(): void {
    if (this.canRead) {
      this.loadReturns();
      this.loadPendingReturns();
    }
  }

  approve(returnNumber: string): void {
    if (this.moderating() || this.loadingPending()) {
      return;
    }
    this.moderating.set(true);
    this.moderationErrorMessage.set(null);
    this.returnsService
      .approveReturn(returnNumber)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.moderating.set(false);
          this.loadReturns();
          this.loadPendingReturns();
        },
        error: () => {
          this.moderating.set(false);
          this.moderationErrorMessage.set('Failed to approve return request.');
        },
      });
  }

  reject(returnNumber: string): void {
    if (this.moderating() || this.loadingPending()) {
      return;
    }
    this.moderating.set(true);
    this.moderationErrorMessage.set(null);
    this.returnsService
      .rejectReturn(returnNumber)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.moderating.set(false);
          this.loadReturns();
          this.loadPendingReturns();
        },
        error: () => {
          this.moderating.set(false);
          this.moderationErrorMessage.set('Failed to reject return request.');
        },
      });
  }

  refreshQueue(): void {
    if (this.moderating() || this.loadingPending()) {
      return;
    }
    this.loadPendingReturns();
  }

  private loadReturns(): void {
    this.returnsSubscription.unsubscribe();
    this.errorMessage.set(null);
    this.returnsSubscription = this.returnsService
      .listReturns()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) =>
          this.returns.set(page._embedded?.returnRequestResourceList ?? []),
        error: () => this.errorMessage.set('Failed to load return requests.'),
      });
  }

  private loadPendingReturns(): void {
    this.pendingSubscription.unsubscribe();
    this.loadingPending.set(true);
    this.moderationErrorMessage.set(null);
    this.pendingSubscription = this.returnsService
      .listPendingReturns()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.loadingPending.set(false);
          this.pendingReturns.set(
            page._embedded?.returnRequestResourceList ?? []
          );
        },
        error: () => {
          this.loadingPending.set(false);
          this.pendingReturns.set([]);
          this.moderationErrorMessage.set(
            'Failed to load pending return requests.'
          );
        },
      });
  }
}
