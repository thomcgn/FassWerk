import { expect, test, type Page } from "@playwright/test";
import { mockAuthenticatedSession } from "./support/mock-auth";
import { createFlowState, mockFlowApis } from "./support/mock-api";

async function createCategory(page: Page, name: string, sortOrder: string) {
  await page.getByTestId("category-create-name").fill(name);
  await page.getByTestId("category-create-sort-order").fill(sortOrder);
  await page.getByTestId("category-create-save").click();
}

async function openCategoryEditModal(page: Page, name: string, sortOrder: number) {
  const categoryRow = page.locator("div", { hasText: name }).filter({ hasText: `Sortierung: ${sortOrder}` }).first();
  await expect(categoryRow).toBeVisible();
  await categoryRow.getByRole("button", { name: `Kategorie ${name} bearbeiten` }).click();
  const modal = page.getByTestId("category-edit-modal");
  await expect(modal.getByRole("heading", { name: "Kategorie bearbeiten" })).toBeVisible();
  return modal;
}

test("bar-admin: kategorie nachtraeglich in name und sortierung bearbeiten", async ({ page }) => {
  const flowState = createFlowState();

  await mockAuthenticatedSession(page);
  await mockFlowApis(page, flowState);

  await page.goto("/bar-admin");

  await createCategory(page, "Bier", "10");
  const modal = await openCategoryEditModal(page, "Bier", 10);
  await modal.getByTestId("category-edit-name").fill("Bier Spezial");
  await modal.getByTestId("category-edit-sort-order").fill("15");
  await modal.getByTestId("category-edit-save").click();

  const updatedRow = page.locator("div", { hasText: "Bier Spezial" }).filter({ hasText: "Sortierung: 15" }).first();
  await expect(updatedRow).toBeVisible();
  await expect(page.getByText("Kategorie aktualisiert.")).toBeVisible();
});

test("bar-admin: kategorie-validierung greift bei leerem namen", async ({ page }) => {
  const flowState = createFlowState();

  await mockAuthenticatedSession(page);
  await mockFlowApis(page, flowState);

  await page.goto("/bar-admin");
  await createCategory(page, "Cocktails", "20");

  const modal = await openCategoryEditModal(page, "Cocktails", 20);
  await modal.getByTestId("category-edit-name").fill("   ");
  await modal.getByTestId("category-edit-save").click();

  await expect(page.getByText("Bitte einen Kategorienamen eingeben.")).toBeVisible();
  await expect(modal).toBeVisible();
});

test("bar-admin: kategorie-validierung greift bei negativer sortierung", async ({ page }) => {
  const flowState = createFlowState();

  await mockAuthenticatedSession(page);
  await mockFlowApis(page, flowState);

  await page.goto("/bar-admin");
  await createCategory(page, "Softdrinks", "30");

  const modal = await openCategoryEditModal(page, "Softdrinks", 30);
  await modal.getByTestId("category-edit-sort-order").fill("-1");
  await modal.getByTestId("category-edit-save").click();

  await expect(page.getByText("Sortierung muss eine ganze Zahl >= 0 sein.")).toBeVisible();
  await expect(modal).toBeVisible();
});

test("bar-admin: kategorie-bearbeitung zeigt konfliktmeldung bei 409", async ({ page }) => {
  const flowState = createFlowState();

  await mockAuthenticatedSession(page);
  await mockFlowApis(page, flowState);
  await page.route(/.*\/api\/drink-categories\/(\d+)$/, async (route) => {
    if (route.request().method() === "PUT") {
      await route.fulfill({
        status: 409,
        contentType: "application/json",
        body: JSON.stringify({ message: "Ein Eintrag mit diesem Namen existiert bereits." }),
      });
      return;
    }
    await route.fallback();
  });

  await page.goto("/bar-admin");
  await createCategory(page, "Shots", "40");

  const modal = await openCategoryEditModal(page, "Shots", 40);
  await modal.getByTestId("category-edit-name").fill("Shots Premium");
  await modal.getByTestId("category-edit-save").click();

  await expect(page.getByText("Ein Eintrag mit diesem Namen existiert bereits.")).toBeVisible();
  await expect(modal).toBeVisible();
});




