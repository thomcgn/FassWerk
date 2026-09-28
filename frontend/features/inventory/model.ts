import { parseArray, isRecord } from "../../lib/api-client.ts";
import { parseDrink, parseDrinkVariant, parseInventoryItem } from "../billing/model.ts";
import type { Drink, DrinkVariant, InventoryItem, InventoryItemUpsertRequest, InventoryPackageDefaults } from "../../types/api.ts";

export type PackageType = InventoryItemUpsertRequest["packageType"];
export type ContentUnit = InventoryItemUpsertRequest["contentUnit"];

export const contentDefaultsByType: Record<PackageType, { contentUnit: ContentUnit; contentPerPackage: string }> = {
  BARREL: { contentUnit: "LITER", contentPerPackage: "50" },
  CRATE: { contentUnit: "LITER", contentPerPackage: "4.5" },
  BOTTLE: { contentUnit: "LITER", contentPerPackage: "0.5" },
  SINGLE_BOTTLE: { contentUnit: "LITER", contentPerPackage: "0.3" },
  BOX: { contentUnit: "PIECE", contentPerPackage: "1" },
};

export const isPackageType = (value: string): value is PackageType => ["BARREL", "CRATE", "BOTTLE", "BOX", "SINGLE_BOTTLE"].includes(value);
export const isContentUnit = (value: string): value is ContentUnit => ["MILLILITER", "LITER", "PIECE"].includes(value);
export const parseInventoryItems = (value: unknown): InventoryItem[] | null => parseArray(value, parseInventoryItem);
export const parseDrinks = (value: unknown): Drink[] | null => parseArray(value, parseDrink);
export const parseDrinkVariants = (value: unknown): DrinkVariant[] | null => parseArray(value, parseDrinkVariant);

function parsePackageDefaults(value: unknown): InventoryPackageDefaults | null {
  if (!isRecord(value) || typeof value.packageType !== "string" || !isPackageType(value.packageType)
    || typeof value.reorderThresholdPackages !== "number" || typeof value.minimumStockPackages !== "number"
    || typeof value.recommendedReorderPackages !== "number") return null;
  return { packageType: value.packageType, reorderThresholdPackages: value.reorderThresholdPackages, minimumStockPackages: value.minimumStockPackages, recommendedReorderPackages: value.recommendedReorderPackages };
}
export const parseInventoryPackageDefaults = (value: unknown): InventoryPackageDefaults[] | null => parseArray(value, parsePackageDefaults);

export function parseLocaleNumber(value: string): number { return Number(value.replace(",", ".")); }
export function calculateCrateContentPerPackage(unit: ContentUnit | string, bottlesPerCrate: string, litersPerBottle: string): string {
  const bottles = parseLocaleNumber(bottlesPerCrate);
  const liters = parseLocaleNumber(litersPerBottle);
  if (!Number.isFinite(bottles) || bottles <= 0) return "0";
  if (unit === "PIECE") return String(bottles);
  if (!Number.isFinite(liters) || liters <= 0) return "0";
  return String(unit === "MILLILITER" ? bottles * liters * 1000 : bottles * liters);
}
export function packageTypeLabel(value: PackageType | string): string {
  const labels: Record<string, string> = { BARREL: "Fass", CRATE: "Kasten", BOTTLE: "Flasche", SINGLE_BOTTLE: "Einzelflasche", BOX: "Karton / Box" };
  return labels[value] ?? value;
}
export function packageAmountLabel(value: PackageType | string): string {
  const labels: Record<string, string> = { BARREL: "Faesser", CRATE: "Kaesten", BOTTLE: "Flaschen", SINGLE_BOTTLE: "Einzelflaschen", BOX: "Boxen" };
  return labels[value] ?? "Gebinde";
}
export function packageAmountLabelLower(value: PackageType | string): string { const label = packageAmountLabel(value); return label.charAt(0).toLowerCase() + label.slice(1); }
export function unitLabel(value: ContentUnit | string): string {
  if (value === "LITER") return "Liter (l)";
  if (value === "PIECE") return "Stück";
  if (value === "MILLILITER") return "Milliliter (ml)";
  return value;
}
export const shortUnit = (value: string): string => value === "LITER" ? "l" : value === "PIECE" ? "Stk" : value === "MILLILITER" ? "ml" : value;
const formatAmount = (value: number): string => Number.isFinite(value) ? new Intl.NumberFormat("de-DE", { maximumFractionDigits: 2 }).format(value) : "-";
export function getInventoryStatus(item: InventoryItem): { label: string; variant: "success" | "destructive" | "muted" } {
  if (!item.active) return { label: "Inaktiv", variant: "muted" };
  const stock = Number(item.totalStockAmount);
  const threshold = Number(item.reorderThreshold);
  return Number.isNaN(stock) || Number.isNaN(threshold) || stock <= threshold ? { label: "Nachbestellen", variant: "destructive" } : { label: "Stabil", variant: "success" };
}
export function formatStockForArticle(item: InventoryItem, variants: DrinkVariant[]): { primary: string; secondary: string } {
  const total = Number(item.totalStockAmount);
  const perPackage = Number(item.contentPerPackage);
  const packages = Number.isFinite(total) && Number.isFinite(perPackage) && perPackage > 0 ? total / perPackage : NaN;
  const base = Number.isFinite(total) ? `${formatAmount(total)} ${shortUnit(item.contentUnit)}` : `${item.totalStockAmount} ${shortUnit(item.contentUnit)}`;
  const packageValue = Number.isFinite(packages) ? `${formatAmount(packages)} ${packageAmountLabel(item.packageType)}` : "-";
  if (["CRATE", "BOTTLE", "SINGLE_BOTTLE"].includes(item.packageType)) {
    const variant = item.linkedDrinkVariantId === null ? undefined : variants.find((entry) => entry.id === item.linkedDrinkVariantId);
    const totalMl = item.contentUnit === "MILLILITER" ? total : item.contentUnit === "LITER" ? total * 1000 : null;
    if (variant && variant.volumeMl > 0 && totalMl !== null && Number.isFinite(totalMl)) return { primary: `${formatAmount(totalMl / variant.volumeMl)} Flaschen`, secondary: base };
    if (Number.isFinite(packages)) return { primary: packageValue, secondary: base };
  }
  if (item.packageType === "BARREL" && Number.isFinite(packages)) return { primary: packageValue, secondary: base };
  if (item.packageType === "BOX") {
    if (item.contentUnit === "PIECE" && Number.isFinite(total)) return { primary: `${formatAmount(total)} Stk`, secondary: packageValue };
    if (Number.isFinite(packages)) return { primary: packageValue, secondary: base };
  }
  return { primary: base, secondary: packageValue };
}
export function formatThresholdPackages(item: InventoryItem): string {
  const threshold = Number(item.reorderThreshold);
  const perPackage = Number(item.contentPerPackage);
  return Number.isFinite(threshold) && Number.isFinite(perPackage) && perPackage > 0 ? formatAmount(threshold / perPackage) : "-";
}
export function parseOptionalId(value: string): number | null {
  const parsed = Number(value.trim());
  return value.trim() && Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}
