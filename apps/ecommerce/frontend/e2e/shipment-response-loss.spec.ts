import { expect, test, type Page } from "@playwright/test";

import { loginAs } from "./auth";

const FIXTURE_SKU = "DEMO-USB-HUB-001";
const FIXTURE_NAME = "USB-C Hub";
const FIXTURE_PRICE = "69.5";

async function placeFixtureOrder(page: Page): Promise<string> {
  await page.getByRole("link", { name: "Orders", exact: true }).click();
  await expect(page).toHaveURL(/\/order(?:$|[?#])/);
  await page.getByTestId("order-fill-demo").click();
  await page.getByTestId("item-sku-0").fill(FIXTURE_SKU);
  await page.getByTestId("item-productName-0").fill(FIXTURE_NAME);
  await page.getByTestId("item-unitPrice-0").fill(FIXTURE_PRICE);

  const placement = page.waitForResponse(
    (response) =>
      response.url().endsWith("/api/order") &&
      response.request().method() === "POST",
  );
  await page.getByTestId("order-submit").click();
  expect((await placement).status()).toBe(201);
  const orderNumber = (
    await page.getByTestId("order-number").textContent()
  )?.trim();
  expect(orderNumber).toBeTruthy();
  return orderNumber!;
}

test("shipment advance replays after the committed HTTP response is lost", async ({
  page,
}) => {
  test.setTimeout(90_000);
  await loginAs(page);
  const orderNumber = await placeFixtureOrder(page);

  await page.getByTestId("order-history-link").click();
  const orderRow = page
    .getByTestId("order-row")
    .filter({ hasText: orderNumber });
  await expect(orderRow).toBeVisible();
  await orderRow.getByTestId("view-order").click();
  await expect(page.getByTestId("order-details")).toContainText("CONFIRMED");
  await expect
    .poll(
      async () => {
        const orderDetailsResponse = page.waitForResponse((response) => {
          const url = new URL(response.url());
          return (
            url.pathname.endsWith(`/api/order/${orderNumber}`) &&
            response.request().method() === "GET"
          );
        });
        await orderRow.getByTestId("view-order").click();
        const orderDetails = await orderDetailsResponse;
        const order = (await orderDetails.json()) as {
          payment?: { status?: string } | null;
        };
        return order.payment?.status ?? "MISSING";
      },
      { intervals: [1000, 2000, 5000], timeout: 60_000 },
    )
    .toBe("CAPTURED");
  await page.getByTestId("shipment-carrier").fill("E2E Carrier");
  const shipmentCreation = page.waitForResponse(
    (response) =>
      response.url().endsWith("/api/shipments") &&
      response.request().method() === "POST",
  );
  await page.getByTestId("create-shipment").click();
  const creationResponse = await shipmentCreation;
  expect(creationResponse.status()).toBe(201);
  const shipmentNumber = (
    (await creationResponse.json()) as { shipmentNumber: string }
  ).shipmentNumber;
  expect(shipmentNumber).toBeTruthy();

  await page.getByRole("link", { name: "Shipments", exact: true }).click();
  await expect(page).toHaveURL(/\/shipments(?:$|[?#])/);
  const row = page.getByTestId("shipment-row").filter({ hasText: orderNumber });
  await expect(
    row.getByRole("cell", { name: "PENDING", exact: true }),
  ).toBeVisible();

  let firstOperationId = "";
  let firstExpectedStatus = "";
  let committedStatus = 0;
  let committedBody: unknown;
  await page.route(
    `**/home/api/shipments/${shipmentNumber}/advance`,
    async (route) => {
      firstOperationId = route.request().headers()["idempotency-key"] ?? "";
      firstExpectedStatus =
        route.request().headers()["x-expected-shipment-status"] ?? "";
      const response = await route.fetch();
      committedStatus = response.status();
      committedBody = await response.json();
      await route.abort("failed");
    },
  );

  await row.getByTestId("advance-shipment").click();
  await expect(page.getByRole("alert")).toContainText(
    "Failed to advance shipment status.",
  );
  expect(committedStatus).toBe(200);
  expect(firstOperationId).toMatch(/^[0-9a-f-]{36}$/i);
  expect(firstExpectedStatus).toBe("PENDING");
  expect((committedBody as { status?: string }).status).toBe("DISPATCHED");

  await page.unroute(`**/home/api/shipments/${shipmentNumber}/advance`);
  await page.reload();
  await expect(page).toHaveURL(/\/shipments(?:$|[?#])/);
  const reloadedRow = page
    .getByTestId("shipment-row")
    .filter({ hasText: orderNumber });
  await expect(
    reloadedRow.getByRole("cell", { name: "DISPATCHED", exact: true }),
  ).toBeVisible();

  const replay = page.waitForResponse(
    (response) =>
      response.url().endsWith(`/api/shipments/${shipmentNumber}/advance`) &&
      response.request().method() === "POST",
  );
  const refreshedShipments = page.waitForResponse((response) => {
    const url = new URL(response.url());
    return (
      url.pathname.endsWith("/api/shipments") &&
      url.searchParams.get("page") === "0" &&
      response.request().method() === "GET"
    );
  });
  await reloadedRow.getByTestId("advance-shipment").click();
  const replayResponse = await replay;
  const refreshedShipmentsResponse = await refreshedShipments;
  expect(replayResponse.status()).toBe(200);
  expect(refreshedShipmentsResponse.status()).toBe(200);
  expect(replayResponse.request().headers()["idempotency-key"]).toBe(
    firstOperationId,
  );
  expect(replayResponse.request().headers()["x-expected-shipment-status"]).toBe(
    firstExpectedStatus,
  );
  expect(await replayResponse.json()).toEqual(committedBody);
  await expect(page.getByTestId("shipments-loading")).toBeHidden();
  const persistedRow = page
    .getByTestId("shipment-row")
    .filter({ hasText: orderNumber });
  await expect(
    persistedRow.getByRole("cell", { name: "DISPATCHED", exact: true }),
  ).toBeVisible();

  let shipmentCountForOrder = 0;
  let pageIndex = 0;
  while (true) {
    shipmentCountForOrder += await page
      .getByTestId("shipment-row")
      .filter({ hasText: orderNumber })
      .count();
    const nextPage = page.getByTestId("shipments-next-page");
    if (await nextPage.isDisabled()) break;
    const nextPageIndex = pageIndex + 1;
    const nextPageResponse = page.waitForResponse((response) => {
      const url = new URL(response.url());
      return (
        url.pathname.endsWith("/api/shipments") &&
        url.searchParams.get("page") === String(nextPageIndex) &&
        response.request().method() === "GET"
      );
    });
    await nextPage.click();
    expect((await nextPageResponse).status()).toBe(200);
    await expect(page.getByTestId("shipments-loading")).toBeHidden();
    pageIndex = nextPageIndex;
  }
  expect(shipmentCountForOrder).toBe(1);
});
