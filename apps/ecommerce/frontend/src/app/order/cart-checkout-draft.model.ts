import { OrderLineItemRequestModel } from '@app/order/order-line-item-request.model';

export interface CartCheckoutDraft {
  readonly kind: 'cart-checkout';
  readonly items: readonly OrderLineItemRequestModel[];
  readonly couponCode: string | null;
}
