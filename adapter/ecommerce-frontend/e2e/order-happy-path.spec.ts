import { expect, test } from '@playwright/test';

test('order happy path: login and place order', async ({ page }) => {
  // An empty path preserves baseURL's own "/home" context path in full; a leading "/" would instead resolve to the
  // server's root ("http://host:port/"), which 404s since the app is only served under /home.
  await page.goto('');
  await expect(page).toHaveURL(/login/);

  await page.getByTestId('login-username').fill('order-admin');
  await page.getByTestId('login-password').fill('password');
  await page.getByTestId('login-submit').click();

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
  await page.getByTestId('order-submit').click();

  await expect(page.getByTestId('order-number')).toBeVisible({
    timeout: 10_000,
  });
});
