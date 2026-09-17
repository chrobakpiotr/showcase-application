// Mirrors the backend's StockLevelResource/StockAdjustmentResource
// (modules/adapters/web/.../inventory/resource/*.java).
export interface StockLevelModel {
  sku: string;
  quantityOnHand: number;
  quantityReserved: number;
  quantityAvailable: number;
}
