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
  await page.getByRole("button", { name: "Teilzahlung" }).first().click();
  const table = page.getByRole("button", { name: /T1/ });
  await expect(table).toContainText("FREE");
  await table.click();
  await expect(page.getByText("Noch keine Positionen auf dem Bon.")).toBeVisible();
});




test("archived tab frees table for a separate new bill and preserves debt", async ({ page }) => {
  const flowState = createFlowState();
  await mockAuthenticatedSession(page);
  await mockFlowApis(page, flowState);
  await page.goto("/table-billing");
  const table = page.getByRole("button", { name: /T1/ });
  await table.click();
  await expect(page.getByRole("button", { name: "Zurückstellen", exact: true })).toBeEnabled();
  const oldId = flowState.orders[0].id;
  await page.getByRole("button", { name: "Zurückstellen", exact: true }).click();
  await expect(table).toContainText("FREE");
  await table.click();
  const dialog = page.getByRole("dialog", { name: "Tischdetail" });
  await expect(dialog.getByRole("button", { name: "Bezahlen", exact: true })).toBeEnabled();
  expect(flowState.orders).toHaveLength(2);
  expect(flowState.orders.find(order => order.id === oldId)?.paid).toBe(false);
  expect(flowState.orders.find(order => order.id === oldId)?.status).toBe("CLOSED");
  await dialog.getByRole("button", { name: "Bezahlen", exact: true }).click();
  await expect(table).toContainText("FREE");
  await expect(page.getByRole("button", { name: `Bon #${oldId} laden`, exact: true })).toBeVisible();
});
