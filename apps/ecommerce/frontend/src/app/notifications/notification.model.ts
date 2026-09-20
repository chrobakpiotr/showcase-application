import { HalPageMetadata } from '@app/shared/hal-page.model';

export interface NotificationModel {
  notificationId: string;
  recipientEmail: string;
  channel: string;
  type: string;
  subject: string;
  body: string;
  status: string;
  createdDate: string;
  sentDate: string | null;
}

export interface NotificationCollectionModel {
  _embedded?: {
    notificationResourceList?: NotificationModel[];
  };
  page?: HalPageMetadata;
}
