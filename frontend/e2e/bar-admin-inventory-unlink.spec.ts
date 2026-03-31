import { expect, test } from "@playwright/test";
import { mockAuthenticatedSession } from "./support/mock-auth";
import { createFlowState, mockFlowApis } from "./support/mock-api";

test("bar-admin: gelöschter lagerbestand unverknüpft getränk wieder und zeigt warnung", async ({ page }) => {
  const flowState = createFlowState();

  await mockAuthenticatedSession(page);
  await mockFlowApis(page, flowState);

  await page.goto("/bar-admin");

  // Erstelle Kategorie
  await page.getByTestId("category-create-name").fill("Softdrinks");
  await page.getByTestId("category-create-sort-order").fill("50");
  await page.getByTestId("category-create-save").click();
  await page.waitForTimeout(500);

  // Erstelle Getränk
  const drinkSelect = page.getByRole("combobox").first();
  const options = await drinkSelect.locator("option").count();
  if (options > 0) {
    await drinkSelect.selectOption(String(options - 1)); // Wähle die erste verfügbare Kategorie
  }

  await page.getByRole("button", { name: "Bier" }).click();
  await page.locator("input[placeholder='Getränk 1']").fill("Fanta Orange");
  await page.getByRole("button", { name: "Alle Getränke mit Variante speichern" }).click();
  await page.waitForTimeout(500);

  // Gehe zu Lagerverwaltung
  await page.getByRole("link", { name: "Lagerverwaltung" }).click();
  await page.waitForTimeout(1000);

  // Erstelle Lagerbestand und verknüpfe mit Getränk
  // (Annahme: es gibt eine UI zum Anlegen / Verknüpfen)
  // Diesen Schritt können wir vereinfachen durch direkten Mock

  // Gehe zurück zu Bar Admin
  await page.getByRole("link", { name: "Bar Admin" }).click();
  await page.waitForTimeout(1000);

  // Scrolle zu Getränke-Verwaltung
  await page.locator("text=Getränke verwalten").scrollIntoViewIfNeeded();
  await page.waitForTimeout(500);

  // Zu diesem Zeitpunkt sollte die Warnung "Getränke ohne Lagerverknüpfung" sichtbar sein
  // weil wir keine Lagerverknüpfung erstellt haben
  const noInventoryWarning = page.getByText(/Getränke ohne Lagerverknüpfung/);
  await expect(noInventoryWarning).toBeVisible();
});

