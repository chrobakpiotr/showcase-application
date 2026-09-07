import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';

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

  readonly notifications = signal<NotificationModel[]>([]);
  readonly errorMessage = signal<string | null>(null);
  readonly selectedStatus = signal<string>('ALL');

  get canRead(): boolean {
    return this.authService.roles().includes('NOTIFICATION_READ');
  }

  ngOnInit(): void {
    if (this.canRead) {
      this.loadNotifications();
    }
  }

  updateStatusFilter(status: string): void {
    this.selectedStatus.set(status);
    this.loadNotifications();
  }

  private loadNotifications(): void {
    this.errorMessage.set(null);
    const request =
      this.selectedStatus() === 'ALL'
        ? this.notificationsService.listNotifications()
        : this.notificationsService.listNotificationsByStatus(
            this.selectedStatus()
          );
    request.subscribe({
      next: (page) =>
        this.notifications.set(page._embedded?.notificationResourceList ?? []),
      error: () => this.errorMessage.set('Failed to load notifications.'),
    });
  }
}
