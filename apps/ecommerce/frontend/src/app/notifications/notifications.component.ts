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
import { Subject, catchError, of, startWith, switchMap, tap } from 'rxjs';

import { AuthService } from '@app/auth/auth.service';
import { NotificationModel } from '@app/notifications/notification.model';
import { NotificationsService } from '@app/notifications/notifications.service';

@Component({
  selector: 'app-notifications',
  templateUrl: './notifications.component.html',
  styleUrls: ['./notifications.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe],
})
export class NotificationsComponent implements OnInit {
  private readonly notificationsService = inject(NotificationsService);
  private readonly authService = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reload = new Subject<void>();
  private readonly pageSize = 20;

  readonly notifications = signal<NotificationModel[]>([]);
  readonly errorMessage = signal<string | null>(null);
  readonly selectedStatus = signal<string>('ALL');
  readonly loading = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);

  get canRead(): boolean {
    return this.authService.roles().includes('NOTIFICATION_READ');
  }

  ngOnInit(): void {
    if (!this.canRead) return;
    this.reload
      .pipe(
        startWith(undefined),
        tap(() => {
          this.loading.set(true);
          this.errorMessage.set(null);
        }),
        switchMap(() => {
          const request =
            this.selectedStatus() === 'ALL'
              ? this.notificationsService.listNotifications(
                  this.page(),
                  this.pageSize
                )
              : this.notificationsService.listNotificationsByStatus(
                  this.selectedStatus(),
                  this.page(),
                  this.pageSize
                );
          return request.pipe(
            catchError(() => {
              this.errorMessage.set('Failed to load notifications.');
              return of(null);
            })
          );
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((result) => {
        this.loading.set(false);
        if (result === null) return;
        this.notifications.set(
          result._embedded?.notificationResourceList ?? []
        );
        this.totalPages.set(result.page?.totalPages ?? 0);
        this.totalElements.set(
          result.page?.totalElements ?? this.notifications().length
        );
      });
  }

  updateStatusFilter(status: string): void {
    this.selectedStatus.set(status);
    this.page.set(0);
    this.reload.next();
  }

  previousPage(): void {
    if (this.page() === 0) return;
    this.page.update((value) => value - 1);
    this.reload.next();
  }

  nextPage(): void {
    if (this.page() + 1 >= this.totalPages()) return;
    this.page.update((value) => value + 1);
    this.reload.next();
  }
}
