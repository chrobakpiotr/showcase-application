export type ReturnStatus = 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'REFUNDED';

export interface ReturnModel {
  returnNumber: string;
  orderNumber: string;
  sku: string;
  quantity: number;
  reason: string;
  status: ReturnStatus;
  requestedDate: string;
  decidedDate: string | null;
  refundAmount: number;
  _links?: {
    approve?: { href: string };
    reject?: { href: string };
  };
}

export interface RequestReturnModel {
  orderNumber: string;
  sku: string;
  quantity: number;
  reason: string;
}

export interface ReturnCollectionModel {
  _embedded?: {
    returnRequestResourceList: ReturnModel[];
  };
}
