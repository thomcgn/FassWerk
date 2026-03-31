import { expect, test } from "@playwright/test";
import { mockAuthenticatedSession } from "./support/mock-auth";
import { createFlowState, mockFlowApis } from "./support/mock-api";
import { setupDrinkWithLinkedInventoryViaUi } from "./support/setup-flow";

test("tischbon: entfernen reduziert nur eine einzelne Position", async ({ page }) => {
  const flowState = createFlowState();

  await mockAuthenticatedSession(page);
  await mockFlowApis(page, flowState);

  const setup = await setupDrinkWithLinkedInventoryViaUi(page);

  await page.goto("/table-billing");
  await page.getByRole("button", { name: /T1/ }).click();

  const variantButton = page.getByRole("button", { name: new RegExp(`${setup.drinkName} · ${setup.variantLabel}`) }).first();
  await variantButton.click();
  await variantButton.click();

  await expect(page.getByText("2 ×")).toBeVisible();

  const removeButton = page.getByRole("button", { name: "Entfernen" }).first();
  await removeButton.click();
  await expect(page.getByText("1 ×")).toBeVisible();

  await removeButton.click();
  await expect(page.getByText("Noch keine Positionen auf dem Bon.")).toBeVisible();
});

