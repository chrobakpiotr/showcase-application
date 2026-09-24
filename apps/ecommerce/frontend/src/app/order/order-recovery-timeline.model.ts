export type OrderRecoveryTimelineState =
  | 'ACCEPTED'
  | 'PENDING'
  | 'COMPLETED'
  | 'UNKNOWN'
  | 'REJECTED'
  | 'MANUAL_REVIEW';

export interface OrderRecoveryTimelineEntryModel {
  source: string;
  type: string;
  state: OrderRecoveryTimelineState;
  occurredAt: string;
  referenceId: string;
  summary: string;
}

export interface OrderRecoveryTimelineModel {
  orderNumber: string;
  page: number;
  size: number;
  items: OrderRecoveryTimelineEntryModel[];
}
