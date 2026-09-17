import { Injectable } from '@angular/core';

import { OrderRequestModel } from '@app/order/order-request.model';

export const ORDER_ATTEMPT_STORAGE_KEY = 'ecommerce_order_attempt_v1';
export const ORDER_ATTEMPT_TTL_MS = 30 * 60 * 1000;

export interface OrderAttempt {
  key: string;
  payload: OrderRequestModel;
}

interface SerializedOrderRequest extends Omit<OrderRequestModel, 'created'> {
  created: string;
}

interface PersistedOrderAttempt {
  schemaVersion: 1;
  key: string;
  payload: SerializedOrderRequest;
  expiresAt: number;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

@Injectable({ providedIn: 'root' })
export class OrderAttemptStore {
  save(attempt: OrderAttempt, now = Date.now()): void {
    const stored: PersistedOrderAttempt = {
      schemaVersion: 1,
      key: attempt.key,
      payload: {
        ...attempt.payload,
        created: attempt.payload.created.toISOString(),
      },
      expiresAt: now + ORDER_ATTEMPT_TTL_MS,
    };

    sessionStorage.setItem(ORDER_ATTEMPT_STORAGE_KEY, JSON.stringify(stored));
  }

  restore(now = Date.now()): OrderAttempt | null {
    const raw = sessionStorage.getItem(ORDER_ATTEMPT_STORAGE_KEY);
    if (!raw) return null;

    try {
      const parsed: unknown = JSON.parse(raw);
      if (!this.isUsable(parsed, now)) {
        this.clear();
        return null;
      }

      return {
        key: parsed.key,
        payload: {
          ...parsed.payload,
          created: new Date(parsed.payload.created),
        },
      };
    } catch {
      this.clear();
      return null;
    }
  }

  clear(): void {
    sessionStorage.removeItem(ORDER_ATTEMPT_STORAGE_KEY);
  }

  private isUsable(
    value: unknown,
    now: number
  ): value is PersistedOrderAttempt {
    if (!isRecord(value)) return false;
    if (value['schemaVersion'] !== 1) return false;

    const key = value['key'];
    if (typeof key !== 'string') return false;
    if (!key) return false;

    const expiresAt = value['expiresAt'];
    if (typeof expiresAt !== 'number') return false;
    if (expiresAt <= now) return false;

    const payload = value['payload'];
    if (!isRecord(payload)) return false;

    const created = payload['created'];
    if (typeof created !== 'string') return false;

    return !Number.isNaN(new Date(created).getTime());
  }
}
