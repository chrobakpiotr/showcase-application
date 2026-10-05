import { expect, test } from "@playwright/test";

import { completeKeycloakLogin } from "./auth";

test("requires login and loads parked dispatches from the secured read endpoint", async ({
  page,
}) => {
  await page.goto("parked-dispatches");
  await expect(page).toHaveURL(/\/login\?returnUrl=%2Fparked-dispatches/);
  const anonymousRead = await page.request.get(
    new URL(
      "/home/api/order-placement/dispatches/parked?page=0&size=20",
      page.url(),
    ).href,
  );
  expect(anonymousRead.status()).toBe(401);
  const parkedResponse = page.waitForResponse(
    (response) =>
      response.url().includes("/api/order-placement/dispatches/parked?") &&
      response.request().method() === "GET",
  );
  await completeKeycloakLogin(page, "order-viewer", "/parked-dispatches");

  await expect(
    page.getByRole("heading", { name: "Parked dispatches" }),
  ).toBeVisible();
  await expect(page.getByTestId("parked-dispatches-table")).toBeVisible();
  expect((await parkedResponse).status()).toBe(200);
  await expect(page.getByTestId("parked-dispatch-redrive")).toHaveCount(0);
  for (const width of [320, 768, 1024, 1440]) {
    await page.setViewportSize({ width, height: 844 });
    const dimensions = await page
      .locator("app-parked-dispatches")
      .evaluate((element) => ({
        clientWidth: element.clientWidth,
        scrollWidth: element.scrollWidth,
      }));
    expect(dimensions.scrollWidth).toBeLessThanOrEqual(
      dimensions.clientWidth + 1,
    );
  }
});
