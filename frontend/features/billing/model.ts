import { isRecord, parseArray } from "../../lib/api-client.ts";
import type { Drink, DrinkCategory, DrinkVariant, InventoryItem, SplitPaymentResponse, Table, TableOrder, TableOrderItem } from "../../types/api.ts";

const isNumber = (value: unknown): value is number => typeof value === "number" && Number.isFinite(value);
const isString = (value: unknown): value is string => typeof value === "string";
const isNullableString = (value: unknown): value is string | null => value === null || isString(value);
const isNullableNumber = (value: unknown): value is number | null => value === null || isNumber(value);

function parseOrderItem(value: unknown): TableOrderItem | null {
  if (!isRecord(value) || !isNumber(value.id) || !isNumber(value.drinkVariantId) || !isString(value.drinkLabel)
    || !isNumber(value.quantity) || !isString(value.unitPrice) || !isString(value.totalPrice) || !isString(value.deductedVolumeMl)) return null;
  return {
    id: value.id, drinkVariantId: value.drinkVariantId, drinkLabel: value.drinkLabel,
    quantity: value.quantity, unitPrice: value.unitPrice, totalPrice: value.totalPrice,
    deductedVolumeMl: value.deductedVolumeMl,
  };
}

export function parseTableOrder(value: unknown): TableOrder | null {
  if (!isRecord(value) || !isNumber(value.id) || !isNumber(value.tableId) || !isString(value.tableName)
    || !isNullableNumber(value.reservationId) || (value.status !== "OPEN" && value.status !== "CLOSED")
    || typeof value.paid !== "boolean" || !isString(value.openedAt) || !isNullableString(value.closedAt)
    || !isString(value.total)) return null;
  const items = parseArray(value.items, parseOrderItem);
  return items === null ? null : {
    id: value.id, tableId: value.tableId, tableName: value.tableName, reservationId: value.reservationId,
    status: value.status, paid: value.paid, openedAt: value.openedAt, closedAt: value.closedAt,
    total: value.total, items,
  };
}

export const parseTableOrders = (value: unknown): TableOrder[] | null => parseArray(value, parseTableOrder);

export function parseTable(value: unknown): Table | null {
  if (!isRecord(value) || !isNumber(value.id) || !isString(value.name) || !isNullableString(value.area)
    || (value.status !== "FREE" && value.status !== "OCCUPIED" && value.status !== "RESERVED" && value.status !== "READY_FOR_PAYMENT")
    || typeof value.active !== "boolean") return null;
  const seats = value.seats;
  if (seats !== undefined && !isNullableNumber(seats)) return null;
  return { id: value.id, name: value.name, area: value.area, status: value.status, active: value.active, seats };
}

export const parseTables = (value: unknown): Table[] | null => parseArray(value, parseTable);

export function parseDrinkCategory(value: unknown): DrinkCategory | null {
  if (!isRecord(value) || !isNumber(value.id) || !isString(value.name) || !isNumber(value.sortOrder) || typeof value.active !== "boolean") return null;
  return { id: value.id, name: value.name, sortOrder: value.sortOrder, active: value.active };
}

export const parseDrinkCategories = (value: unknown): DrinkCategory[] | null => parseArray(value, parseDrinkCategory);

export function parseDrink(value: unknown): Drink | null {
  if (!isRecord(value) || !isNumber(value.id) || !isNumber(value.categoryId) || !isString(value.categoryName)
    || !isString(value.name) || !isNullableString(value.description) || !isNullableString(value.imageUrl) || typeof value.active !== "boolean") return null;
  return { id: value.id, categoryId: value.categoryId, categoryName: value.categoryName, name: value.name, description: value.description, imageUrl: value.imageUrl, active: value.active };
}

export const parseDrinks = (value: unknown): Drink[] | null => parseArray(value, parseDrink);

export function parseDrinkVariant(value: unknown): DrinkVariant | null {
  if (!isRecord(value) || !isNumber(value.id) || !isNumber(value.drinkId) || !isString(value.drinkName)
    || !isString(value.displayVolumeName) || !isNumber(value.volumeMl) || !isString(value.price)
    || typeof value.useStandardPrice !== "boolean" || !isNullableString(value.sku) || typeof value.active !== "boolean") return null;
  return { id: value.id, drinkId: value.drinkId, drinkName: value.drinkName, displayVolumeName: value.displayVolumeName, volumeMl: value.volumeMl, price: value.price, useStandardPrice: value.useStandardPrice, sku: value.sku, active: value.active };
}

export const parseDrinkVariants = (value: unknown): DrinkVariant[] | null => parseArray(value, parseDrinkVariant);

