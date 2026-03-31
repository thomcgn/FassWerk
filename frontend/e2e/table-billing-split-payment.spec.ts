import { expect, test } from "@playwright/test";
import { mockAuthenticatedSession } from "./support/mock-auth";
import { createFlowState, mockFlowApis } from "./support/mock-api";
import { setupDrinkWithLinkedInventoryViaUi } from "./support/setup-flow";

test("tischbon: position buchen und split-payment durchfuehren", async ({ page }) => {
  const flowState = createFlowState();

  await mockAuthenticatedSession(page);
  await mockFlowApis(page, flowState);

  const setup = await setupDrinkWithLinkedInventoryViaUi(page);

  await page.goto("/table-billing");

  await expect(page.getByRole("heading", { name: "Tischabrechnung für dein Team." })).toBeVisible();
  await page.getByRole("button", { name: /T1/ }).click();

  await page.getByRole("button", { name: new RegExp(`${setup.drinkName} · ${setup.variantLabel}`) }).first().click();
  await page.getByRole("button", { name: new RegExp(`${setup.drinkName} · ${setup.variantLabel}`) }).first().click();

  await expect(page.getByText("2 ×")).toBeVisible();
  await page.getByRole("button", { name: "Teilzahlung" }).first().click();
  await expect(page.getByText("1 ×")).toBeVisible();
});


