import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
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
    this.moderationErrorMessage.set(null);
    this.returnsService.approveReturn(returnNumber).subscribe({
      next: () => {
        this.loadReturns();
        this.loadPendingReturns();
      },
      error: () =>
        this.moderationErrorMessage.set('Failed to approve return request.'),
    });
  }

  reject(returnNumber: string): void {
    this.moderationErrorMessage.set(null);
    this.returnsService.rejectReturn(returnNumber).subscribe({
      next: () => {
        this.loadReturns();
        this.loadPendingReturns();
      },
      error: () =>
        this.moderationErrorMessage.set('Failed to reject return request.'),
    });
  }

  private loadReturns(): void {
    this.errorMessage.set(null);
    this.returnsService.listReturns().subscribe({
      next: (page) =>
        this.returns.set(page._embedded?.returnRequestResourceList ?? []),
      error: () => this.errorMessage.set('Failed to load return requests.'),
    });
  }

  private loadPendingReturns(): void {
    this.moderationErrorMessage.set(null);
    this.returnsService.listPendingReturns().subscribe({
      next: (page) =>
        this.pendingReturns.set(
          page._embedded?.returnRequestResourceList ?? []
        ),
      error: () =>
        this.moderationErrorMessage.set(
          'Failed to load pending return requests.'
        ),
    });
  }
}
