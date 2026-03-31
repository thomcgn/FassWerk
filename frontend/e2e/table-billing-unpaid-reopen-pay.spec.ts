import { expect, test } from "@playwright/test";
import { mockAuthenticatedSession } from "./support/mock-auth";
import { createFlowState, mockFlowApis } from "./support/mock-api";
import { setupDrinkWithLinkedInventoryViaUi } from "./support/setup-flow";

test.setTimeout(60_000);

test("table-billing: unbezahlten Bon aus Archiv wieder oeffnen und bezahlen", async ({ page }) => {
  const flowState = createFlowState();

  await mockAuthenticatedSession(page);
  await mockFlowApis(page, flowState);

  const setup = await setupDrinkWithLinkedInventoryViaUi(page);

  await page.goto("/table-billing");
  await page.getByRole("button", { name: /T1/ }).click();
  await page.getByRole("button", { name: new RegExp(`${setup.drinkName} · ${setup.variantLabel}`) }).first().click();

  await page.getByRole("button", { name: "Zurückstellen" }).click();
  await expect(page.getByText("Bon #900 · T1")).toBeVisible();

  const reopenOk = await page.evaluate(async () => {
    const response = await fetch("/api/table-orders/900/reopen-unpaid", { method: "POST" });
    return response.ok;
  });
  expect(reopenOk).toBeTruthy();
  await page.goto("/table-billing");

  const tableButton = page.getByRole("button", { name: /T1/ }).first();
  await expect(tableButton).toBeVisible();
  await tableButton.click();
  await page.getByRole("button", { name: "Bezahlen" }).click();

  await expect(page.getByRole("button", { name: /T1/ })).toHaveCount(0);
  await expect(page.getByText("Bon #900 · T1")).toHaveCount(0);
});




