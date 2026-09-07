import { routes } from './app.routes';

describe('app routes', () => {
  it('defines login route', async () => {
    const loginRoute = routes.find((route) => route.path === 'login');
    expect(loginRoute).toBeTruthy();
    const component = await loginRoute?.loadComponent?.();
    expect(component).toBeDefined();
  });

  it('defines guarded dashboard route', async () => {
    const dashboardRoute = routes.find((route) => route.path === 'dashboard');
    expect(dashboardRoute?.canActivate?.length).toBe(1);
    const component = await dashboardRoute?.loadComponent?.();
    expect(component).toBeDefined();
  });

  it('defines guarded order route', async () => {
    const orderRoute = routes.find((route) => route.path === 'order');
    expect(orderRoute?.canActivate?.length).toBe(1);
    const component = await orderRoute?.loadComponent?.();
    expect(component).toBeDefined();
  });

  it('defines guarded orders route', async () => {
    const ordersRoute = routes.find((route) => route.path === 'orders');
    expect(ordersRoute?.canActivate?.length).toBe(1);
    const component = await ordersRoute?.loadComponent?.();
    expect(component).toBeDefined();
  });

  it('defines guarded analytics route', async () => {
    const analyticsRoute = routes.find((route) => route.path === 'analytics');
    expect(analyticsRoute?.canActivate?.length).toBe(1);
    const component = await analyticsRoute?.loadComponent?.();
    expect(component).toBeDefined();
  });

  it('defines guarded catalog route', async () => {
    const catalogRoute = routes.find((route) => route.path === 'catalog');
    expect(catalogRoute?.canActivate?.length).toBe(1);
    const component = await catalogRoute?.loadComponent?.();
    expect(component).toBeDefined();
  });

  it('defines guarded cart route', async () => {
    const cartRoute = routes.find((route) => route.path === 'cart');
    expect(cartRoute?.canActivate?.length).toBe(1);
    const component = await cartRoute?.loadComponent?.();
    expect(component).toBeDefined();
  });

  it('defines guarded wishlist route', async () => {
    const wishlistRoute = routes.find((route) => route.path === 'wishlist');
    expect(wishlistRoute?.canActivate?.length).toBe(1);
    const component = await wishlistRoute?.loadComponent?.();
    expect(component).toBeDefined();
  });

  it('defines guarded recommendations route', async () => {
    const recommendationsRoute = routes.find(
      (route) => route.path === 'recommendations'
    );
    expect(recommendationsRoute?.canActivate?.length).toBe(1);
    const component = await recommendationsRoute?.loadComponent?.();
    expect(component).toBeDefined();
  });

  it('defines guarded inventory route', async () => {
    const inventoryRoute = routes.find((route) => route.path === 'inventory');
    expect(inventoryRoute?.canActivate?.length).toBe(1);
    const component = await inventoryRoute?.loadComponent?.();
    expect(component).toBeDefined();
  });

  it('defines guarded reviews route', async () => {
    const reviewsRoute = routes.find((route) => route.path === 'reviews');
    expect(reviewsRoute?.canActivate?.length).toBe(1);
    const component = await reviewsRoute?.loadComponent?.();
    expect(component).toBeDefined();
  });

  it('defines guarded returns route', async () => {
    const returnsRoute = routes.find((route) => route.path === 'returns');
    expect(returnsRoute?.canActivate?.length).toBe(1);
    const component = await returnsRoute?.loadComponent?.();
    expect(component).toBeDefined();
  });

  it('defines guarded notifications route', async () => {
    const notificationsRoute = routes.find(
      (route) => route.path === 'notifications'
    );
    expect(notificationsRoute?.canActivate?.length).toBe(1);
    const component = await notificationsRoute?.loadComponent?.();
    expect(component).toBeDefined();
  });

  it('defines guarded shipments route', async () => {
    const shipmentsRoute = routes.find((route) => route.path === 'shipments');
    expect(shipmentsRoute?.canActivate?.length).toBe(1);
    const component = await shipmentsRoute?.loadComponent?.();
    expect(component).toBeDefined();
  });

  it('defines guarded coupons route', async () => {
    const couponsRoute = routes.find((route) => route.path === 'coupons');
    expect(couponsRoute?.canActivate?.length).toBe(1);
    const component = await couponsRoute?.loadComponent?.();
    expect(component).toBeDefined();
  });

  it('redirects empty path to dashboard', () => {
    const defaultRoute = routes.find((route) => route.path === '');
    expect(defaultRoute?.redirectTo).toBe('dashboard');
    expect(defaultRoute?.pathMatch).toBe('full');
  });

  it('redirects wildcard path to the not-found component', async () => {
    const wildcardRoute = routes.find((route) => route.path === '**');
    expect(wildcardRoute).toBeTruthy();
    const component = await wildcardRoute?.loadComponent?.();
    expect(component).toBeDefined();
  });
});
