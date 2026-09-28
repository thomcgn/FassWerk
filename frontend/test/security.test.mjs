import { test } from "node:test";
import assert from "node:assert/strict";
import { isAllowedApiRequest } from "../lib/csrf.ts";
import { createRefreshCoordinator } from "../lib/refresh-coordinator.ts";

test("unsafe cookie requests require the exact origin", () => {
  for (const origin of [null, "null", "https://evil.test", "http://bar.test", "https://bar.test:8443",
    "https://user@bar.test", "https://bar.test/path", "https://bar.test?query"]) {
    assert.equal(isAllowedApiRequest("POST", origin, null, "https://bar.test"), false);
  }
  assert.equal(isAllowedApiRequest("PUT", "https://bar.test", "same-origin", "https://bar.test"), true);
  assert.equal(isAllowedApiRequest("DELETE", "https://bar.test", "cross-site", "https://bar.test"), false);
  assert.equal(isAllowedApiRequest("GET", null, null, "https://bar.test"), true);
});

test("parallel and slightly delayed refresh calls share one rotation", async () => {
  let calls = 0;
  let now = 0;
  const coordinate = createRefreshCoordinator(3000, 2, () => now);
  const rotate = async () => { calls++; return { successor: "test-only" }; };
  const results = await Promise.all([coordinate("a", rotate), coordinate("a", rotate)]);
  assert.equal(calls, 1);
  assert.equal(results[0], results[1]);
  assert.equal(await coordinate("a", rotate), results[0]);
  assert.equal(calls, 1);
  now = 3001;
  await coordinate("a", rotate);
  assert.equal(calls, 2);
});

test("coordination separates sessions, bounds memory and does not cache transport errors", async () => {
  const coordinate = createRefreshCoordinator(3000, 1);
  await assert.rejects(coordinate("a", async () => { throw new Error("transport"); }));
  assert.equal(await coordinate("a", async () => "success"), "success");
  await assert.rejects(coordinate("b", async () => "other"), /capacity/);
});
