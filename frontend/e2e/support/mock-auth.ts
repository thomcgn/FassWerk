import type { Page } from "@playwright/test";

export async function mockAuthenticatedSession(page: Page) {
  await page.context().addCookies([
    {
      name: "fw_access_token",
      value: "test-access",
      domain: "127.0.0.1",
      path: "/",
      httpOnly: true,
      sameSite: "Lax",
    },
    {
      name: "fw_refresh_token",
      value: "test-refresh",
      domain: "127.0.0.1",
      path: "/",
      httpOnly: true,
      sameSite: "Lax",
    },
  ]);

  await page.route("**/api/auth/login", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        accessToken: "test-access",
        refreshToken: "test-refresh",
        tokenType: "Bearer",
        accessExpiresInSeconds: 7200,
        refreshExpiresInSeconds: 1209600,
        role: "ADMIN",
        displayName: "E2E Admin",
      }),
      headers: {
        "set-cookie": "fw_access_token=test-access; Path=/; HttpOnly",
      },
    });
  });

  await page.route("**/api/auth/status", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ authenticated: true, role: "ADMIN" }),
    });
  });
}

export async function loginViaUi(page: Page) {
  await page.goto("/login");
  await page.getByRole("button", { name: "Zum Dashboard" }).click();
}

