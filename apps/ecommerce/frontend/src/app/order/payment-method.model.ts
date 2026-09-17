// Mirrors the backend's domain.order.PaymentMethod enum (see ADR 0030).
export type PaymentMethod = 'CARD' | 'PAYPAL' | 'BANK_TRANSFER';

export const PAYMENT_METHODS: readonly PaymentMethod[] = [
  'CARD',
  'PAYPAL',
  'BANK_TRANSFER',
];
