import { test } from "node:test";
import assert from "node:assert/strict";
import { parseTableOrders, readCachedOrders, selectSellableCatalog, sortByClosedAtDesc } from "../features/billing/model.ts";
import { calculateCrateContentPerPackage, formatStockForArticle, getInventoryStatus } from "../features/inventory/model.ts";
import { calculateAdjustedPrice, prepareDrinkBatch } from "../features/catalog/model.ts";
import { groupReservationsByStatus, partitionReservations } from "../features/reservation/model.ts";

const category = { id: 1, name: "Bier", sortOrder: 1, active: true };
const drinks = [
  { id: 10, categoryId: 1, categoryName: "Bier", name: "Pils", description: null, imageUrl: null, active: true },
  { id: 11, categoryId: 1, categoryName: "Bier", name: "Alt", description: null, imageUrl: null, active: true },
];
const variants = [
  { id: 100, drinkId: 10, drinkName: "Pils", displayVolumeName: "0,3 l", volumeMl: 300, price: 3.5, useStandardPrice: false, sku: null, active: true },
  { id: 101, drinkId: 10, drinkName: "Pils", displayVolumeName: "0,5 l", volumeMl: 500, price: 5, useStandardPrice: false, sku: null, active: true },
  { id: 102, drinkId: 11, drinkName: "Alt", displayVolumeName: "0,3 l", volumeMl: 300, price: 3.4, useStandardPrice: false, sku: null, active: true },
];
function stock(overrides = {}) {
  return { id: 1, name: "Fass", linkedDrinkId: null, linkedDrinkVariantId: null, packageType: "BARREL", packagesInStock: 1, contentPerPackage: 50, contentUnit: "LITER", totalStockAmount: 50, reorderThreshold: 10, minimumStock: 5, recommendedReorderAmount: 50, supplier: null, active: true, ...overrides };
}
function order(id, closedAt) {
  return { id, tableId: 1, tableName: "T1", reservationId: null, status: "CLOSED", paid: false, openedAt: "2026-09-28T10:00:00Z", closedAt, total: 4, items: [] };
}

test("billing catalog only exposes variants backed by positive active stock", () => {
  const result = selectSellableCatalog(variants, [
    stock({ id: 1, linkedDrinkVariantId: 100 }),
    stock({ id: 2, linkedDrinkId: 11, packageType: "CRATE" }),
    stock({ id: 3, linkedDrinkVariantId: 101, totalStockAmount: 0 }),
  ], drinks, [category]);
  assert.deepEqual(result.variants.map(({ id }) => id), [100, 102]);
  assert.deepEqual(result.drinks.map(({ id }) => id), [10, 11]);
});

test("drink fallback is disabled when several stock entries target the same drink", () => {
  const result = selectSellableCatalog(variants, [stock({ id: 1, linkedDrinkId: 10 }), stock({ id: 2, linkedDrinkId: 10 })], drinks, [category]);
  assert.deepEqual(result.variants, []);
});

test("billing cache rejects malformed data and archive sorting is deterministic", () => {
  assert.deepEqual(readCachedOrders('{"unexpected":true}'), []);
  assert.equal(parseTableOrders([{ id: "wrong" }]), null);
  assert.deepEqual(sortByClosedAtDesc([order(1, null), order(2, "2026-09-28T12:00:00Z")]).map(({ id }) => id), [2, 1]);
});

test("inventory calculations handle crate units and thresholds", () => {
  assert.equal(calculateCrateContentPerPackage("LITER", "12", "0,75"), "9");
  assert.equal(calculateCrateContentPerPackage("MILLILITER", "12", "0.75"), "9000");
  assert.equal(calculateCrateContentPerPackage("PIECE", "24", "invalid"), "24");
  assert.equal(getInventoryStatus(stock({ totalStockAmount: 10, reorderThreshold: 10 })).label, "Nachbestellen");
  assert.deepEqual(formatStockForArticle(stock({ packageType: "CRATE", linkedDrinkVariantId: 100, totalStockAmount: 9, contentPerPackage: 9 }), variants), { primary: "30 Flaschen", secondary: "9 l" });
});

test("drink batches are normalized and reject duplicates or unknown volumes", () => {
  const valid = prepareDrinkBatch("1", [{ id: "d1", name: " Pils ", description: " frisch " }], [{ id: "v1", label: "0,3 l", price: "3.5" }]);
  assert.equal(valid.error, null);
  assert.deepEqual(valid.batch?.variants[0], { id: "v1", label: "0,3 l", price: 3.5, volumeMl: 300 });
  assert.match(prepareDrinkBatch("1", [{ id: "1", name: "Pils", description: "" }, { id: "2", name: "pils", description: "" }], [{ id: "v", label: "0,3 l", price: "3" }]).error ?? "", /doppelte Getränkenamen/);
  assert.match(prepareDrinkBatch("1", [{ id: "1", name: "Pils", description: "" }], [{ id: "v", label: "1 l", price: "3" }]).error ?? "", /Unbekanntes Label/);
});

test("price adjustments round to cents and never become negative", () => {
  assert.equal(calculateAdjustedPrice("4.95", "10", "PERCENT"), 5.45);
  assert.equal(calculateAdjustedPrice("1.00", "-5", "ABSOLUTE"), 0);
  assert.equal(calculateAdjustedPrice("invalid", "2", "ABSOLUTE"), null);
});


test("reservation view models separate actionable and terminal states", () => {
  const base = { id: 1, guestName: "Team", contactEmail: null, contactPhone: null, reservationDate: "2026-09-28", reservationTime: "18:00", guestCount: 4, expiresAt: null, checkedInAt: null, qrCodeToken: "token", qrScanUrl: "/scan/token" };
  const reservations = [{ ...base, status: "CONFIRMED" }, { ...base, id: 2, status: "CHECKED_IN" }, { ...base, id: 3, status: "NO_SHOW" }];
  const partitioned = partitionReservations(reservations);
  assert.deepEqual(partitioned.actionable.map(({ id }) => id), [1, 2]);
  assert.deepEqual(partitioned.closed.map(({ id }) => id), [3]);
  assert.equal(groupReservationsByStatus(reservations).NO_SHOW.length, 1);
});
