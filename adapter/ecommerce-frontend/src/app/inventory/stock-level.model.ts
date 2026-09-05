// Mirrors the backend's StockLevelResource/StockAdjustmentResource
// (adapter/web/.../inventory/resource/*.java).
export interface StockLevelModel {
  sku: string;
  quantityOnHand: number;
  quantityReserved: number;
  quantityAvailable: number;
}
