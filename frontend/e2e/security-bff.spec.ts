import { test, expect } from "@playwright/test";

test("BFF rejects cross-origin login before processing credentials", async ({ request }) => {
  const response = await request.post("/api/auth/login", {
    headers: { Origin: "https://untrusted.example", "Sec-Fetch-Site": "cross-site" },
    data: {},
  });
  expect(response.status()).toBe(403);
  expect(await response.json()).toEqual({ message: "Cross-origin request denied" });
});

test("BFF rejects unsafe requests without an Origin", async ({ request }) => {
  const response = await request.post("/api/auth/login", { data: {} });
  expect(response.status()).toBe(403);
});

test("BFF permits same-origin requests to reach validation", async ({ request }) => {
  const response = await request.post("/api/auth/login", {
    headers: { Origin: "http://127.0.0.1:3000", "Sec-Fetch-Site": "same-origin" },
    data: {},
  });
  expect(response.status()).toBe(400);
  expect(response.headers()["x-content-type-options"]).toBe("nosniff");
  expect(response.headers()["x-frame-options"]).toBe("DENY");
});

test("BFF public read remains available without authentication", async ({ request }) => {
  const response = await request.get("/api/auth/status");
  expect(response.status()).toBe(200);
});

test("BFF does not equate different loopback origins", async ({ request }) => {
  const response = await request.post("/api/auth/login", {
    headers: { Origin: "http://localhost:3000", "Sec-Fetch-Site": "same-site" },
    data: {},
  });
  expect(response.status()).toBe(403);
});
