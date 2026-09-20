import { TestBed } from '@angular/core/testing';

import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import {
  provideHttpClient,
  withInterceptorsFromDi,
  withXhr,
} from '@angular/common/http';

import { environment } from '@environments/environment';
import {
  NotificationCollectionModel,
  NotificationModel,
} from '@app/notifications/notification.model';
import { NotificationsService } from '@app/notifications/notifications.service';

describe('NotificationsService', () => {
  let notificationsService: NotificationsService;
  let httpTestingController: HttpTestingController;

  const notification: NotificationModel = {
    notificationId: 'NOTIF-1',
    recipientEmail: 'customer@example.com',
    channel: 'EMAIL',
    type: 'ORDER_CONFIRMED',
    subject: 'Order confirmed',
    body: 'Your order was confirmed.',
    status: 'SENT',
    createdDate: '2024-03-15T10:30:00.000Z',
    sentDate: '2024-03-15T10:31:00.000Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        NotificationsService,
        provideHttpClient(withXhr(), withInterceptorsFromDi()),
        provideHttpClientTesting(),
      ],
    });
    notificationsService = TestBed.inject(NotificationsService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should be created', () => {
    expect(notificationsService).toBeTruthy();
  });

  it('lists notifications', () => {
    const response: NotificationCollectionModel = {
      _embedded: { notificationResourceList: [notification] },
    };

    notificationsService
      .listNotifications()
      .subscribe((data) => expect(data).toBe(response));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/notifications`
    );
    expect(req.request.method).toBe('GET');
    req.flush(response);
  });

  it('lists notifications by status', () => {
    const response: NotificationCollectionModel = {
      _embedded: { notificationResourceList: [notification] },
    };

    notificationsService
      .listNotificationsByStatus('SENT')
      .subscribe((data) => expect(data).toBe(response));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/notifications/status/SENT`
    );
    expect(req.request.method).toBe('GET');
    req.flush(response);
  });

  it('gets a notification by id', () => {
    notificationsService
      .getNotification('NOTIF-1')
      .subscribe((data) => expect(data).toBe(notification));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/notifications/NOTIF-1`
    );
    expect(req.request.method).toBe('GET');
    req.flush(notification);
  });

  it('covers explicit and default paging parameters for notification reads', () => {
    const response: NotificationCollectionModel = {
      _embedded: { notificationResourceList: [notification] },
    };

    notificationsService.listNotifications(2, 7).subscribe();
    let req = httpTestingController.expectOne(
      `${environment.apiPrefix}/notifications?page=2&size=7`
    );
    expect(req.request.method).toBe('GET');
    req.flush(response);

    notificationsService.listNotifications(3).subscribe();
    req = httpTestingController.expectOne(
      `${environment.apiPrefix}/notifications?page=3&size=20`
    );
    req.flush(response);

    notificationsService.listNotificationsByStatus('SENT', 4, 9).subscribe();
    req = httpTestingController.expectOne(
      `${environment.apiPrefix}/notifications/status/SENT?page=4&size=9`
    );
    req.flush(response);

    notificationsService.listNotificationsByStatus('FAILED', 5).subscribe();
    req = httpTestingController.expectOne(
      `${environment.apiPrefix}/notifications/status/FAILED?page=5&size=20`
    );
    req.flush(response);
  });
});
