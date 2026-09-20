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
  readonly historyPage = signal(0);
  readonly historyTotalPages = signal(0);
  readonly pendingPage = signal(0);
  readonly pendingTotalPages = signal(0);
  private readonly pageSize = 20;

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

  previousHistoryPage(): void {
    if (this.historyPage() === 0) return;
    this.historyPage.update((value) => value - 1);
    this.loadReturns();
  }

  nextHistoryPage(): void {
    if (this.historyPage() + 1 >= this.historyTotalPages()) return;
    this.historyPage.update((value) => value + 1);
    this.loadReturns();
  }

  previousPendingPage(): void {
    if (this.pendingPage() === 0) return;
    this.pendingPage.update((value) => value - 1);
    this.loadPendingReturns();
  }

  nextPendingPage(): void {
    if (this.pendingPage() + 1 >= this.pendingTotalPages()) return;
    this.pendingPage.update((value) => value + 1);
    this.loadPendingReturns();
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
      .listReturns(this.historyPage(), this.pageSize)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.returns.set(page._embedded?.returnRequestResourceList ?? []);
          this.historyTotalPages.set(page.page?.totalPages ?? 0);
        },
        error: () => this.errorMessage.set('Failed to load return requests.'),
      });
  }

  private loadPendingReturns(): void {
    this.pendingSubscription.unsubscribe();
    this.loadingPending.set(true);
    this.moderationErrorMessage.set(null);
    this.pendingSubscription = this.returnsService
      .listPendingReturns(this.pendingPage(), this.pageSize)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.loadingPending.set(false);
          this.pendingReturns.set(
            page._embedded?.returnRequestResourceList ?? []
          );
          this.pendingTotalPages.set(page.page?.totalPages ?? 0);
          if (this.pendingReturns().length === 0 && this.pendingPage() > 0) {
            this.pendingPage.update((value) => value - 1);
            this.loadPendingReturns();
          }
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
