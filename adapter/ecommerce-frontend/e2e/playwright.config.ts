import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  // Relative to this config file's own directory (e2e/), so this resolves to the e2e/ folder itself, where the
  // *.spec.ts files actually live - NOT e2e/e2e/. Also fixes the default port to the ecommerce-app service.
  testDir: '.',
  timeout: 30_000,
  retries: 0,
  // Explicit so CI always produces a downloadable HTML report regardless of Playwright's own
  // environment-dependent default reporter; 'never' keeps a local run from also popping open a browser tab.
  reporter: [['html', { open: 'never' }]],
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:9080/home',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
