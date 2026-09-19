import { expect, test } from '@playwright/test';

import { completeKeycloakLogin, loginAs } from './auth';

test('OIDC login preserves the originally requested route', async ({ page }) => {
  await page.goto('orders');
  await expect(page).toHaveURL(/\/login\?returnUrl=%2Forders/);

  await completeKeycloakLogin(page, 'order-admin', '/orders');

  await expect(page.locator('app-order-list')).toBeVisible();
});

test('read-only operator authenticates through the same PKCE flow', async ({
  page,
}) => {
  await loginAs(page, 'order-viewer');

  await expect(page.locator('.session__copy')).toContainText('order-viewer');
  await expect(
    page.getByRole('link', { name: 'Order History', exact: true })
  ).toBeVisible();
});

test('logout ends the Keycloak session and returns to application login', async ({
  page,
}) => {
  await loginAs(page);

  await page.getByRole('button', { name: 'Logout', exact: true }).click();

  await expect(page).toHaveURL(/\/login(?:$|[?#])/);
  await expect(page.getByTestId('login-submit')).toBeVisible();
});
