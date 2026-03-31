import { expect, test } from "@playwright/test";
import { createFlowState, mockFlowApis } from "./support/mock-api";
import { mockAuthenticatedSession } from "./support/mock-auth";
import { setupDrinkWithLinkedInventoryViaUi } from "./support/setup-flow";

test("tischbon wird erst nach Lager+Bar-Setup buchbar", async ({ page }) => {
  const flowState = createFlowState();

  await mockAuthenticatedSession(page);
  await mockFlowApis(page, flowState);

  await page.goto("/table-billing");
  await page.getByRole("button", { name: /T1/ }).click();

  await expect(page.getByText("Noch keine Positionen auf dem Bon.")).toBeVisible();
  await expect(page.getByText("Varianten antippen")).toBeVisible();
  await expect(page.getByRole("button", { name: /0,5 l/ })).toHaveCount(0);

  const setup = await setupDrinkWithLinkedInventoryViaUi(page);

  await page.goto("/table-billing");
  await page.getByRole("button", { name: /T1/ }).click();
  await expect(page.getByRole("button", { name: new RegExp(`${setup.drinkName} · ${setup.variantLabel}`) })).toBeVisible();
});

