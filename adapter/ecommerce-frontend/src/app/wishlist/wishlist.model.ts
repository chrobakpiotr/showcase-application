export interface WishlistItemModel {
  sku: string;
  productName: string;
  addedDate: string;
}

export interface WishlistModel {
  wishlistId: string;
  items: WishlistItemModel[];
  itemCount: number;
}
