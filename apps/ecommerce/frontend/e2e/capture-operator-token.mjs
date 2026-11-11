// Logs in as order-admin through the real Keycloak PKCE flow and writes the bearer token that the SPA sends to the
// API. CI uses it for the k6 order smoke; the token is never printed.
// Usage: node e2e/capture-operator-token.mjs <output-file>
import { writeFileSync } from 'node:fs';
import { chromium } from '@playwright/test';

const output = process.argv[2];
if (!output) {
  console.error('usage: node e2e/capture-operator-token.mjs <output-file>');
  process.exit(2);
}

const baseUrl = process.env.E2E_BASE_URL ?? 'http://localhost:9080/home/';
const timeout = 60_000;
const browser = await chromium.launch();
try {
  const page = await browser.newPage();
  await page.goto(baseUrl, { timeout });
  await page.waitForURL(/\/login(?:$|[?#])/, { timeout });
  await page.getByTestId('login-submit').click();
  await page.waitForURL(/\/realms\/ecommerce\/protocol\/openid-connect\/auth/, { timeout });
  await page.locator('#username').fill('order-admin');
  await page.locator('#password').fill('password');
  await page.locator('#kc-login').click();
  await page.waitForURL(/\/dashboard(?:$|[?#])/, { timeout });

  const apiRequest = page.waitForRequest(
    (request) => request.url().includes('/home/api/') && Boolean(request.headers()['authorization']),
    { timeout }
  );
  await page.goto(new URL('catalog', baseUrl).toString(), { timeout });
  const header = (await apiRequest).headers()['authorization'];
  const token = header.replace(/^Bearer\s+/i, '').trim();
  if (!token) {
    throw new Error('The API request carried an empty bearer token');
  }
  writeFileSync(output, token, { mode: 0o600 });
  console.log('Operator token captured');
} finally {
  await browser.close();
}
