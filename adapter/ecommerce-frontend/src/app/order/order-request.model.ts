import { CustomerRequestModel } from '@app/order/customer-request.model';
import { OrderLineItemRequestModel } from '@app/order/order-line-item-request.model';

export interface OrderRequestModel {
  remarks: string;
  created: Date;
  customer: CustomerRequestModel;
  items: OrderLineItemRequestModel[];
}
