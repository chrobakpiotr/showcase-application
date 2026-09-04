import { expect, test } from '@playwright/test';

// Mocked at the network boundary via page.route() rather than exercising the real Ollama-backed endpoint: the "ai"
// docker-compose profile (and so Ollama) isn't started for this e2e pipeline, so a real call would either hang or
// 503. This test is only about the widget's own wiring (open/close, send, render the response) - not about the
// quality of any actual model answer, which is already covered by the backend's own adapter/domain unit tests.
test('support assistant widget: ask a question and see the mocked answer', async ({
  page,
}) => {
  await page.goto('');
  await expect(page).toHaveURL(/login/);

  await page.getByTestId('login-username').fill('order-admin');
  await page.getByTestId('login-password').fill('password');
  await page.getByTestId('login-submit').click();
  await expect(page).toHaveURL(/\/order(?:$|[?#])/);

  await page.route('**/api/support-assistant/questions', async (route) => {
    const request = route.request();
    expect(request.method()).toBe('POST');
    const body = request.postDataJSON() as {
      question: string;
      conversationId: string;
    };
    expect(body.question).toBe('Can I still cancel my order?');
    expect(body.conversationId).toBeTruthy();

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        answer: 'Yes, you can cancel any CONFIRMED order from the order page.',
        assistantAvailable: true,
      }),
    });
  });

  await page.getByTestId('support-assistant-toggle').click();
  await page
    .getByTestId('support-assistant-input')
    .fill('Can I still cancel my order?');
  await page.getByTestId('support-assistant-send').click();

  await expect(page.getByTestId('support-assistant-messages')).toContainText(
    'Yes, you can cancel any CONFIRMED order from the order page.',
    { timeout: 10_000 }
  );
});
