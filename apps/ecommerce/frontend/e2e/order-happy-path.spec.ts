import { randomUUID } from 'node:crypto';
import { expect, test, type Page } from '@playwright/test';

async function loginAsAdmin(page: Page): Promise<void> {
  await page.goto('');
  await expect(page).toHaveURL(/login/);
  await page.getByTestId('login-username').fill('order-admin');
  await page.getByTestId('login-password').fill('password');
  await page.getByTestId('login-submit').click();
  await expect(page).toHaveURL(/\/dashboard(?:$|[?#])/);
}

async function prepareIsolatedStock(page: Page, sku: string): Promise<void> {
  await page.getByRole('link', { name: 'Inventory', exact: true }).click();
  await expect(page).toHaveURL(/\/inventory(?:$|[?#])/);
  await page.getByTestId('sku').fill(sku);
  await page.getByTestId('lookup').click();
  await expect(page.getByTestId('stock-level')).toBeVisible();

  const availableStock = page.getByTestId('quantity-available');
  const availableBefore = Number(await availableStock.textContent());
  await page.getByTestId('adjustment-quantity').fill('1');
  await page.getByTestId('receive').click();
  await expect(availableStock).toHaveText(String(availableBefore + 1));
}

async function openDemoOrder(page: Page, sku: string): Promise<void> {
  await page.getByRole('link', { name: 'Orders', exact: true }).click();
  await expect(page).toHaveURL(/\/order(?:$|[?#])/);

  await page.getByTestId('order-fill-demo').click();
  await expect(page.getByTestId('item-sku-0')).toHaveValue('DEMO-MOUSE-001');
  await expect(page.getByTestId('item-productName-0')).toHaveValue(
    'Wireless Mouse'
  );
  await expect(page.getByTestId('item-unitPrice-0')).toHaveValue('39.9');
  await page.getByTestId('item-sku-0').fill(sku);
  await expect(page.getByTestId('order-submit')).toBeEnabled();
}

test('order happy path: login, prepare isolated stock and place order', async ({
  page,
}) => {
  const fixtureSku = `E2E-${randomUUID().replaceAll('-', '')}`;

  await loginAsAdmin(page);
  await prepareIsolatedStock(page, fixtureSku);
  await openDemoOrder(page, fixtureSku);

  const placement = page.waitForResponse(
    (response) =>
      response.url().endsWith('/api/order') &&
      response.request().method() === 'POST'
  );
  await page.getByTestId('order-submit').click();
  const response = await placement;
  expect(response.status()).toBe(201);
  expect(response.request().headers()['idempotency-key']).toBeTruthy();
  await expect(page.getByTestId('order-number')).toBeVisible({
    timeout: 10_000,
  });
  await page.getByTestId('order-history-link').click();
  await expect(page).toHaveURL(/\/orders(?:$|[?#])/);
});

test('unknown order outcome survives reload and replays the same attempt', async ({
  page,
}) => {
  const fixtureSku = `E2E-${randomUUID().replaceAll('-', '')}`;
  let firstKey = '';
  let committedOrderNumber = '';

  await loginAsAdmin(page);
  await prepareIsolatedStock(page, fixtureSku);
  await openDemoOrder(page, fixtureSku);

  await page.route('**/api/order', async (route) => {
    const request = route.request();
    if (request.method() !== 'POST') {
      await route.continue();
      return;
    }

    firstKey = request.headers()['idempotency-key'] ?? '';
    const upstream = await route.fetch();
    expect(upstream.status()).toBe(201);
    const body = (await upstream.json()) as { orderNumber?: string };
    committedOrderNumber = body.orderNumber ?? '';
    expect(committedOrderNumber).toBeTruthy();

    // The server accepted the request, but the browser never receives the response.
    await route.abort('failed');
  });

  await page.getByTestId('order-submit').click();
  await expect(page.getByTestId('order-unknown')).toBeVisible();
  expect(firstKey).toBeTruthy();

  await page.unroute('**/api/order');
  await page.reload();

  await expect(page).toHaveURL(/\/order(?:$|[?#])/);
  await expect(page.getByTestId('order-unknown')).toBeVisible();
  await expect(page.getByTestId('item-sku-0')).toHaveValue(fixtureSku);

  const replay = page.waitForResponse(
    (response) =>
      response.url().endsWith('/api/order') &&
      response.request().method() === 'POST'
  );
  await page.getByTestId('order-submit').click();
  const replayResponse = await replay;

  expect(replayResponse.status()).toBe(201);
  expect(replayResponse.request().headers()['idempotency-key']).toBe(firstKey);
  await expect(page.getByTestId('order-number')).toHaveText(
    committedOrderNumber
  );

  await page.getByTestId('order-history-link').click();
  await expect(page).toHaveURL(/\/orders(?:$|[?#])/);
  await expect(
    page.getByTestId('order-row').filter({ hasText: committedOrderNumber })
  ).toHaveCount(1);
});
