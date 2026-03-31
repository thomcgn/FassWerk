import { test, expect } from "@playwright/test";
import { mockAuthenticatedSession } from "./support/mock-auth";
import { createFlowState, mockFlowApis } from "./support/mock-api";
import { setupDrinkWithLinkedInventoryViaUi } from "./support/setup-flow";

test.setTimeout(60_000);

test("archive loads successfully with empty database", async ({ page }) => {
  const flowState = createFlowState();

  // Mock auth with empty state (no table orders)
  await mockAuthenticatedSession(page);
  await mockFlowApis(page, flowState);

  // Navigate to table billing
  await page.goto("/table-billing");

  const archiveHeading = page.getByRole("heading", { name: "Archiv: Unbezahlt" });
  await expect(archiveHeading).toBeVisible();

  // Check that archive is either empty OR shows unpaid orders, but NO error
  const archiveError = page.getByText("Archiv konnte nicht geladen werden");
  const archiveEmpty = page.getByText("Keine unbezahlten Bons im Archiv");

  // Should NOT have an error
  await expect(archiveError).not.toBeVisible();

  await expect(archiveEmpty).toBeVisible();
});

test("unpaid order persists in archive after navigation", async ({ page }) => {
  const flowState = createFlowState();
  const archiveDate = process.env.E2E_FIXED_DATE ?? "2026-03-20";

  await mockAuthenticatedSession(page);
  await mockFlowApis(page, flowState);

  const setup = await setupDrinkWithLinkedInventoryViaUi(page);

  await page.goto("/table-billing");
  await expect(page.getByRole("heading", { name: "Archiv: Unbezahlt" })).toBeVisible();

  await page.getByRole("button", { name: /T1/ }).click();
  await page.getByRole("button", { name: new RegExp(`${setup.drinkName} · ${setup.variantLabel}`) }).first().click();
  await page.getByRole("button", { name: "Zurückstellen" }).click();

  await page.getByLabel("Archivdatum").fill(archiveDate);
  await page.getByRole("button", { name: "Aktualisieren" }).click();
  await expect(page.getByText("Bon #900 · T1")).toBeVisible();

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

  const archiveError = page.getByText("Archiv konnte nicht geladen werden");
  await expect(archiveError).not.toBeVisible();
});