export function parseInventoryItem(value: unknown): InventoryItem | null {
  if (!isRecord(value) || !isNumber(value.id) || !isString(value.name) || !isNullableNumber(value.linkedDrinkId)
    || !isNullableNumber(value.linkedDrinkVariantId) || !isString(value.packageType) || !isString(value.packagesInStock)
    || !isString(value.contentPerPackage) || !isString(value.contentUnit) || !isString(value.totalStockAmount)
    || !isString(value.reorderThreshold) || !isString(value.minimumStock) || !isString(value.recommendedReorderAmount)
    || !isNullableString(value.supplier) || typeof value.active !== "boolean") return null;
  return {
    id: value.id, name: value.name, linkedDrinkId: value.linkedDrinkId, linkedDrinkVariantId: value.linkedDrinkVariantId,
    packageType: value.packageType, packagesInStock: value.packagesInStock, contentPerPackage: value.contentPerPackage,
    contentUnit: value.contentUnit, totalStockAmount: value.totalStockAmount, reorderThreshold: value.reorderThreshold,
    minimumStock: value.minimumStock, recommendedReorderAmount: value.recommendedReorderAmount,
    supplier: value.supplier, active: value.active,
  };
}

export const parseInventoryItems = (value: unknown): InventoryItem[] | null => parseArray(value, parseInventoryItem);

export function parseSplitPayment(value: unknown): SplitPaymentResponse | null {
  if (!isRecord(value)) return null;
  const openOrder = parseTableOrder(value.openOrder);
  const paidOrder = parseTableOrder(value.paidOrder);
  return openOrder && paidOrder ? { openOrder, paidOrder } : null;
}

export function selectSellableCatalog(allVariants: DrinkVariant[], allItems: InventoryItem[], allDrinks: Drink[], allCategories: DrinkCategory[]) {
  const stockedItems = allItems.filter((item) => item.active && Number(item.totalStockAmount) > 0);
  const variantIds = new Set<number>();
  const drinkLinkCounts = new Map<number, number>();
  for (const item of stockedItems) {
    if (item.linkedDrinkVariantId !== null) variantIds.add(item.linkedDrinkVariantId);
    if (item.linkedDrinkId !== null) drinkLinkCounts.set(item.linkedDrinkId, (drinkLinkCounts.get(item.linkedDrinkId) ?? 0) + 1);
  }
  const fallbackDrinkIds = new Set([...drinkLinkCounts].filter(([, count]) => count === 1).map(([id]) => id));
  const variants = allVariants.filter((variant) => variant.active && (variantIds.has(variant.id) || fallbackDrinkIds.has(variant.drinkId)));
  const drinkIds = new Set(variants.map((variant) => variant.drinkId));
  const drinks = allDrinks.filter((drink) => drink.active && drinkIds.has(drink.id));
  const categoryIds = new Set(drinks.map((drink) => drink.categoryId));
  return { variants, drinks, categories: allCategories.filter((category) => category.active && categoryIds.has(category.id)) };
}

export function readCachedOrders(raw: string | null): TableOrder[] {
  if (!raw) return [];
  try {
    return parseTableOrders(JSON.parse(raw)) ?? [];
  } catch {
    return [];
  }
}

export function todayIsoDate(): string { return new Date().toISOString().slice(0, 10); }
export function toGermanDateLabel(value: string): string {
  if (!value) return "";
  const parsed = new Date(`${value}T00:00:00`);
  return Number.isNaN(parsed.getTime()) ? value : new Intl.DateTimeFormat("de-DE", { dateStyle: "medium" }).format(parsed);
}
export function toCurrency(value: string): string {
  const amount = Number(value);
  return Number.isNaN(amount) ? `${value} EUR` : new Intl.NumberFormat("de-DE", { style: "currency", currency: "EUR" }).format(amount);
}
export function matchesBusinessDate(closedAt: string | null, businessDate: string): boolean { return Boolean(closedAt && businessDate && closedAt.slice(0, 10) === businessDate); }
export function sortByClosedAtDesc(entries: TableOrder[]): TableOrder[] {
  return [...entries].sort((a, b) => (b.closedAt ? Date.parse(b.closedAt) : 0) - (a.closedAt ? Date.parse(a.closedAt) : 0));
}
export function tableStatusVariant(status: Table["status"]): "success" | "warning" | "destructive" | "muted" {
  if (status === "FREE") return "success";
  if (status === "READY_FOR_PAYMENT") return "warning";
  if (status === "RESERVED") return "muted";
  return "destructive";
}
