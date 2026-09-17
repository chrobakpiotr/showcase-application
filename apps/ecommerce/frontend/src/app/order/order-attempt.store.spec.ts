import {
  ORDER_ATTEMPT_STORAGE_KEY,
  ORDER_ATTEMPT_TTL_MS,
  OrderAttemptStore,
} from '@app/order/order-attempt.store';

const VALID_STORED_ATTEMPT = {
  schemaVersion: 1,
  key: 'attempt-1',
  payload: {
    remarks: 'retry me',
    created: '2026-09-17T10:00:00.000Z',
    customer: {
      fullName: 'Jane Doe',
      email: 'jane@example.com',
      phone: '',
      street: 'Main 1',
      postalCode: '00-001',
      city: 'Warsaw',
      countryCode: 'PL',
    },
    items: [
      {
        sku: 'SKU-1',
        productName: 'Mouse',
        unitPrice: 39.9,
        quantity: 1,
      },
    ],
    paymentMethod: 'CARD',
    couponCode: null,
  },
  expiresAt: 10_000,
};

describe('OrderAttemptStore', () => {
  const store = new OrderAttemptStore();

  afterEach(() => {
    sessionStorage.removeItem(ORDER_ATTEMPT_STORAGE_KEY);
  });

  it('returns null when there is no unresolved attempt', () => {
    expect(store.restore(1000)).toBeNull();
  });

  it('saves and restores the immutable request snapshot with created as a Date', () => {
    const created = new Date('2026-09-17T10:00:00.000Z');
    store.save(
      {
        key: 'attempt-1',
        payload: {
          ...VALID_STORED_ATTEMPT.payload,
          created,
          paymentMethod: 'CARD',
        },
      },
      1000
    );

    const restored = store.restore(1001);

    expect(restored?.key).toBe('attempt-1');
    expect(restored?.payload.created instanceof Date).toBeTrue();
    expect(restored?.payload.created.toISOString()).toBe(created.toISOString());
    expect(restored?.payload.items[0].sku).toBe('SKU-1');
  });

  [
    {
      description: 'a non-object value',
      value: null,
    },
    {
      description: 'an unsupported schema',
      value: { ...VALID_STORED_ATTEMPT, schemaVersion: 2 },
    },
    {
      description: 'a non-string key',
      value: { ...VALID_STORED_ATTEMPT, key: 7 },
    },
    {
      description: 'an empty key',
      value: { ...VALID_STORED_ATTEMPT, key: '' },
    },
    {
      description: 'a non-numeric expiry',
      value: { ...VALID_STORED_ATTEMPT, expiresAt: 'later' },
    },
    {
      description: 'an expired attempt',
      value: { ...VALID_STORED_ATTEMPT, expiresAt: 1000 },
    },
    {
      description: 'a missing payload object',
      value: { ...VALID_STORED_ATTEMPT, payload: null },
    },
    {
      description: 'a non-string created timestamp',
      value: {
        ...VALID_STORED_ATTEMPT,
        payload: { ...VALID_STORED_ATTEMPT.payload, created: 123 },
      },
    },
    {
      description: 'an invalid created timestamp',
      value: {
        ...VALID_STORED_ATTEMPT,
        payload: {
          ...VALID_STORED_ATTEMPT.payload,
          created: 'not-a-date',
        },
      },
    },
  ].forEach(({ description, value }) => {
    it(`clears ${description}`, () => {
      sessionStorage.setItem(ORDER_ATTEMPT_STORAGE_KEY, JSON.stringify(value));

      expect(store.restore(1000)).toBeNull();
      expect(sessionStorage.getItem(ORDER_ATTEMPT_STORAGE_KEY)).toBeNull();
    });
  });

  it('clears malformed JSON', () => {
    sessionStorage.setItem(ORDER_ATTEMPT_STORAGE_KEY, '{');

    expect(store.restore(1000)).toBeNull();
    expect(sessionStorage.getItem(ORDER_ATTEMPT_STORAGE_KEY)).toBeNull();
  });

  it('expires at the configured TTL boundary', () => {
    store.save(
      {
        key: 'attempt-ttl',
        payload: {
          ...VALID_STORED_ATTEMPT.payload,
          created: new Date(VALID_STORED_ATTEMPT.payload.created),
          paymentMethod: 'CARD',
        },
      },
      1000
    );

    expect(store.restore(1000 + ORDER_ATTEMPT_TTL_MS)).toBeNull();
  });

  it('clears an explicitly stored attempt', () => {
    sessionStorage.setItem(
      ORDER_ATTEMPT_STORAGE_KEY,
      JSON.stringify(VALID_STORED_ATTEMPT)
    );

    store.clear();

    expect(sessionStorage.getItem(ORDER_ATTEMPT_STORAGE_KEY)).toBeNull();
  });
});
