import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';

import { AuthService } from '@app/auth/auth.service';
import { NotificationModel } from '@app/notifications/notification.model';
import { NotificationsComponent } from '@app/notifications/notifications.component';
import { NotificationsService } from '@app/notifications/notifications.service';

describe('NotificationsComponent', () => {
  let fixture: ComponentFixture<NotificationsComponent>;
  let component: NotificationsComponent;
  let notificationsServiceSpy: jasmine.SpyObj<NotificationsService>;

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

  function setup(roles: string[] = []): void {
    notificationsServiceSpy = jasmine.createSpyObj('NotificationsService', [
      'listNotifications',
      'listNotificationsByStatus',
      'getNotification',
    ]);
    notificationsServiceSpy.listNotifications.and.returnValue(
      of({ _embedded: { notificationResourceList: [notification] } })
    );
    notificationsServiceSpy.listNotificationsByStatus.and.returnValue(
      of({ _embedded: { notificationResourceList: [notification] } })
    );

    TestBed.configureTestingModule({
      imports: [NotificationsComponent],
      providers: [
        { provide: NotificationsService, useValue: notificationsServiceSpy },
        { provide: AuthService, useValue: { roles: signal(roles) } },
      ],
    });

    fixture = TestBed.createComponent(NotificationsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should create the component', () => {
    setup();
    expect(component).toBeTruthy();
  });

  it('does not load notifications without the NOTIFICATION_READ role', () => {
    setup();
    expect(notificationsServiceSpy.listNotifications).not.toHaveBeenCalled();
    expect(component.canRead).toBeFalse();
  });

  it('loads notifications with the NOTIFICATION_READ role', () => {
    setup(['NOTIFICATION_READ']);
    expect(notificationsServiceSpy.listNotifications).toHaveBeenCalled();
    expect(component.notifications()).toEqual([notification]);
  });

  it('defaults the list to an empty array when embedded collection is missing', () => {
    notificationsServiceSpy = jasmine.createSpyObj('NotificationsService', [
      'listNotifications',
      'listNotificationsByStatus',
      'getNotification',
    ]);
    notificationsServiceSpy.listNotifications.and.returnValue(of({}));
    notificationsServiceSpy.listNotificationsByStatus.and.returnValue(of({}));

    TestBed.configureTestingModule({
      imports: [NotificationsComponent],
      providers: [
        { provide: NotificationsService, useValue: notificationsServiceSpy },
        {
          provide: AuthService,
          useValue: { roles: signal(['NOTIFICATION_READ']) },
        },
      ],
    });

    fixture = TestBed.createComponent(NotificationsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.notifications()).toEqual([]);
  });

  it('loads notifications filtered by status', () => {
    setup(['NOTIFICATION_READ']);

    component.updateStatusFilter('SENT');

    expect(
      notificationsServiceSpy.listNotificationsByStatus
    ).toHaveBeenCalledWith('SENT', 0, 20);
    expect(component.selectedStatus()).toBe('SENT');
  });

  it('sets an error message when loading notifications fails', () => {
    notificationsServiceSpy = jasmine.createSpyObj('NotificationsService', [
      'listNotifications',
      'listNotificationsByStatus',
      'getNotification',
    ]);
    notificationsServiceSpy.listNotifications.and.returnValue(
      throwError(() => new Error('failed'))
    );
    notificationsServiceSpy.listNotificationsByStatus.and.returnValue(
      of({ _embedded: { notificationResourceList: [] } })
    );
    TestBed.configureTestingModule({
      imports: [NotificationsComponent],
      providers: [
        { provide: NotificationsService, useValue: notificationsServiceSpy },
        {
          provide: AuthService,
          useValue: { roles: signal(['NOTIFICATION_READ']) },
        },
      ],
    });
    fixture = TestBed.createComponent(NotificationsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.errorMessage()).toBe('Failed to load notifications.');
  });

  it('covers notification pagination in both directions and at boundaries', () => {
    setup(['NOTIFICATION_READ']);
    notificationsServiceSpy.listNotifications.calls.reset();

    component.page.set(0);
    component.totalPages.set(3);
    component.previousPage();
    expect(notificationsServiceSpy.listNotifications).not.toHaveBeenCalled();

    component.nextPage();
    expect(component.page()).toBe(1);
    expect(notificationsServiceSpy.listNotifications).toHaveBeenCalledWith(
      1,
      20
    );

    component.previousPage();
    expect(component.page()).toBe(0);
    expect(notificationsServiceSpy.listNotifications).toHaveBeenCalledWith(
      0,
      20
    );

    notificationsServiceSpy.listNotifications.calls.reset();
    component.page.set(2);
    component.totalPages.set(3);
    component.nextPage();
    expect(component.page()).toBe(2);
    expect(notificationsServiceSpy.listNotifications).not.toHaveBeenCalled();
  });
});
