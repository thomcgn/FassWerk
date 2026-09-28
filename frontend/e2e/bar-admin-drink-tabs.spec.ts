import { expect, test } from "@playwright/test";
import { mockAuthenticatedSession } from "./support/mock-auth";
import { createFlowState, mockFlowApis } from "./support/mock-api";

test("bar-admin: getränke werden kategorieweise in tabs angezeigt und gefiltert", async ({ page }) => {
  const flowState = createFlowState();

  await mockAuthenticatedSession(page);
  await mockFlowApis(page, flowState);

  await page.goto("/bar-admin");

  for (const [name, sortOrder] of [["Biere", "10"], ["Weine", "20"]]) {
    await page.getByTestId("category-create-name").fill(name);
    await page.getByTestId("category-create-sort-order").fill(sortOrder);
    await page.getByTestId("category-create-save").click();
    await expect(page.getByTestId("category-create-name")).toHaveValue("");
    await expect(page.locator("button[data-testid^='drink-category-tab-']").filter({ hasText: name })).toBeVisible();
  }

  const tabs = page.locator("button[data-testid^='drink-category-tab-']");
  await expect(tabs).toHaveCount(2);
  const firstTab = tabs.nth(0);
  const secondTab = tabs.nth(1);
  await expect(firstTab).toHaveClass(/border-cyan-400/);
  await secondTab.click();
  await expect(secondTab).toHaveClass(/border-cyan-400/);
  await expect(firstTab).not.toHaveClass(/border-cyan-400/);
  await firstTab.click();
  await expect(firstTab).toHaveClass(/border-cyan-400/);
});
