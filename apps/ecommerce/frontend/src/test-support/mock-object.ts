import type { MockedObject } from 'vitest';

export function asMockedObject<T extends object>(
  value: object
): MockedObject<T> {
  return value as unknown as MockedObject<T>;
}
