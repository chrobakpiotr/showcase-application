import { expect, test } from '@playwright/test';

test('order happy path: login, use seeded demo fixture and place order', async ({
  page,
}) => {
  await page.goto('');
  await expect(page).toHaveURL(/login/);

  await page.getByTestId('login-username').fill('order-admin');
  await page.getByTestId('login-password').fill('password');
  await page.getByTestId('login-submit').click();
  await expect(page).toHaveURL(/\/dashboard(?:$|[?#])/);

  await page.getByRole('link', { name: 'Inventory', exact: true }).click();
  await expect(page).toHaveURL(/\/inventory(?:$|[?#])/);
  await page.getByTestId('sku').fill('DEMO-MOUSE-001');
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
    'Wireless Mouse'
  );
  await expect(page.getByTestId('item-unitPrice-0')).toHaveValue('39.9');
  await expect(page.getByTestId('order-submit')).toBeEnabled();

  await page.getByTestId('order-submit').click();
  await expect(page.getByTestId('order-number')).toBeVisible({
    timeout: 10_000,
  });
});
