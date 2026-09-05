// Mirrors the backend's CartLineItemResource/CartResource (adapter/web/.../cart/resource/*.java).
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
  total: number;
  itemCount: number;
}
