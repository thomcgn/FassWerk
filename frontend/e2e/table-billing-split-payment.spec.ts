import { expect, test } from "@playwright/test";
import { mockAuthenticatedSession } from "./support/mock-auth";
import { tableBillingMeta } from "./support/test-data";

test("tischbon: position buchen und split-payment durchfuehren", async ({ page }) => {
  const { tables, categories, drinks, variants } = tableBillingMeta();
  const inventory = [{
    id: 41,
    name: "Helles Fass",
    linkedDrinkId: 21,
    linkedDrinkVariantId: 31,
    packageType: "BARREL",
    packagesInStock: "1",
    contentPerPackage: "50",
    contentUnit: "LITER",
    totalStockAmount: "50",
    reorderThreshold: "10",
    minimumStock: "5",
    recommendedReorderAmount: "15",
    supplier: "Brauerei",
    active: true,
  }];

  const order = {
    id: 901,
    tableId: 1,
    tableName: "T1",
    reservationId: null,
    status: "OPEN",
    paid: false,
    openedAt: new Date().toISOString(),
    closedAt: null,
    total: "0.00",
    items: [] as Array<{
      id: number;
      drinkVariantId: number;
      drinkLabel: string;
      quantity: number;
      unitPrice: string;
      totalPrice: string;
      deductedVolumeMl: string;
    }>,
  };

  await mockAuthenticatedSession(page);

  await page.route("**/api/tables", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(tables) });
  });
  await page.route("**/api/drink-categories", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(categories) });
  });
  await page.route("**/api/drinks", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(drinks) });
  });
  await page.route("**/api/drink-variants", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(variants) });
  });
  await page.route("**/api/inventory", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(inventory) });
  });

  await page.route("**/api/table-orders/open/table/1", async (route) => {
    await route.fulfill({ status: 404, contentType: "application/json", body: JSON.stringify({ message: "not-found" }) });
  });

  await page.route("**/api/table-orders/open", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(order) });
  });

  await page.route("**/api/table-orders/901/items", async (route) => {
    if (order.items.length === 0) {
      order.items.push({
        id: 1,
        drinkVariantId: 31,
        drinkLabel: "Helles · 0,5 l",
        quantity: 1,
        unitPrice: "5.90",
        totalPrice: "5.90",
        deductedVolumeMl: "500",
      });
      order.total = "5.90";
    } else {
      order.items[0].quantity = 2;
      order.items[0].totalPrice = "11.80";
      order.total = "11.80";
    }
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(order) });
  });

  await page.route("**/api/table-orders/901/split-payment", async (route) => {
    order.items[0].quantity = 1;
    order.items[0].totalPrice = "5.90";
    order.total = "5.90";

    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        openOrder: order,
        paidOrder: {
          ...order,
          id: 902,
          status: "CLOSED",
          total: "5.90",
          items: [{ ...order.items[0], quantity: 1, totalPrice: "5.90" }],
        },
      }),
    });
  });

  await page.goto("/table-billing");

  await expect(page.getByRole("heading", { name: "Tischabrechnung für dein Team." })).toBeVisible();
  await page.getByRole("button", { name: /T1/ }).click();

  await page.getByRole("button", { name: /Helles · 0,5 l/ }).first().click();
  await page.getByRole("button", { name: /Helles · 0,5 l/ }).first().click();

  await expect(page.getByText("2 ×")).toBeVisible();
  await page.getByRole("button", { name: "Teilzahlung" }).first().click();
  await expect(page.getByText("1 ×")).toBeVisible();
});


