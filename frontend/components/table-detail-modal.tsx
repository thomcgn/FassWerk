"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Beer, Trash2, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { Drink, DrinkCategory, DrinkVariant, SplitPaymentItemRequest, TableOrder } from "@/types/api";

function toCurrency(value: string): string {
  const amount = Number(value);
  if (Number.isNaN(amount)) return `${value} EUR`;
  return new Intl.NumberFormat("de-DE", { style: "currency", currency: "EUR" }).format(amount);
}

interface TableDetailModalProps {
  isOpen: boolean;
  order: TableOrder | null;
  categories: DrinkCategory[];
  drinks: Drink[];
  variants: DrinkVariant[];
  onClose: () => void;
  onAddItem: (drinkVariantId: number, quantity: number) => Promise<void>;
  onRemoveItem: (itemId: number) => Promise<void>;
  onCloseOrder: () => Promise<void>;
  onSplitPayment: (items: SplitPaymentItemRequest[]) => Promise<void>;
  error: string | null;
  status: string | null;
}

export function TableDetailModal({
  isOpen,
  order,
  categories,
  drinks,
  variants,
  onClose,
  onAddItem,
  onRemoveItem,
  onCloseOrder,
  onSplitPayment,
  error,
  status,
}: TableDetailModalProps) {
  const [selectedCategoryId, setSelectedCategoryId] = useState("");
  const [isAddingVariantId, setIsAddingVariantId] = useState<number | null>(null);
  const [splitQuantities, setSplitQuantities] = useState<Record<number, string>>({});

  const canEditOrder = useMemo(() => order?.status === "OPEN", [order]);

  const availableCategoryIds = useMemo(() => {
    const drinkIds = new Set(variants.map((variant) => variant.drinkId));
    return new Set(drinks.filter((drink) => drinkIds.has(drink.id)).map((drink) => drink.categoryId));
  }, [drinks, variants]);

  const selectableCategories = useMemo(
    () => categories.filter((category) => availableCategoryIds.has(category.id)),
    [categories, availableCategoryIds],
  );

  const categoryVariants = useMemo(() => {
    if (!selectedCategoryId) return [];
    const categoryId = Number(selectedCategoryId);
    const drinkIdsInCategory = new Set(
      drinks.filter((drink) => drink.categoryId === categoryId).map((drink) => drink.id),
    );
    return variants
      .filter((variant) => drinkIdsInCategory.has(variant.drinkId))
      .sort((a, b) => {
        if (a.drinkName !== b.drinkName) return a.drinkName.localeCompare(b.drinkName, "de-DE");
        return a.volumeMl - b.volumeMl;
      });
  }, [selectedCategoryId, drinks, variants]);

  useEffect(() => {
    if (!isOpen) return;
    const timer = window.setTimeout(() => {
      if (selectableCategories.length === 0) {
        setSelectedCategoryId("");
        return;
      }
      if (!selectedCategoryId || !selectableCategories.some((category) => String(category.id) === selectedCategoryId)) {
        setSelectedCategoryId(String(selectableCategories[0].id));
      }
    }, 0);
    return () => window.clearTimeout(timer);
  }, [isOpen, selectableCategories, selectedCategoryId]);

  const handleTapAddItem = useCallback(async (variantId: number) => {
    if (!canEditOrder || isAddingVariantId !== null) return;
    setIsAddingVariantId(variantId);
    try {
      await onAddItem(variantId, 1);
    } finally {
      setIsAddingVariantId(null);
    }
  }, [canEditOrder, isAddingVariantId, onAddItem]);

  const handleClose = useCallback(() => {
    setSelectedCategoryId("");
    setIsAddingVariantId(null);
    setSplitQuantities({});
    onClose();
  }, [onClose]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      if (!order) {
        setSplitQuantities({});
        return;
      }
      setSplitQuantities((current) => {
        const next: Record<number, string> = {};
        for (const item of order.items) {
          next[item.id] = current[item.id] ?? "1";
        }
        return next;
      });
    }, 0);
    return () => window.clearTimeout(timer);
  }, [order]);

  const handleSplitItem = useCallback(async (itemId: number, maxQuantity: number) => {
    const parsed = Number(splitQuantities[itemId] ?? "0");
    if (!Number.isFinite(parsed) || parsed < 1 || parsed > maxQuantity) {
      return;
    }
    await onSplitPayment([{ itemId, quantity: parsed }]);
    setSplitQuantities((current) => ({ ...current, [itemId]: "1" }));
  }, [onSplitPayment, splitQuantities]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-2 md:p-4">
      <Card className="w-full max-w-4xl max-h-[92vh] overflow-y-auto">
        <CardHeader className="sticky top-0 flex flex-row items-center justify-between bg-[color:var(--color-surface)] border-b">
          <div>
            <CardTitle>Tischdetail</CardTitle>
            {order ? <p className="text-sm text-[color:var(--color-muted-foreground)]">Tisch {order.tableName} · Bon #{order.id}</p> : null}
          </div>
          <button onClick={handleClose} className="rounded-lg p-2 hover:bg-white/10 transition-colors">
            <X className="h-5 w-5" />
          </button>
        </CardHeader>

        <CardContent className="space-y-5 p-6">
          {order ? (
            <>
              <div className="space-y-4">
                <div>
                  <Label className="mb-2 block">Kategorie wählen</Label>
                  <div className="grid gap-2 sm:grid-cols-3">
                    {selectableCategories.map((category) => (
                      <button
                        key={category.id}
                        type="button"
                        disabled={!canEditOrder}
                        onClick={() => setSelectedCategoryId(String(category.id))}
                        className={`min-h-12 rounded-lg border px-3 py-3 text-sm font-semibold transition-all ${selectedCategoryId === String(category.id)
                          ? "border-cyan-500/60 bg-cyan-500/20 text-cyan-100"
                          : "border-white/10 bg-white/5 text-[color:var(--color-muted-foreground)] hover:text-[color:var(--color-foreground)] hover:border-cyan-500/40"}`}
                      >
                        {category.name}
                      </button>
                    ))}
                  </div>
                </div>

                <div>
                  <Label className="mb-2 block">Varianten antippen (jeder Tap fügt 1x hinzu)</Label>
                  <div className="grid gap-2 sm:grid-cols-3">
                    {categoryVariants.map((variant) => (
                      <button
                        key={variant.id}
                        type="button"
                        disabled={!canEditOrder || isAddingVariantId !== null}
                        onClick={() => void handleTapAddItem(variant.id)}
                        className={`min-h-14 rounded-lg border px-3 py-3 text-sm font-semibold transition-all ${isAddingVariantId === variant.id
                          ? "border-cyan-500/60 bg-cyan-500/20 text-cyan-100"
                          : "border-white/10 bg-white/5 text-[color:var(--color-muted-foreground)] hover:text-[color:var(--color-foreground)] hover:border-cyan-500/40"}`}
                      >
                        <span className="block">{variant.drinkName} · {variant.displayVolumeName}</span>
                        <span className="mt-1 block text-xs text-cyan-300">{toCurrency(variant.price)}</span>
                      </button>
                    ))}
                  </div>
                </div>
              </div>

              <div className="space-y-3">
                {order.items.length === 0 ? (
                  <div className="rounded-[24px] border border-dashed border-white/15 bg-white/5 p-4 text-sm text-[color:var(--color-muted-foreground)]">Noch keine Positionen auf dem Bon.</div>
                ) : (
                  order.items.map((item) => (
                    <div key={item.id} className="rounded-[24px] border border-white/10 bg-white/5 p-4 shadow-sm">
                      <div className="flex flex-wrap items-center justify-between gap-3">
                        <p className="inline-flex items-center gap-2 text-sm font-semibold text-[color:var(--color-foreground)]"><Beer className="h-4 w-4 text-[color:var(--color-muted-foreground)]" />{item.drinkLabel}</p>
                        <p className="text-sm font-semibold text-[color:var(--color-foreground)]">{toCurrency(item.totalPrice)}</p>
                      </div>
                      <p className="mt-2 text-sm text-[color:var(--color-muted-foreground)]">{item.quantity} × {toCurrency(item.unitPrice)}</p>
                      <div className="mt-3 flex flex-wrap items-end gap-2">
                        <div className="w-32">
                          <Label className="text-xs">Teilmenge</Label>
                          <Input
                            className="h-11"
                            type="number"
                            min={1}
                            max={item.quantity}
                            value={splitQuantities[item.id] ?? "1"}
                            onChange={(event) => setSplitQuantities((current) => ({ ...current, [item.id]: event.target.value }))}
                            disabled={!canEditOrder}
                          />
                        </div>
                        <Button
                          variant="secondary"
                          className="h-11"
                          disabled={!canEditOrder}
                          onClick={() => void handleSplitItem(item.id, item.quantity)}
                        >
                          Teilzahlung
                        </Button>
                        <Button className="ml-auto h-11" variant="outline" disabled={!canEditOrder} onClick={() => void onRemoveItem(item.id)}>
                          <Trash2 className="h-4 w-4" />Entfernen
                        </Button>
                      </div>
                    </div>
                  ))
                )}
              </div>

              <div className="flex flex-col gap-3 rounded-[24px] bg-white/5 p-4 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <p className="text-sm text-[color:var(--color-muted-foreground)]">Gesamt</p>
                  <p className="text-2xl font-semibold text-[color:var(--color-foreground)]">{toCurrency(order.total)}</p>
                </div>
                <Button className="h-12" onClick={() => void onCloseOrder()} disabled={!canEditOrder}>Bon schließen</Button>
              </div>

              {status ? <p className="rounded-2xl bg-green-500/15 border border-green-500/30 px-4 py-3 text-sm text-green-300">{status}</p> : null}
              {error ? <p className="rounded-2xl bg-red-500/15 border border-red-500/30 px-4 py-3 text-sm text-red-300">{error}</p> : null}
            </>
          ) : (
            <div className="rounded-[24px] border border-dashed border-white/15 bg-white/5 p-4 text-sm text-[color:var(--color-muted-foreground)]">
              Tippe auf einen Tisch, um den Bon zu laden.
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

