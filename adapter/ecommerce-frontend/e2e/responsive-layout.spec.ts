import { expect, test } from '@playwright/test';

const ROUTES = [
  'dashboard',
  'order',
  'orders',
  'analytics',
  'catalog',
  'cart',
  'wishlist',
  'recommendations',
  'inventory',
  'reviews',
  'returns',
  'notifications',
  'shipments',
  'coupons',
];

test('authenticated demo pages do not overflow a mobile viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('');
  await page.getByTestId('login-username').fill('order-admin');
  await page.getByTestId('login-password').fill('password');
  await page.getByTestId('login-submit').click();
  await expect(page).toHaveURL(/\/dashboard(?:$|[?#])/);

  const dashboardUrl = new URL(page.url());
  const appRoot = dashboardUrl.pathname.replace(/\/dashboard$/, '');

  for (const route of ROUTES) {
    const target = new URL(page.url());
    target.pathname = `${appRoot}/${route}`;
    target.search = '';
    target.hash = '';
    await page.goto(target.toString());
    await page.waitForLoadState('domcontentloaded');

    const dimensions = await page.evaluate(() => ({
      clientWidth: document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth,
    }));

    expect(
      dimensions.scrollWidth,
      `${route} should not cause page-level horizontal overflow`
    ).toBeLessThanOrEqual(dimensions.clientWidth + 1);
  }
});
