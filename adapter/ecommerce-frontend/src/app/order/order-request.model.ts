import { CustomerRequestModel } from '@app/order/customer-request.model';

export interface OrderRequestModel {
  remarks: string;
  created: Date;
  customer: CustomerRequestModel;
}
