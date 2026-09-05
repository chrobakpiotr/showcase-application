import { expect, request, test } from '@playwright/test';

const KEYCLOAK_TOKEN_URL =
  process.env['E2E_KEYCLOAK_TOKEN_URL'] ??
  'http://localhost:8081/realms/ecommerce/protocol/openid-connect/token';
const API_BASE_URL = process.env['E2E_BASE_URL'] ?? 'http://localhost:9080/home';

// Stock must exist in Inventory before an order can reserve it - OrderController.placeOrder rejects a line
// item with insufficient stock (HTTP 409). A fresh SKU per run keeps repeated executions independent of
// whatever stock a prior run left behind.
async function receiveStockForTestSku(sku: string): Promise<void> {
  const apiContext = await request.newContext();

  const tokenResponse = await apiContext.post(KEYCLOAK_TOKEN_URL, {
    form: {
      grant_type: 'password',
      client_id: 'ecommerce-app',
      username: 'order-admin',
      password: 'password',
    },
  });
  const { access_token: accessToken } = (await tokenResponse.json()) as {
    access_token: string;
  };

  await apiContext.post(`${API_BASE_URL}/api/inventory/${sku}/receive`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: { quantity: 100 },
  });

  await apiContext.dispose();
}

test('order happy path: login and place order', async ({ page }) => {
  const sku = `E2E-SKU-${Date.now()}`;
  await receiveStockForTestSku(sku);

  // An empty path preserves baseURL's own "/home" context path in full; a leading "/" would instead resolve to the
  // server's root ("http://host:port/"), which 404s since the app is only served under /home. The guarded default
  // landing page is now the Dashboard (see the demo entry-point work), so an explicit click into "Orders" from the
  // always-present top nav gets us to the order form, exactly like a real user would navigate.
  await page.goto('');
  await expect(page).toHaveURL(/login/);

  await page.getByTestId('login-username').fill('order-admin');
  await page.getByTestId('login-password').fill('password');
  await page.getByTestId('login-submit').click();
  await expect(page).toHaveURL(/\/dashboard(?:$|[?#])/);

  await page.getByRole('link', { name: 'Orders', exact: true }).click();

  // Anchored to a literal "/order" path segment (not just a bare "order" substring): the auth guard
  // redirects a failed/unauthenticated visit to "/login?returnUrl=%2Forder", whose query string itself
  // contains "order" as a substring and would otherwise make this assertion pass even when login failed.
  await expect(page).toHaveURL(/\/order(?:$|[?#])/);

  // A unique email per run keeps the test idempotent across repeated executions against the same database,
  // independent of whether a given backend revision happens to allow or reject repeat orders for one email.
  await page.getByTestId('customer-fullName').fill('Jane Doe');
  await page.getByTestId('customer-email').fill(`jane.doe+${Date.now()}@example.com`);
  await page.getByTestId('customer-phone').fill('+1 555 123 4567');
  await page.getByTestId('customer-street').fill('Main Street 1');
  await page.getByTestId('customer-postalCode').fill('12-345');
  await page.getByTestId('customer-city').fill('Warsaw');
  await page.getByTestId('customer-countryCode').fill('PL');
  await page
    .getByTestId('order-remarks')
    .fill('Integration test order remarks');
  await page.getByTestId('item-sku-0').fill(sku);
  await page.getByTestId('item-productName-0').fill('Wireless Mouse');
  await page.getByTestId('item-unitPrice-0').fill('29.99');
  await page.getByTestId('item-quantity-0').fill('2');
  await page.getByTestId('order-submit').click();

  await expect(page.getByTestId('order-number')).toBeVisible({
    timeout: 10_000,
  });
});
