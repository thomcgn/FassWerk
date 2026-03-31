import { expect, test } from "@playwright/test";
import { mockAuthenticatedSession } from "./support/mock-auth";
import { createFlowState, mockFlowApis } from "./support/mock-api";
import { setupDrinkWithLinkedInventoryViaUi } from "./support/setup-flow";

test.setTimeout(60_000);

test("table-billing: unbezahlte Bons bleiben archiviert auch bei anderem Datum", async ({ page }) => {
  const flowState = createFlowState();

  await mockAuthenticatedSession(page);
  await mockFlowApis(page, flowState);

  const setup = await setupDrinkWithLinkedInventoryViaUi(page);

  await page.goto("/table-billing");
  await page.getByRole("button", { name: /T1/ }).click();
  const variantButton = page.getByRole("button", { name: `${setup.drinkName} · ${setup.variantLabel}` }).first();
  await expect(variantButton).toBeVisible();
  await variantButton.click();
  await page.getByRole("button", { name: "Zurückstellen" }).click();

  await expect(page.getByText("Bon #900 · T1")).toBeVisible();

  // absichtlich anderes Datum waehlen: unbezahlte Bons muessen trotzdem sichtbar bleiben
  await page.getByLabel("Archivdatum").fill("2035-01-01");
  await page.getByRole("button", { name: "Aktualisieren" }).click();

  await expect(page.getByText("Bon #900 · T1")).toBeVisible();

  // Navigation weg von Tische und zurueck darf den unbezahlten Bon nicht verlieren.
  await page.route("**/api/ops/auth-metrics", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        dayRevenue: "0",
        weekRevenue: "0",
        monthRevenue: "0",
        dayConsumedMl: "0",
        weekConsumedMl: "0",
        monthConsumedMl: "0",
        strongestWeekday: "-",
        weekPoints: [],
        monthPoints: [],
      }),
    });
  });

  await page.goto("/ops/auth-metrics");
  await expect(page.getByRole("button", { name: "Aktualisieren" })).toBeVisible();

  await page.goto("/table-billing");
  await expect(page.getByRole("heading", { name: "Archiv: Unbezahlt" })).toBeVisible();
  await expect(page.getByText("Bon #900 · T1")).toBeVisible();
});



