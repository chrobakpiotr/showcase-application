import { CustomerRequestModel } from '@app/order/customer-request.model';
import { PaymentMethod } from '@app/order/payment-method.model';

// Mirrors the backend's OrderLineItemResource (adapter/web/.../order/resource/OrderLineItemResource.java).
export interface OrderLineItemDetailsModel {
  sku: string;
  productName: string;
  unitPrice: number;
  quantity: number;
  subtotal: number;
}

// Mirrors the backend's PaymentResource, nested inside OrderDetailsResource (see ADR 0030).
export interface PaymentModel {
  status: string;
  method: PaymentMethod;
  amount: number;
  gatewayReference: string;
}

// Every order response is a Spring HATEOAS EntityModel: the resource's own fields are flattened alongside a "_links" object.
// The "cancel" link is only present while the order is still cancellable (affordance-driven, see OrderController) - the
// frontend relies on its mere presence rather than re-implementing the CONFIRMED-only business rule itself.
export interface OrderDetailsModel {
  orderNumber: string;
  status: string;
  created: string;
  remarks: string;
  customer: CustomerRequestModel;
  items: OrderLineItemDetailsModel[];
  total: number;
  paymentMethod: PaymentMethod;
  payment: PaymentModel | null;
  _links?: {
    cancel?: { href: string };
  };
}

// Mirrors Spring HATEOAS's PagedModel envelope for GET /api/order, analogous to catalog's ProductPageModel.
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
