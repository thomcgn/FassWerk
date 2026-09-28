import { defineConfig, devices } from "@playwright/test";

const production = !!process.env.CI || process.env.E2E_PRODUCTION === "true";

export default defineConfig({
  testDir: "./e2e",
  timeout: 30_000,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  // Keep load consistent across local and CI runs.
  workers: 1,
  reporter: [
    ["list"],
    ["html", { open: "never" }],
    ["junit", { outputFile: "test-results/playwright/results.xml" }],
  ],
  use: {
    baseURL: "http://127.0.0.1:3000",
    trace: "retain-on-failure",
    timezoneId: "Europe/Berlin",
  },
  webServer: {
    command: production
      ? "npm run start -- --hostname 127.0.0.1 --port 3000"
      : "npm run dev -- --hostname 127.0.0.1 --port 3000",
    env: { APP_ORIGIN: "http://127.0.0.1:3000" },
    url: "http://127.0.0.1:3000/login",
    reuseExistingServer: false,
    timeout: 120_000,
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});

