import {
  expect,
  test,
  type Locator,
  type Page,
  type Route,
} from "@playwright/test";

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

async function waitForCapturedPayment(
  page: Page,
  orderNumber: string,
  orderRow: Locator,
): Promise<void> {
  let paymentStatus = "MISSING";
  const orderDetailsPattern = `**/api/order/${encodeURIComponent(orderNumber)}`;
  const capturePaymentStatus = async (route: Route) => {
    const response = await route.fetch();
    if (response.ok()) {
      const order = (await response.json()) as {
        payment?: { status?: string } | null;
      };
      paymentStatus = order.payment?.status ?? "MISSING";
    }
    await route.fulfill({ response });
  };

  await page.route(orderDetailsPattern, capturePaymentStatus);
  try {
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
          expect((await orderDetailsResponse).status()).toBe(200);
          return paymentStatus;
        },
        { intervals: [1000, 2000, 5000], timeout: 60_000 },
      )
      .toBe("CAPTURED");
  } finally {
    await page.unroute(orderDetailsPattern, capturePaymentStatus);
  }
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
  await waitForCapturedPayment(page, orderNumber, orderRow);
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
  let advanceRequestCount = 0;
  let resolveFirstAdvance: (() => void) | undefined;
  const firstAdvanceGate = new Promise<void>((resolve) => {
    resolveFirstAdvance = resolve;
  });
  await page.route(
    `**/home/api/shipments/${shipmentNumber}/advance`,
    async (route) => {
      advanceRequestCount += 1;
      firstOperationId = route.request().headers()["idempotency-key"] ?? "";
      firstExpectedStatus =
        route.request().headers()["x-expected-shipment-status"] ?? "";
      await firstAdvanceGate;
      const response = await route.fetch();
      committedStatus = response.status();
      committedBody = await response.json();
      await route.abort("failed");
    },
  );

  await row.getByTestId("advance-shipment").dblclick();
  const advanceButton = row.getByTestId("advance-shipment");
  await expect(advanceButton).toBeDisabled();
  expect(advanceRequestCount).toBe(1);
  resolveFirstAdvance?.();
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

