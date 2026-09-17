import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';

test('order happy path: login, prepare isolated stock and place order', async ({
  page,
}) => {
  const fixtureSku = `E2E-${randomUUID().replaceAll('-', '')}`;
  await page.goto('');
  await expect(page).toHaveURL(/login/);

  await page.getByTestId('login-username').fill('order-admin');
  await page.getByTestId('login-password').fill('password');
  await page.getByTestId('login-submit').click();
  await expect(page).toHaveURL(/\/dashboard(?:$|[?#])/);

  await page.getByRole('link', { name: 'Inventory', exact: true }).click();
  await expect(page).toHaveURL(/\/inventory(?:$|[?#])/);
  await page.getByTestId('sku').fill(fixtureSku);
  await page.getByTestId('lookup').click();
  await expect(page.getByTestId('stock-level')).toBeVisible();

  const availableStock = page.getByTestId('quantity-available');
  const availableBefore = Number(await availableStock.textContent());
  await page.getByTestId('adjustment-quantity').fill('1');
  await page.getByTestId('receive').click();
  await expect(availableStock).toHaveText(String(availableBefore + 1));

  await page.getByRole('link', { name: 'Orders', exact: true }).click();
  await expect(page).toHaveURL(/\/order(?:$|[?#])/);

  await page.getByTestId('order-fill-demo').click();
  await expect(page.getByTestId('item-sku-0')).toHaveValue('DEMO-MOUSE-001');
  await expect(page.getByTestId('item-productName-0')).toHaveValue(
    'Wireless Mouse',
  );
  await expect(page.getByTestId('item-unitPrice-0')).toHaveValue('39.9');
  await page.getByTestId('item-sku-0').fill(fixtureSku);
  await expect(page.getByTestId('order-submit')).toBeEnabled();

  const placement = page.waitForResponse(
    (response) =>
      response.url().endsWith('/api/order') &&
      response.request().method() === 'POST',
  );
  await page.getByTestId('order-submit').click();
  const response = await placement;
  expect(response.status(), await response.text()).toBe(201);
  expect(response.request().headers()['idempotency-key']).toBeTruthy();
  await expect(page.getByTestId('order-number')).toBeVisible({
    timeout: 10_000,
  });
  await page.getByTestId('order-history-link').click();
  await expect(page).toHaveURL(/\/orders(?:$|[?#])/);
});
