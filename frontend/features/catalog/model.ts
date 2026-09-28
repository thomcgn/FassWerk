import { isRecord, parseArray } from "../../lib/api-client.ts";
import type { VolumePrice } from "../../types/api.ts";

export type BatchDrinkInput = { id: string; name: string; description: string };
export type BatchVariantInput = { id: string; label: string; price: string };
export type PreparedBatch = { categoryId: number; drinks: BatchDrinkInput[]; variants: Array<{ id: string; label: string; price: number; volumeMl: number }> };
export const volumeByLabel: Record<string, number> = { "0,2 l": 200, "0,3 l": 300, "0,33 l": 330, "0,4 l": 400, "0,5 l": 500, "0,75 l": 750, "2 cl": 20, "4 cl": 40 };
export function prepareDrinkBatch(categoryValue: string, drinkRows: BatchDrinkInput[], variantRows: BatchVariantInput[]): { batch: PreparedBatch | null; error: string | null } {
  const categoryId = Number(categoryValue);
  if (!Number.isInteger(categoryId) || categoryId <= 0) return { batch: null, error: "Bitte eine Kategorie auswählen." };
  const drinks = drinkRows.map((row) => ({ ...row, name: row.name.trim(), description: row.description.trim() })).filter((row) => row.name.length > 0);
  if (drinks.length === 0) return { batch: null, error: "Bitte mindestens ein Getränk eintragen." };
  const variants = variantRows.map((row) => ({ ...row, label: row.label.trim(), price: Number(row.price), volumeMl: volumeByLabel[row.label.trim()] ?? 0 })).filter((row) => row.label.length > 0);
  if (variants.length === 0) return { batch: null, error: "Bitte mindestens eine Variante mit Label und Preis angeben." };
  const invalidPriceIndex = variants.findIndex((row) => !Number.isFinite(row.price) || row.price < 0);
  if (invalidPriceIndex >= 0) return { batch: null, error: `Bitte einen gültigen Preis in Varianten-Zeile ${invalidPriceIndex + 1} eingeben.` };
  const unknownVolumeIndex = variants.findIndex((row) => row.volumeMl <= 0);
  if (unknownVolumeIndex >= 0) return { batch: null, error: `Unbekanntes Label '${variants[unknownVolumeIndex].label}' in Varianten-Zeile ${unknownVolumeIndex + 1}. Bitte ein Standard-Label wählen.` };
  const hasDuplicates = (values: string[]) => new Set(values.map((value) => value.toLocaleLowerCase("de-DE"))).size !== values.length;
  if (hasDuplicates(drinks.map((row) => row.name))) return { batch: null, error: "Bitte doppelte Getränkenamen in der Liste entfernen." };
  if (hasDuplicates(variants.map((row) => row.label))) return { batch: null, error: "Bitte doppelte Varianten-Labels in der Liste entfernen." };
  return { batch: { categoryId, drinks, variants }, error: null };
}
export function calculateAdjustedPrice(currentPrice: number | string, adjustment: string, mode: "ABSOLUTE" | "PERCENT"): number | null {
  const current = Number(currentPrice);
  const value = Number(adjustment);
  if (!Number.isFinite(current) || !Number.isFinite(value)) return null;
  const next = mode === "ABSOLUTE" ? current + value : current * (1 + value / 100);
  return Math.max(0, Number(next.toFixed(2)));
}

function parseVolumePrice(value: unknown): VolumePrice | null {
  if (!isRecord(value) || typeof value.id !== "number" || typeof value.volumeMl !== "number" || typeof value.price !== "number") return null;
  return { id: value.id, volumeMl: value.volumeMl, price: value.price };
}
export const parseVolumePrices = (value: unknown): VolumePrice[] | null => parseArray(value, parseVolumePrice);
