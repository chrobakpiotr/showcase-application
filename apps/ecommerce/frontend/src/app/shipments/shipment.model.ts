import { HalPageMetadata } from '@app/shared/hal-page.model';

export type ShipmentStatus =
  | 'PENDING'
  | 'DISPATCHED'
  | 'IN_TRANSIT'
  | 'DELIVERED';

export interface ShipmentModel {
  shipmentNumber: string;
  orderNumber: string;
  carrier: string;
  trackingNumber: string;
  status: ShipmentStatus;
  dispatchedDate: string | null;
  estimatedDeliveryDate: string | null;
  deliveredDate: string | null;
  createdDate: string;
  _links?: {
    'advance-status'?: { href: string };
  };
}

export interface CreateShipmentModel {
  orderNumber: string;
  carrier: string;
}

export interface ShipmentCollectionModel {
  _embedded?: {
    shipmentResourceList?: ShipmentModel[];
  };
  page?: HalPageMetadata;
}
