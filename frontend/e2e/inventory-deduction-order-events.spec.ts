import { expect, test } from "@playwright/test";
import { mockAuthenticatedSession } from "./support/mock-auth";
import { tableBillingMeta } from "./support/test-data";

test("inventory: bestand wird nach Bon-Abschluss aktualisiert", async ({ page }) => {
  const { tables, categories, drinks, variants } = tableBillingMeta();

  const inventoryState = [{
    id: 41,
    name: "Helles Fass",
    linkedDrinkId: 21,
    linkedDrinkVariantId: 31,
    packageType: "BARREL",
    packagesInStock: "10.0",
    contentPerPackage: "1.0",
    contentUnit: "LITER",
    totalStockAmount: "10.0",
    reorderThreshold: "2.0",
    minimumStock: "1.0",
    recommendedReorderAmount: "5.0",
    supplier: "Brauerei",
    active: true,
  }];

  const order = {
    id: 1001,
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
  await page.route("**/api/inventory/defaults", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify([]) });
  });
  await page.route("**/api/inventory", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(inventoryState) });
  });

  await page.route("**/api/table-orders/open/table/1", async (route) => {
    await route.fulfill({ status: 404, contentType: "application/json", body: JSON.stringify({ message: "not-found" }) });
  });
  await page.route("**/api/table-orders/open", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(order) });
  });
  await page.route("**/api/table-orders/1001/items", async (route) => {
    order.items = [{
      id: 1,
      drinkVariantId: 31,
      drinkLabel: "Helles · 0,5 l",
      quantity: 1,
      unitPrice: "5.90",
      totalPrice: "5.90",
      deductedVolumeMl: "500",
    }];
    order.total = "5.90";
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(order) });
  });
  await page.route("**/api/table-orders/1001/close", async (route) => {
    inventoryState[0].totalStockAmount = "9.5";
    order.status = "CLOSED";
    order.closedAt = new Date().toISOString();
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(order) });
  });

  await page.goto("/table-billing");

  await page.getByRole("button", { name: /T1/ }).click();
  await page.getByRole("button", { name: /Helles · 0,5 l/ }).first().click();
  await page.getByRole("button", { name: "Bon schließen" }).click();

  await page.getByRole("link", { name: "Lagerverwaltung" }).click();
  await expect(page.getByRole("heading", { name: "Lagerbestand kompakt" })).toBeVisible();
  await expect(page.locator("body")).toContainText("9,5 l");
});


