import { test, expect } from "@playwright/test";

test("login -> sessions revoke -> logout-all flow", async ({ page }) => {
  await page.route("**/api/auth/login", async (route) => {
    await route.fulfill({ status: 200, body: JSON.stringify({ role: "ADMIN", displayName: "Admin" }) });
  });

  await page.route("**/api/inventory", async (route) => {
    await route.fulfill({
      status: 200,
      body: JSON.stringify([
        {
          id: 1,
          name: "Guinness Stock",
          linkedDrinkId: null,
          linkedDrinkVariantId: null,
          packageType: "BARREL",
          packagesInStock: "2.00",
          contentPerPackage: "30000.00",
          contentUnit: "MILLILITER",
          totalStockAmount: "60000.00",
          reorderThreshold: "30000.00",
          minimumStock: "20000.00",
          recommendedReorderAmount: "60000.00",
          supplier: "Dublin Beverages",
          active: true,
        },
      ]),
    });
  });

  let sessions = [
    {
      id: 101,
      tokenId: "current-token-111111",
      createdAt: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 3600_000).toISOString(),
      lastUsedAt: new Date().toISOString(),
      userAgent: "Chrome on Linux (Desktop)",
      ipAddress: "127.0.0.1",
      current: true,
    },
    {
      id: 102,
      tokenId: "other-token-222222",
      createdAt: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 3600_000).toISOString(),
      lastUsedAt: null,
      userAgent: "Safari on iOS (Mobile)",
      ipAddress: "10.0.0.10",
      current: false,
    },
  ];

  await page.route("**/api/auth/sessions", async (route) => {
    await route.fulfill({ status: 200, body: JSON.stringify(sessions) });
  });

  await page.route("**/api/auth/sessions/102", async (route) => {
    sessions = sessions.filter((entry) => entry.id !== 102);
    await route.fulfill({ status: 204 });
  });

  await page.route("**/api/auth/logout-all", async (route) => {
    await route.fulfill({ status: 204 });
  });

  await page.goto("/login");
  await page.getByRole("button", { name: "Anmelden" }).click();

  await expect(page).toHaveURL(/\/inventory$/);
  await expect(page.getByRole("heading", { name: "Inventory" })).toBeVisible();

  await page.getByRole("link", { name: "Sessions" }).click();
  await expect(page).toHaveURL(/\/sessions$/);
  await expect(page.getByRole("heading", { name: "Aktive Sessions" })).toBeVisible();

  await page.getByRole("button", { name: "Beenden" }).click();
  await expect(page.locator("text=other-token-2")).toHaveCount(0);

  await page.getByRole("button", { name: "Logout All" }).click();
  await expect(page).toHaveURL(/\/login$/);
});

