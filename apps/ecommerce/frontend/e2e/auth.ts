import { expect, type Page } from '@playwright/test';

export async function completeKeycloakLogin(
  page: Page,
  username: string,
  expectedPath: string
): Promise<void> {
  await page.getByTestId('login-submit').click();
  await expect(page).toHaveURL(
    /localhost:8081\/realms\/ecommerce\/protocol\/openid-connect\/auth/
  );

  await page.locator('#username').fill(username);
  await page.locator('#password').fill('password');
  await page.locator('#kc-login').click();

  await expect(page).toHaveURL(
    new RegExp(`${escapeRegex(expectedPath)}(?:$|[?#])`)
  );
}

export async function loginAs(
  page: Page,
  username = 'order-admin'
): Promise<void> {
  await page.goto('');
  await expect(page).toHaveURL(/\/login(?:$|[?#])/);
  await completeKeycloakLogin(page, username, '/dashboard');
}

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
