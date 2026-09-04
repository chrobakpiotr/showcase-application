import { routes } from './app.routes';

describe('app routes', () => {
  it('defines login route', async () => {
    const loginRoute = routes.find((route) => route.path === 'login');
    expect(loginRoute).toBeTruthy();
    const component = await loginRoute?.loadComponent?.();
    expect(component).toBeDefined();
  });

  it('defines guarded order route', async () => {
    const orderRoute = routes.find((route) => route.path === 'order');
    expect(orderRoute?.canActivate?.length).toBe(1);
    const component = await orderRoute?.loadComponent?.();
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

  it('redirects empty path to order', () => {
    const defaultRoute = routes.find((route) => route.path === '');
    expect(defaultRoute?.redirectTo).toBe('order');
    expect(defaultRoute?.pathMatch).toBe('full');
  });

  it('redirects wildcard path to the not-found component', async () => {
    const wildcardRoute = routes.find((route) => route.path === '**');
    expect(wildcardRoute).toBeTruthy();
    const component = await wildcardRoute?.loadComponent?.();
    expect(component).toBeDefined();
  });
});
