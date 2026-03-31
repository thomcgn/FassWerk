import { expect, test } from "@playwright/test";
import { mockAuthenticatedSession } from "./support/mock-auth";
import { createFlowState, mockFlowApis } from "./support/mock-api";
import { setupDrinkWithLinkedInventoryViaUi } from "./support/setup-flow";

test.setTimeout(60_000);

test("table-billing: zurueckgestellter Bon erscheint im unbezahlt-Archiv", async ({ page }) => {
  const archiveDate = process.env.E2E_FIXED_DATE ?? "2026-03-20";
  const flowState = createFlowState();

  await mockAuthenticatedSession(page);
  await mockFlowApis(page, flowState);

  const setup = await setupDrinkWithLinkedInventoryViaUi(page);

  await page.goto("/table-billing");

  await page.getByRole("button", { name: /T1/ }).click();
  await page.getByRole("button", { name: new RegExp(`${setup.drinkName} · ${setup.variantLabel}`) }).first().click();
  await page.getByRole("button", { name: "Zurückstellen" }).click();
  await expect(page.getByRole("button", { name: /^T1$/ })).toHaveCount(0);

  await page.getByLabel("Archivdatum").fill(archiveDate);
  await page.getByRole("button", { name: "Aktualisieren" }).click();

  await expect(page.getByRole("heading", { name: "Archiv: Unbezahlt" })).toBeVisible();
  await expect(page.getByText("Bon #900 · T1")).toBeVisible();

  const loadArchiveOrderButton = page.getByRole("button", { name: "Bon #900 laden" }).first();
  await expect(loadArchiveOrderButton).toBeVisible();
  await loadArchiveOrderButton.click();
  await expect(page.getByText("Tisch T1 · Bon #900")).toBeVisible();
});

