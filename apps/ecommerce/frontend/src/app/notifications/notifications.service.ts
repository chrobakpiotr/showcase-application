import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@environments/environment';
import {
  NotificationCollectionModel,
  NotificationModel,
} from '@app/notifications/notification.model';

@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private readonly httpClient = inject(HttpClient);

  listNotifications(
    page?: number,
    size?: number
  ): Observable<NotificationCollectionModel> {
    const suffix = page === undefined ? '' : `?page=${page}&size=${size ?? 20}`;
    return this.httpClient.get<NotificationCollectionModel>(
      `${environment.apiPrefix}/notifications${suffix}`
    );
  }

  listNotificationsByStatus(
    status: string,
    page?: number,
    size?: number
  ): Observable<NotificationCollectionModel> {
    const suffix = page === undefined ? '' : `?page=${page}&size=${size ?? 20}`;
    return this.httpClient.get<NotificationCollectionModel>(
      `${environment.apiPrefix}/notifications/status/${status}${suffix}`
    );
  }

  getNotification(notificationId: string): Observable<NotificationModel> {
    return this.httpClient.get<NotificationModel>(
      `${environment.apiPrefix}/notifications/${notificationId}`
    );
  }
}
