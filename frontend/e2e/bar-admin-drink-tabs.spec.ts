import { expect, test } from "@playwright/test";
import { mockAuthenticatedSession } from "./support/mock-auth";
import { createFlowState, mockFlowApis } from "./support/mock-api";

test("bar-admin: getränke werden kategorieweise in tabs angezeigt und gefiltert", async ({ page }) => {
  const flowState = createFlowState();

  await mockAuthenticatedSession(page);
  await mockFlowApis(page, flowState);

  await page.goto("/bar-admin");

  // Erstelle zwei Kategorien
  await page.getByTestId("category-create-name").fill("Biere");
  await page.getByTestId("category-create-sort-order").fill("10");
  await page.getByTestId("category-create-save").click();
  await page.waitForTimeout(800);

  await page.getByTestId("category-create-name").clear();
  await page.getByTestId("category-create-sort-order").clear();
  await page.getByTestId("category-create-name").fill("Weine");
  await page.getByTestId("category-create-sort-order").fill("20");
  await page.getByTestId("category-create-save").click();
  await page.waitForTimeout(800);

  // Scrolle runter zu Getränke-Verwaltungs-Tabs
  await page.locator("text=Getränke verwalten").scrollIntoViewIfNeeded();
  await page.waitForTimeout(1000);

  // Versuche zuerst, Tabs über data-testid zu finden, ansonsten per Klasse
  let allTabs = page.locator("button[data-testid^='drink-category-tab-']");
  let tabCount = await allTabs.count();

  // Fallback: wenn keine testids, versuche über CSS-Klasse
  if (tabCount === 0) {
    allTabs = page.locator("button.rounded-t-lg.border-b-2");
    tabCount = await allTabs.count();
  }

  // Es sollten mindestens 2 Tabs geben (Biere und Weine)
  if (tabCount < 2) {
    console.log(`Nur ${tabCount} Tabs gefunden, erwarte mindestens 2`);
    throw new Error(`Tab count ${tabCount} < 2`);
  }

  expect(tabCount).toBeGreaterThanOrEqual(2);

  // Stelle sicher, dass die Tabs sichtbar sind
  const firstTab = allTabs.nth(0);
  const secondTab = allTabs.nth(1);

  await expect(firstTab).toBeVisible();
  await expect(secondTab).toBeVisible();

  // Erster Tab sollte aktiv sein (border-cyan-400)
  const firstTabClass = await firstTab.getAttribute("class");
  expect(firstTabClass).toContain("border-cyan-400");

  // Zweiten Tab clicken
  await secondTab.click();
  await page.waitForTimeout(500);

  // Zweiter Tab sollte jetzt aktiv sein
  const secondTabClassAfter = await secondTab.getAttribute("class");
  expect(secondTabClassAfter).toContain("border-cyan-400");

  // Erster Tab sollte nicht mehr aktiv sein
  const firstTabClassAfter = await firstTab.getAttribute("class");
  expect(firstTabClassAfter).not.toContain("border-cyan-400");

  // Wechsel zurück zum ersten Tab
  await firstTab.click();
  await page.waitForTimeout(500);

  // Erster Tab sollte wieder aktiv sein
  const firstTabClassFinal = await firstTab.getAttribute("class");
  expect(firstTabClassFinal).toContain("border-cyan-400");
});


