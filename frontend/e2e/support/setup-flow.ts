import { expect } from "@playwright/test";
import type { Page } from "@playwright/test";

type SetupResult = {
  drinkName: string;
  inventoryName: string;
  variantLabel: string;
};

export async function setupDrinkWithLinkedInventoryViaUi(page: Page): Promise<SetupResult> {
  const drinkName = "E2E Lagerbier";
  const inventoryName = "E2E Lagerfass";
  let variantLabel = "0,5 l";

  await page.goto("/bar-admin");
  await page.getByRole("heading", { name: "Bar Admin" }).waitFor();

  await page.getByPlaceholder("z. B. Weine").fill("Bier Spezial");
  await page.getByRole("button", { name: "Kategorie speichern" }).click();

  await page.getByPlaceholder(/Get.*1/i).fill(drinkName);
  await page.getByRole("button", { name: "Alle Getränke mit Variante speichern" }).click();

  await page.goto("/inventory");
  await page.getByRole("heading", { name: "Lagerbestand kompakt" }).waitFor();

  const showConfigButton = page.getByRole("button", { name: /Konfiguration einblenden/ });
  if (await showConfigButton.count()) {
    await showConfigButton.first().click();
  }

  await expect(page.locator("#item-drink-link")).toContainText(drinkName);

  await page.getByLabel("Artikelname").fill(inventoryName);
  await page.locator("#item-package-type").selectOption("BARREL");
  await page.locator("#item-packages-stock").fill("1");
  await page.locator("#item-content-per-package").fill("10");
  await page.locator("#item-drink-link").selectOption({ label: drinkName });
  
  // Wait until at least one concrete variant option for the created drink is available.
  await expect.poll(async () => {
    const optionTexts = await page.locator("#item-variant-link option").allTextContents();
    return optionTexts.filter((text) => text.includes(drinkName) && /\d/.test(text)).length;
  }).toBeGreaterThan(0);

  const variantOptions = await page.locator("#item-variant-link option").allTextContents();
  const matchingVariant = variantOptions.find((text) => text.includes(drinkName) && /\d/.test(text));
  if (!matchingVariant) {
    // Debug: Log available options
    console.log("Available variant options:", variantOptions);
    throw new Error(`Keine passende Variante fuer die Drink-Verknuepfung gefunden. Verfuegbare Optionen: ${variantOptions.join(", ")}`);
  }
  variantLabel = matchingVariant.includes("·")
    ? (matchingVariant.split("·")[1]?.trim() || variantLabel)
    : matchingVariant.replace(drinkName, "").replace("-", "").trim() || variantLabel;
  await page.locator("#item-variant-link").selectOption({ label: matchingVariant.trim() });
  await page.getByRole("button", { name: "Lagerartikel anlegen" }).click();

  await page.getByText("Lagerkonfiguration wurde angelegt.").first().waitFor();

  return { drinkName, inventoryName, variantLabel };
}

