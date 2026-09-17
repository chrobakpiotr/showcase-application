import { expect, test } from '@playwright/test';

const ROUTES = [
  ['dashboard', 'app-dashboard'],
  ['order', 'app-order'],
  ['orders', 'app-order-list'],
  ['analytics', 'app-analytics-assistant'],
  ['catalog', 'app-catalog'],
  ['cart', 'app-cart'],
  ['wishlist', 'app-wishlist'],
  ['recommendations', 'app-recommendations'],
  ['inventory', 'app-inventory'],
  ['reviews', 'app-reviews'],
  ['returns', 'app-returns'],
  ['notifications', 'app-notifications'],
  ['shipments', 'app-shipments'],
  ['coupons', 'app-coupons'],
] as const;

// A separate test budget per route prevents fourteen full navigations sharing 30s.
for (const [route, component] of ROUTES) {
  test(`${route} renders without viewport overflow`, async ({ page }) => {
    // Preserve the viewport selected in the main-branch pipeline repair.
    await page.setViewportSize({ width: 681, height: 844 });
    await page.goto('');
    await page.getByTestId('login-username').fill('order-admin');
    await page.getByTestId('login-password').fill('password');
    await page.getByTestId('login-submit').click();
    await expect(page).toHaveURL(/\/dashboard(?:$|[?#])/);
    const target = new URL(page.url());
    target.pathname = target.pathname.replace(/\/dashboard$/, `/${route}`);
    const tableCollections: Record<string, string> = {
      notifications: 'notificationResourceList',
      shipments: 'shipmentResourceList',
      coupons: 'couponDetailsResourceList',
    };
    const tableCollection = tableCollections[route];
    const loadedTable = tableCollection
      ? page.waitForResponse((response) =>
          new URL(response.url()).pathname.endsWith(`/api/${route}`),
        )
      : null;
    await page.goto(target.toString());
    if (loadedTable) {
      const response = await loadedTable;
      expect(response.ok()).toBeTruthy();
      await response.finished();
      const body = await response.json();
      const count = body._embedded?.[tableCollection]?.length ?? 0;
      await expect(page.locator(`${component} tbody tr`)).toHaveCount(
        Math.max(1, count),
      );
      if (count === 0) {
        await expect(page.locator(`${component} tbody`)).toContainText(
          /No (notifications|shipments|coupons)/,
        );
      }
    }
    await expect(page.locator(component)).toBeVisible();
    if (route === 'catalog') {
      await expect(page.getByTestId('product-card').first()).toBeVisible();
    }
    await expect(page.locator(`${component} .loading`)).toHaveCount(0);
    const dimensions = await page.evaluate(() => ({
      clientWidth: document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth,
    }));
    expect(dimensions.scrollWidth).toBeLessThanOrEqual(
      dimensions.clientWidth + 1,
    );
  });
}

for (const state of ['populated', 'empty', 'error'] as const) {
  test(`catalog at 390px after ${state} response`, async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('');
    await page.getByTestId('login-username').fill('order-admin');
    await page.getByTestId('login-password').fill('password');
    await page.getByTestId('login-submit').click();
    await expect(page).toHaveURL(/\/dashboard(?:$|[?#])/);
    await page.route('**/api/catalog/products?*', (route) =>
      route.fulfill({
        status: state === 'error' ? 503 : 200,
        json: {
          _embedded: {
            productDetailsResourceList:
              state === 'populated'
                ? [
                    {
                      sku: 'LAYOUT-FIXTURE',
                      name: 'A product with a representative long display name',
                      description:
                        'A loaded product card for layout verification.',
                      categoryName: 'Electronics',
                      categorySlug: 'electronics',
                      unitPrice: 39.9,
                      imageUrl: '',
                      active: true,
                      created: '2026-01-01',
                    },
                  ]
                : [],
          },
          page: {
            size: 12,
            totalElements: state === 'populated' ? 1 : 0,
            totalPages: 1,
            number: 0,
          },
        },
      }),
    );
    const target = new URL(page.url());
    target.pathname = target.pathname.replace(/\/dashboard$/, '/catalog');
    const response = page.waitForResponse((result) =>
      new URL(result.url()).pathname.endsWith('/api/catalog/products'),
    );
    await page.goto(target.toString());
    await (await response).finished();
    await expect(page.locator('app-catalog')).toBeVisible();
    await expect(page.locator('app-catalog .loading')).toHaveCount(0);
    if (state === 'populated') {
      await expect(page.getByTestId('product-card')).toContainText(
        'LAYOUT-FIXTURE',
      );
      await page.getByTestId('product-inventory').click();
      await expect(page.getByTestId('sku')).toHaveValue('LAYOUT-FIXTURE');
      await expect(page.getByTestId('stock-level')).toHaveCount(0);
      await page.goBack();
      await expect(page.getByTestId('product-card')).toBeVisible();
    } else if (state === 'empty') {
      await expect(page.getByTestId('empty-products')).toBeVisible();
    } else {
      await expect(page.locator('app-catalog [role="alert"]')).toContainText(
        'Failed to load products.',
      );
    }
    const overflow = await page.evaluate(
      () =>
        document.documentElement.scrollWidth -
        document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(1);
  });
}
