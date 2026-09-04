import { expect, test } from '@playwright/test';

// Mocked at the network boundary via page.route() rather than exercising the real Ollama-backed endpoint: the "ai"
// docker-compose profile (and so Ollama) isn't started for this e2e pipeline, so a real call would either hang or
// 503. This test is only about the page's own wiring (navigate, send, render the response) - not about the
// quality of any actual model answer, which is already covered by the backend's own adapter/domain unit tests.
test('analytics assistant page: ask a question and see the mocked answer', async ({
  page,
}) => {
  await page.goto('');
  await expect(page).toHaveURL(/login/);

  await page.getByTestId('login-username').fill('order-admin');
  await page.getByTestId('login-password').fill('password');
  await page.getByTestId('login-submit').click();
  await expect(page).toHaveURL(/\/order(?:$|[?#])/);

  await page.route('**/api/order/analytics/ask', async (route) => {
    const request = route.request();
    expect(request.method()).toBe('POST');
    const body = request.postDataJSON() as {
      question: string;
      conversationId: string;
    };
    expect(body.question).toBe(
      'How many orders were placed between 2024-01-01 and 2024-01-31?'
    );
    expect(body.conversationId).toBeTruthy();

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        answer:
          '14 order(s) were placed between 2024-01-01 and 2024-01-31 (inclusive, UTC).',
        assistantAvailable: true,
      }),
    });
  });

  await page.route('**/api/order/analytics/digest', async (route) => {
    expect(route.request().method()).toBe('GET');
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        generatedDate: '2024-03-15T06:00:00.000Z',
        ordersPlacedLastDay: 7,
        remarksClassificationCounts: { STANDARD: 7 },
        narrative: '7 orders were placed in the last 24 hours, all routine.',
      }),
    });
  });

  await page.getByRole('link', { name: 'Analytics Assistant' }).click();
  await expect(page).toHaveURL(/\/analytics(?:$|[?#])/);

  await expect(page.getByTestId('ops-digest-card')).toContainText(
    '7 orders were placed in the last 24 hours, all routine.',
    { timeout: 10_000 }
  );

  await page
    .getByTestId('analytics-assistant-input')
    .fill('How many orders were placed between 2024-01-01 and 2024-01-31?');
  await page.getByTestId('analytics-assistant-send').click();

  await expect(
    page.getByTestId('analytics-assistant-messages')
  ).toContainText(
    '14 order(s) were placed between 2024-01-01 and 2024-01-31 (inclusive, UTC).',
    { timeout: 10_000 }
  );
});
