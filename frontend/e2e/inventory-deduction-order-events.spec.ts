import { expect, test } from "@playwright/test";
import { mockAuthenticatedSession } from "./support/mock-auth";
import { createFlowState, mockFlowApis } from "./support/mock-api";
import { setupDrinkWithLinkedInventoryViaUi } from "./support/setup-flow";

test("inventory: bestand wird nach Bon-Abschluss aktualisiert", async ({ page }) => {
  const flowState = createFlowState();

  await mockAuthenticatedSession(page);
  await mockFlowApis(page, flowState);

  const setup = await setupDrinkWithLinkedInventoryViaUi(page);

  // Fuer den Assertions-Flow setzen wir einen klaren Startbestand (10 l).
  if (flowState.inventory[0]) {
    flowState.inventory[0].packagesInStock = "1";
    flowState.inventory[0].contentPerPackage = "10";
    flowState.inventory[0].totalStockAmount = "10";
  }

  await page.goto("/table-billing");

  await page.getByRole("button", { name: /T1/ }).click();
  await page.getByRole("button", { name: new RegExp(`${setup.drinkName} · ${setup.variantLabel}`) }).first().click();
  await page.getByRole("button", { name: "Bezahlen" }).click();
  await expect(page.getByRole("button", { name: /^T1$/ })).toHaveCount(0);

  await page.getByRole("link", { name: "Lagerverwaltung" }).click();
  await expect(page.getByRole("heading", { name: "Lagerbestand kompakt" })).toBeVisible();
  const soldLiters = Number(setup.variantLabel.replace(" l", "").replace(",", "."));
  const remaining = Math.max(0, 10 - soldLiters);
  const expectedStockText = `${remaining.toFixed(1).replace(".", ",")} l`;
  await expect(page.locator("body")).toContainText(expectedStockText);
});


