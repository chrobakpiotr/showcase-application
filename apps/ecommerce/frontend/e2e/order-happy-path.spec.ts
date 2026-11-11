import { expect, test, type Page } from "@playwright/test";

import { loginAs } from "./auth";

const PRIMARY_ORDER_SKU = "DEMO-MOUSE-001";
const REPLAY_ORDER_SKU = "DEMO-KEYBOARD-001";

async function prepareOrderStock(
  page: Page,
  sku: string,
  quantity = 1,
): Promise<void> {
  await page.getByRole("link", { name: "Inventory", exact: true }).click();
  await expect(page).toHaveURL(/\/inventory(?:$|[?#])/);
  await page.getByTestId("sku").fill(sku);
  await page.getByTestId("lookup").click();
  await expect(page.getByTestId("stock-level")).toBeVisible();

  const availableStock = page.getByTestId("quantity-available");
  const availableBefore = Number(await availableStock.textContent());
  await page.getByTestId("adjustment-quantity").fill(String(quantity));
  await page.getByTestId("receive").click();
  await expect(availableStock).toHaveText(String(availableBefore + quantity));
}

async function openDemoOrder(page: Page, sku: string): Promise<void> {
  await page.getByRole("link", { name: "Orders", exact: true }).click();
  await expect(page).toHaveURL(/\/order(?:$|[?#])/);

  await page.getByTestId("order-fill-demo").click();
  await expect(page.getByTestId("item-sku-0")).toHaveValue("DEMO-MOUSE-001");
  await expect(page.getByTestId("item-productName-0")).toHaveValue(
    "Wireless Mouse",
  );
  await expect(page.getByTestId("item-unitPrice-0")).toHaveValue("39.9");
  await page.getByTestId("item-sku-0").fill(sku);
  await expect(page.getByTestId("order-submit")).toBeEnabled();
}

test("order happy path: login, prepare catalog-backed stock and place order", async ({
  page,
}) => {
  const fixtureSku = PRIMARY_ORDER_SKU;

  await loginAs(page);
  await prepareOrderStock(page, fixtureSku);
  await openDemoOrder(page, fixtureSku);

  const placement = page.waitForResponse(
    (response) =>
      response.url().endsWith("/api/order") &&
      response.request().method() === "POST",
  );
  await page.getByTestId("order-submit").click();
  const response = await placement;
  expect(response.status()).toBe(201);
  expect(response.request().headers()["idempotency-key"]).toBeTruthy();
  await expect(page.getByTestId("order-number")).toBeVisible({
    timeout: 10_000,
  });
  await page.getByTestId("order-history-link").click();
  await expect(page).toHaveURL(/\/orders(?:$|[?#])/);
});

test("unknown cart order outcome survives reload and replays the same attempt", async ({
  page,
}) => {
  const fixtureSku = REPLAY_ORDER_SKU;
  const coupon = "SAVE10";
  let firstKey = "";
  let firstPayload: unknown;
  let committedOrderNumber = "";

  await loginAs(page);
  await prepareOrderStock(page, fixtureSku, 2);
  await page.getByRole("link", { name: "Cart", exact: true }).click();
  await expect(page).toHaveURL(/\/cart(?:$|[?#])/);
  const addItemForm = page.getByTestId("add-item-form");
  await addItemForm.getByTestId("sku").fill(fixtureSku);
  await addItemForm.getByTestId("quantity").fill("2");
  await addItemForm.getByTestId("add-item").click();
  await expect(page.getByTestId("cart-line-item")).toContainText(fixtureSku);
  await page.getByTestId("coupon-input").fill(coupon);
  await page.getByTestId("coupon-apply").click();
  await expect(page.getByTestId("cart-coupon")).toContainText(coupon);
  await page.getByRole("button", { name: "Continue to order" }).click();

  await expect(page).toHaveURL(/\/order(?:$|[?#])/);
  await expect(page.getByTestId("item-sku-0")).toHaveValue(fixtureSku);
  await expect(page.getByTestId("item-productName-0")).toHaveValue(
    "Mechanical Keyboard",
  );
  await expect(page.getByTestId("item-unitPrice-0")).toHaveValue("119");
  await expect(page.getByTestId("item-quantity-0")).toHaveValue("2");
  await expect(page.getByTestId("order-couponCode")).toHaveValue(coupon);

  await page.getByTestId("customer-fullName").fill("Cart Demo Buyer");
  await page
    .getByTestId("customer-email")
    .fill(`cart.demo+${Date.now()}@example.com`);
  await page.getByTestId("customer-street").fill("Demo Street 1");
  await page.getByTestId("customer-city").fill("Warsaw");
  await page.getByTestId("customer-countryCode").fill("PL");
  await page.getByTestId("order-remarks").fill("Demo order from the cart");

  await page.route("**/api/order", async (route) => {
    const request = route.request();
    if (request.method() !== "POST") {
      await route.continue();
      return;
    }

    firstKey = request.headers()["idempotency-key"] ?? "";
    firstPayload = request.postDataJSON();
    expect(firstPayload).toMatchObject({
      items: [{ sku: fixtureSku, quantity: 2 }],
      couponCode: coupon,
    });
    const upstream = await route.fetch();
    expect(upstream.status()).toBe(201);
    const body = (await upstream.json()) as { orderNumber?: string };
    committedOrderNumber = body.orderNumber ?? "";
    expect(committedOrderNumber).toBeTruthy();

    // The server accepted the request, but the browser never receives the response.
    await route.abort("failed");
  });

  await page.getByTestId("order-submit").click();
  await expect(page.getByTestId("order-unknown")).toBeVisible();
  expect(firstKey).toBeTruthy();

  await page.unroute("**/api/order");
  await page.reload();

  await expect(page).toHaveURL(/\/order(?:$|[?#])/);
  await expect(page.getByTestId("order-unknown")).toBeVisible();
  await expect(page.getByTestId("item-sku-0")).toHaveValue(fixtureSku);
  await expect(page.getByTestId("item-quantity-0")).toHaveValue("2");
  await expect(page.getByTestId("order-couponCode")).toHaveValue(coupon);

  const replay = page.waitForResponse(
    (response) =>
      response.url().endsWith("/api/order") &&
      response.request().method() === "POST",
  );
  await page.getByTestId("order-submit").click();
  const replayResponse = await replay;

  expect(replayResponse.status()).toBe(201);
  expect(replayResponse.request().headers()["idempotency-key"]).toBe(firstKey);
  expect(replayResponse.request().postDataJSON()).toEqual(firstPayload);
  await expect(page.getByTestId("order-number")).toHaveText(
    committedOrderNumber,
  );

  await page.getByTestId("order-history-link").click();
  await expect(page).toHaveURL(/\/orders(?:$|[?#])/);
  await expect(
    page.getByTestId("order-row").filter({ hasText: committedOrderNumber }),
  ).toHaveCount(1);

  await page.getByRole("link", { name: "Cart", exact: true }).click();
  await expect(page.getByTestId("cart-line-item")).toContainText(fixtureSku);
  await expect(page.getByTestId("line-item-quantity")).toHaveValue("2");
  await expect(page.getByTestId("cart-coupon")).toContainText(coupon);
});
