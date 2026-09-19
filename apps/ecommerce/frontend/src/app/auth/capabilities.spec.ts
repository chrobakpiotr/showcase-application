import { CAPABILITIES, capabilityForPath } from '@app/auth/capabilities';

describe('capabilities', () => {
  it('uses unique paths', () => {
    const paths = CAPABILITIES.map((capability) => capability.path);
    expect(new Set(paths).size).toBe(paths.length);
  });

  it('finds a capability by path', () => {
    expect(capabilityForPath('/orders')?.title).toBe('Orders');
  });

  it('returns undefined for an unknown path', () => {
    expect(capabilityForPath('/missing')).toBeUndefined();
  });

  it('marks place order as a write capability', () => {
    expect(capabilityForPath('/order')?.writeRole).toBe('ORDER_WRITE');
  });
});
