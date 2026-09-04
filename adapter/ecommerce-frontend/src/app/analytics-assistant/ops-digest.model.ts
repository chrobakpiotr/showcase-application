export interface OpsDigestModel {
  generatedDate: string;
  ordersPlacedLastDay: number;
  remarksClassificationCounts: Record<string, number>;
  narrative: string;
}