test("competing shipment advances require a new explicit operation after typed conflict", async ({
  browser,
  page,
}) => {
  test.setTimeout(120_000);
  const competingContext = await browser.newContext({
    baseURL: process.env["E2E_BASE_URL"] ?? "http://localhost:9080/home/",
  });
  try {
    await loginAs(page);
    const orderNumber = await placeFixtureOrder(page);

    await page.getByTestId("order-history-link").click();
    const orderRow = page
      .getByTestId("order-row")
      .filter({ hasText: orderNumber });
    await expect(orderRow).toBeVisible();
    await orderRow.getByTestId("view-order").click();
    await expect(page.getByTestId("order-details")).toContainText("CONFIRMED");
    await waitForCapturedPayment(page, orderNumber, orderRow);

    await page.getByTestId("shipment-carrier").fill("E2E Competing Client");
    const creation = page.waitForResponse(
      (response) =>
        response.url().endsWith("/api/shipments") &&
        response.request().method() === "POST",
    );
    await page.getByTestId("create-shipment").click();
    const creationResponse = await creation;
    expect(creationResponse.status()).toBe(201);
    const shipmentNumber = (
      (await creationResponse.json()) as { shipmentNumber: string }
    ).shipmentNumber;

    await page.getByRole("link", { name: "Shipments", exact: true }).click();
    const clientARow = page
      .getByTestId("shipment-row")
      .filter({ hasText: orderNumber });
    await expect(
      clientARow.getByRole("cell", { name: "PENDING", exact: true }),
    ).toBeVisible();

    const competingPage = await competingContext.newPage();
    await loginAs(competingPage);
    await competingPage
      .getByRole("link", { name: "Shipments", exact: true })
      .click();
    const clientBRow = competingPage
      .getByTestId("shipment-row")
      .filter({ hasText: orderNumber });
    await expect(
      clientBRow.getByRole("cell", { name: "PENDING", exact: true }),
    ).toBeVisible();

    const clientBAdvance = competingPage.waitForResponse(
      (response) =>
        response.url().endsWith(`/api/shipments/${shipmentNumber}/advance`) &&
        response.request().method() === "POST",
    );
    await clientBRow.getByTestId("advance-shipment").click();
    const clientBResponse = await clientBAdvance;
    expect(clientBResponse.status()).toBe(200);
    expect(
      clientBResponse.request().headers()["x-expected-shipment-status"],
    ).toBe("PENDING");
    const clientBOperationId =
      clientBResponse.request().headers()["idempotency-key"] ?? "";
    expect(clientBOperationId).toMatch(/^[0-9a-f-]{36}$/i);
    expect(((await clientBResponse.json()) as { status?: string }).status).toBe(
      "DISPATCHED",
    );
    await expect(
      clientBRow.getByRole("cell", { name: "DISPATCHED", exact: true }),
    ).toBeVisible();

    const clientARequests: Array<{
      operationId: string;
      expectedStatus: string;
    }> = [];
    page.on("request", (request) => {
      if (
        request.method() === "POST" &&
        request.url().endsWith(`/api/shipments/${shipmentNumber}/advance`)
      ) {
        const headers = request.headers();
        clientARequests.push({
          operationId: headers["idempotency-key"] ?? "",
          expectedStatus: headers["x-expected-shipment-status"] ?? "",
        });
      }
    });

    let staleProblemCode = "";
    await page.route(
      `**/home/api/shipments/${shipmentNumber}/advance`,
      async (route) => {
        if (
          route.request().headers()["x-expected-shipment-status"] !== "PENDING"
        ) {
          await route.continue();
          return;
        }

        const response = await route.fetch();
        const problem = (await response.json()) as { code?: string };
        staleProblemCode = problem.code ?? "";
        await route.fulfill({ response, json: problem });
      },
    );

    const staleAdvance = page.waitForResponse(
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
    await clientARow.getByTestId("advance-shipment").click();
    const staleResponse = await staleAdvance;
    expect(staleResponse.status()).toBe(409);
    expect(staleProblemCode).toBe("SHIPMENT_STALE_STATUS");
    expect(
      staleResponse.request().headers()["x-expected-shipment-status"],
    ).toBe("PENDING");
    const staleOperationId =
      staleResponse.request().headers()["idempotency-key"] ?? "";
    expect(staleOperationId).toMatch(/^[0-9a-f-]{36}$/i);
    expect(staleOperationId).not.toBe(clientBOperationId);
    expect((await refreshedShipments).status()).toBe(200);
    await expect(
      clientARow.getByRole("cell", { name: "DISPATCHED", exact: true }),
    ).toBeVisible();
    await expect(page.getByRole("alert")).toContainText(
      "Failed to advance shipment status.",
    );
    await expect.poll(() => clientARequests.length, { timeout: 1000 }).toBe(1);
    expect(clientARequests).toEqual([
      { operationId: staleOperationId, expectedStatus: "PENDING" },
    ]);

    const explicitAdvance = page.waitForResponse(
      (response) =>
        response.url().endsWith(`/api/shipments/${shipmentNumber}/advance`) &&
        response.request().method() === "POST",
    );
    await clientARow.getByTestId("advance-shipment").click();
    const explicitResponse = await explicitAdvance;
    expect(explicitResponse.status()).toBe(200);
    const explicitOperationId =
      explicitResponse.request().headers()["idempotency-key"] ?? "";
    expect(explicitOperationId).toMatch(/^[0-9a-f-]{36}$/i);
    expect(explicitOperationId).not.toBe(staleOperationId);
    expect(
      explicitResponse.request().headers()["x-expected-shipment-status"],
    ).toBe("DISPATCHED");
    expect(
      ((await explicitResponse.json()) as { status?: string }).status,
    ).toBe("IN_TRANSIT");
    await expect(
      clientARow.getByRole("cell", { name: "IN_TRANSIT", exact: true }),
    ).toBeVisible();

    await competingPage.reload();
    const reloadedClientBRow = competingPage
      .getByTestId("shipment-row")
      .filter({ hasText: orderNumber });
    await expect(
      reloadedClientBRow.getByRole("cell", {
        name: "IN_TRANSIT",
        exact: true,
      }),
    ).toBeVisible();
  } finally {
    await competingContext.close();
  }
});
