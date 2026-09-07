import { CustomerRequestModel } from '@app/order/customer-request.model';
import { PaymentMethod } from '@app/order/payment-method.model';

export interface OrderLineItemDetailsModel {
  sku: string;
  productName: string;
  unitPrice: number;
  quantity: number;
  subtotal: number;
}

export interface PaymentModel {
  status: string;
  method: PaymentMethod;
  amount: number;
  gatewayReference: string;
}

export interface OrderDetailsModel {
  orderNumber: string;
  status: string;
  created: string;
  remarks: string;
  customer: CustomerRequestModel;
  items: OrderLineItemDetailsModel[];
  subtotal: number;
  couponCode: string | null;
  discountAmount: number;
  total: number;
  paymentMethod: PaymentMethod;
  payment: PaymentModel | null;
  _links?: {
    cancel?: { href: string };
  };
}

export interface OrderPageModel {
  _embedded?: {
    orderDetailsResourceList: OrderDetailsModel[];
  };
  page: {
    size: number;
    totalElements: number;
    totalPages: number;
    number: number;
  };
}
