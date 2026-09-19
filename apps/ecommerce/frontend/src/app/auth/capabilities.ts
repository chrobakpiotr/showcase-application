export interface Capability {
  path: string;
  title: string;
  description: string;
  readRole?: string;
  writeRole?: string;
}

export const CAPABILITIES: readonly Capability[] = [
  {
    path: '/order',
    title: 'Place an order',
    description: 'Build and place an order.',
    writeRole: 'ORDER_WRITE',
  },
  {
    path: '/orders',
    title: 'Orders',
    description: 'Browse and manage orders.',
    readRole: 'ORDER_READ',
  },
  {
    path: '/analytics',
    title: 'Analytics Assistant',
    description: 'Ask questions about order analytics.',
    readRole: 'ORDER_READ',
  },
  {
    path: '/catalog',
    title: 'Catalog',
    description: 'Browse products by category.',
    readRole: 'CATALOG_READ',
  },
  {
    path: '/cart',
    title: 'Cart',
    description: 'Manage an anonymous shopping cart.',
  },
  {
    path: '/wishlist',
    title: 'Wishlist',
    description: 'Remember products for later.',
  },
  {
    path: '/recommendations',
    title: 'Personalized Recommendations',
    description: 'Get operator-authorized recommendations.',
    readRole: 'ORDER_READ',
  },
  {
    path: '/inventory',
    title: 'Inventory',
    description: 'Inspect stock levels.',
    readRole: 'INVENTORY_READ',
    writeRole: 'INVENTORY_WRITE',
  },
  {
    path: '/reviews',
    title: 'Reviews & Ratings',
    description: 'Browse reviews and moderate when authorized.',
  },
  {
    path: '/returns',
    title: 'Returns / RMA',
    description: 'Inspect and moderate returns.',
    readRole: 'RETURN_READ',
    writeRole: 'RETURN_WRITE',
  },
  {
    path: '/notifications',
    title: 'Notifications',
    description: 'Inspect notification delivery.',
    readRole: 'NOTIFICATION_READ',
  },
  {
    path: '/shipments',
    title: 'Shipping / Fulfillment Tracking',
    description: 'Inspect and advance shipments.',
    readRole: 'SHIPMENT_READ',
    writeRole: 'SHIPMENT_WRITE',
  },
  {
    path: '/coupons',
    title: 'Coupons & Discounts',
    description: 'Inspect and manage coupons.',
    readRole: 'COUPON_READ',
    writeRole: 'COUPON_WRITE',
  },
];

export function capabilityForPath(path: string): Capability | undefined {
  return CAPABILITIES.find((capability) => capability.path === path);
}
