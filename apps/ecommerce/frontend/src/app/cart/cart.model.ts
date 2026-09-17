export interface CartLineItemModel {
  sku: string;
  productName: string;
  unitPrice: number;
  quantity: number;
  subtotal: number;
}

export interface CartModel {
  cartId: string;
  items: CartLineItemModel[];
  subtotal: number;
  couponCode: string | null;
  discountAmount: number;
  total: number;
  itemCount: number;
}
