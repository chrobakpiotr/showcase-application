import { expect, test } from "@playwright/test";

import { loginAs } from "./auth";

function notificationPage(
  id: string,
  subject: string,
  page: number,
  totalPages: number,
) {
  return {
    _embedded: {
      notificationResourceList: [
        {
          notificationId: id,
          recipientEmail: "e2e@example.com",
          type: "ORDER_CONFIRMED",
          subject,
          channel: "EMAIL",
          status: "SENT",
          createdDate: "2026-09-20T10:00:00Z",
        },
      ],
    },
    page: { size: 20, totalElements: totalPages, totalPages, number: page },
  };
}

function shipmentPage(status: "PENDING" | "DISPATCHED" | "IN_TRANSIT") {
  return {
    _embedded: {
      shipmentResourceList: [
        {
          shipmentNumber: "SHIP-E2E",
          orderNumber: "ORDER-E2E",
          carrier: "DHL",
          trackingNumber: "TRACK-E2E",
          status,
          dispatchedDate: status === "PENDING" ? null : "2026-09-20",
          estimatedDeliveryDate: "2026-09-22",
          deliveredDate: null,
          createdDate: "2026-09-20",
          _links: {
            "advance-status": {
              href: "/home/api/shipments/SHIP-E2E/advance",
            },
          },
        },
      ],
    },
    page: { size: 20, totalElements: 1, totalPages: 1, number: 0 },
  };
}

test("notifications keep the newest request when an older page returns late", async ({
  page,
}) => {
  await loginAs(page);

  await page.route("**/home/api/notifications?*", async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname !== "/home/api/notifications") {
      await route.fallback();
      return;
    }
    const requestedPage = Number(url.searchParams.get("page") ?? "0");
    if (requestedPage === 1) {
      await new Promise((resolve) => setTimeout(resolve, 400));
      await route.fulfill({
        status: 200,
        json: notificationPage("NOTIF-SLOW", "SLOW-OLD-PAGE", 1, 2),
      });
      return;
    }
    await route.fulfill({
      status: 200,
      json: notificationPage("NOTIF-INITIAL", "INITIAL-PAGE", 0, 2),
    });
  });

  await page.route("**/home/api/notifications/status/SENT?*", async (route) => {
    await route.fulfill({
      status: 200,
      json: notificationPage("NOTIF-FAST", "FAST-NEW-FILTER", 0, 1),
    });
  });

  const target = new URL(page.url());
  target.pathname = target.pathname.replace(/\/dashboard$/, "/notifications");
  await page.goto(target.toString());

  await expect(page.getByText("INITIAL-PAGE")).toBeVisible();
  await page.getByTestId("notifications-next-page").click();
  await page.getByTestId("notification-status-filter").selectOption("SENT");
  await expect(page.getByText("FAST-NEW-FILTER")).toBeVisible();
  await page.waitForTimeout(500);
  await expect(page.getByText("SLOW-OLD-PAGE")).toHaveCount(0);
});

test("shipment UI always sends operation identity and expected status", async ({
  page,
}) => {
  await loginAs(page);
  let operationId = "";
  let expectedStatus = "";
  let currentStatus: "PENDING" | "DISPATCHED" = "PENDING";

  await page.route("**/home/api/shipments?*", async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname !== "/home/api/shipments") {
      await route.fallback();
      return;
    }
    await route.fulfill({ status: 200, json: shipmentPage(currentStatus) });
  });

  await page.route("**/home/api/shipments/SHIP-E2E/advance", async (route) => {
    operationId = route.request().headers()["idempotency-key"] ?? "";
    expectedStatus =
      route.request().headers()["x-expected-shipment-status"] ?? "";
    currentStatus = "DISPATCHED";
    await route.fulfill({
      status: 200,
      json: shipmentPage("DISPATCHED")._embedded.shipmentResourceList[0],
    });
  });

  const target = new URL(page.url());
  target.pathname = target.pathname.replace(/\/dashboard$/, "/shipments");
  await page.goto(target.toString());
  await expect(page.getByText("TRACK-E2E")).toBeVisible();
  await page.getByTestId("advance-shipment").click();
  const shipmentRow = page
    .getByTestId("shipment-row")
    .filter({ hasText: "TRACK-E2E" });
  await expect(
    shipmentRow.getByRole("cell", { name: "DISPATCHED", exact: true }),
  ).toBeVisible();
  expect(operationId).not.toBe("");
  expect(expectedStatus).toBe("PENDING");
});

test("shipment 409 refreshes status and starts a new logical attempt", async ({
  page,
}) => {
  await loginAs(page);
  let currentStatus: "PENDING" | "DISPATCHED" | "IN_TRANSIT" = "PENDING";
  const attempts: Array<{ operationId: string; expectedStatus: string }> = [];

  await page.route("**/home/api/shipments?*", async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname !== "/home/api/shipments") {
      await route.fallback();
      return;
    }
    await route.fulfill({ status: 200, json: shipmentPage(currentStatus) });
  });

  await page.route("**/home/api/shipments/SHIP-E2E/advance", async (route) => {
    attempts.push({
      operationId: route.request().headers()["idempotency-key"] ?? "",
      expectedStatus:
        route.request().headers()["x-expected-shipment-status"] ?? "",
    });

    if (attempts.length === 1) {
      currentStatus = "DISPATCHED";
      await route.fulfill({
        status: 409,
        contentType: "application/problem+json",
        json: {
          status: 409,
          title: "Conflict",
          detail: "Shipment status changed in another client",
        },
      });
      return;
    }

    currentStatus = "IN_TRANSIT";
    await route.fulfill({
      status: 200,
      json: shipmentPage("IN_TRANSIT")._embedded.shipmentResourceList[0],
    });
  });

  const target = new URL(page.url());
  target.pathname = target.pathname.replace(/\/dashboard$/, "/shipments");
  await page.goto(target.toString());

  const row = page.getByTestId("shipment-row").filter({ hasText: "TRACK-E2E" });
  await expect(
    row.getByRole("cell", { name: "PENDING", exact: true }),
  ).toBeVisible();

  await page.getByTestId("advance-shipment").click();

  await expect(
    row.getByRole("cell", { name: "DISPATCHED", exact: true }),
  ).toBeVisible();

  await page.getByTestId("advance-shipment").click();

  await expect(
    row.getByRole("cell", { name: "IN_TRANSIT", exact: true }),
  ).toBeVisible();

  expect(attempts).toHaveLength(2);
  expect(attempts[0].operationId).not.toBe("");
  expect(attempts[0].expectedStatus).toBe("PENDING");
  expect(attempts[1].operationId).not.toBe("");
  expect(attempts[1].operationId).not.toBe(attempts[0].operationId);
  expect(attempts[1].expectedStatus).toBe("DISPATCHED");
});
