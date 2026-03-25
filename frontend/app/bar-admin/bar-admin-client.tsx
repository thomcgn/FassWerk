"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Beer, PlusCircle, Edit2, Trash2, X, AlertCircle } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select } from "@/components/ui/select";
import { useToastFeedback } from "@/lib/use-toast-feedback";
import type { Drink, DrinkCategory, DrinkVariant, VolumePrice, InventoryItem } from "@/types/api";

type ApiError = { message?: string; error?: string };

async function readError(response: Response, fallback: string) {
  const payload = (await response.clone().json().catch(() => null)) as ApiError | null;
  if (payload?.message || payload?.error) {
    return payload.message || payload.error || fallback;
  }

  const textBody = await response.clone().text().catch(() => "");
  if (textBody.trim().length > 0) {
    return textBody;
  }

  if (response.status === 401) return "Bitte neu einloggen.";
  if (response.status === 403) return "Keine Berechtigung für diese Aktion.";
  if (response.status === 409) return "Ein Eintrag mit diesem Namen existiert bereits.";
  if (response.status === 422 || response.status === 400) return "Bitte Eingaben prüfen.";
  return fallback;
}

type EditMode = null
  | { type: "drink"; id: number; name: string; categoryId: string; description: string }
  | { type: "variant"; id: number; drinkId: number; label: string; volumeMl: string; useStandardPrice: boolean; price: string; sku: string };

type BatchDrinkRow = {
  id: string;
  name: string;
  description: string;
};

type BatchVariantRow = {
  id: string;
  label: string;
  price: string;
};

type BatchVariantTemplate = {
  id: string;
  name: string;
  rows: Array<Pick<BatchVariantRow, "label" | "price">>;
};

const COMMON_VOLUME_PRESETS = [
  { volumeMl: 200, label: "0,2 l", defaultPrice: "3.90" },
  { volumeMl: 330, label: "0,33 l", defaultPrice: "4.50" },
  { volumeMl: 500, label: "0,5 l", defaultPrice: "5.90" },
] as const;

function getLabelToVolumeMlMap(): Record<string, number> {
  const map: Record<string, number> = {};
  for (const preset of COMMON_VOLUME_PRESETS) {
    map[preset.label] = preset.volumeMl;
  }
  // Zusätzliche Labels
  map["0,3 l"] = 300;
  map["0,4 l"] = 400;
  map["0,75 l"] = 750;
  map["2 cl"] = 20;
  map["4 cl"] = 40;
  return map;
}

const BATCH_VARIANT_TEMPLATES: BatchVariantTemplate[] = [
  {
    id: "beer",
    name: "Bier",
    rows: [
      { label: "0,3 l", price: "5.50" },
      { label: "0,5 l", price: "6.90" },
    ],
  },
  {
    id: "wine",
    name: "Wein",
    rows: [
      { label: "0,2 l", price: "4.50" },
      { label: "0,75 l", price: "12.90" },
    ],
  },
  {
    id: "shots",
    name: "Shots",
    rows: [
      { label: "2 cl", price: "2.50" },
      { label: "4 cl", price: "4.50" },
    ],
  },
];

function createClientId(prefix: string): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return `${prefix}-${crypto.randomUUID()}`;
  }
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
}

export default function BarAdminClient() {
  const [categories, setCategories] = useState<DrinkCategory[]>([]);
  const [drinks, setDrinks] = useState<Drink[]>([]);
  const [variants, setVariants] = useState<DrinkVariant[]>([]);
  const [volumePrices, setVolumePrices] = useState<VolumePrice[]>([]);
  const [inventoryItems, setInventoryItems] = useState<InventoryItem[]>([]);
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editMode, setEditMode] = useState<EditMode>(null);
  const [deleteCategoryModal, setDeleteCategoryModal] = useState<{ id: number; name: string } | null>(null);
  const [deletingCategory, setDeletingCategory] = useState(false);
  const [deleteDrinkModal, setDeleteDrinkModal] = useState<{ id: number; name: string } | null>(null);
  const [deletingDrink, setDeletingDrink] = useState(false);

  useToastFeedback(error, "error");
  useToastFeedback(status, "success");

  const [categoryName, setCategoryName] = useState("");
  const [categorySortOrder, setCategorySortOrder] = useState("30");

  const [drinkCategoryId, setDrinkCategoryId] = useState("");
  const [batchVariantRows, setBatchVariantRows] = useState<BatchVariantRow[]>([
    { id: "variant-1", label: "0,2 l", price: "3.90" },
    { id: "variant-2", label: "0,33 l", price: "4.50" },
    { id: "variant-3", label: "0,5 l", price: "5.90" },
  ]);
  const [selectedVariantTemplateId, setSelectedVariantTemplateId] = useState<string | null>(null);
  const [batchRows, setBatchRows] = useState<BatchDrinkRow[]>([
    { id: "row-1", name: "", description: "" },
    { id: "row-2", name: "", description: "" },
    { id: "row-3", name: "", description: "" },
  ]);
  const [isCreatingBatch, setIsCreatingBatch] = useState(false);

  const [variantDrinkId, setVariantDrinkId] = useState("");
  const [variantLabel, setVariantLabel] = useState("");
  const [variantVolumeMl, setVariantVolumeMl] = useState("500");
  const [variantSku, setVariantSku] = useState("");
  const [bulkPriceMode, setBulkPriceMode] = useState<"ABSOLUTE" | "PERCENT">("ABSOLUTE");
  const [bulkPriceValue, setBulkPriceValue] = useState("0.50");
  const [selectedVolumeMlForAdjust, setSelectedVolumeMlForAdjust] = useState("");
  const [applyingSelectedPriceAdjust, setApplyingSelectedPriceAdjust] = useState(false);

  const loadAll = useCallback(async () => {
    const [categoriesRes, drinksRes, variantsRes, volumePricesRes, inventoryRes] = await Promise.all([
      fetch("/api/drink-categories", { cache: "no-store" }),
      fetch("/api/drinks", { cache: "no-store" }),
      fetch("/api/drink-variants", { cache: "no-store" }),
      fetch("/api/volume-prices", { cache: "no-store" }),
      fetch("/api/inventory", { cache: "no-store" }),
    ]);

    if (!categoriesRes.ok || !drinksRes.ok || !variantsRes.ok || !volumePricesRes.ok || !inventoryRes.ok) {
      setError("Drinks konnten nicht geladen werden.");
      return;
    }

    const [categoriesPayload, drinksPayload, variantsPayload, volumePricesPayload, inventoryPayload] = (await Promise.all([
      categoriesRes.json(),
      drinksRes.json(),
      variantsRes.json(),
      volumePricesRes.json(),
      inventoryRes.json(),
    ])) as [DrinkCategory[], Drink[], DrinkVariant[], VolumePrice[], InventoryItem[]];

    setCategories(categoriesPayload);
    setDrinks(drinksPayload);
    setVariants(variantsPayload);
    setVolumePrices(volumePricesPayload.sort((a, b) => a.volumeMl - b.volumeMl));
    setInventoryItems(inventoryPayload);

    setDrinkCategoryId((current) => current || String(categoriesPayload[0]?.id ?? ""));
    setVariantDrinkId((current) => current || String(drinksPayload[0]?.id ?? ""));
    setSelectedVolumeMlForAdjust((current) => current || String(volumePricesPayload[0]?.volumeMl ?? ""));
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadAll();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [loadAll]);

  async function createCategory() {
    setError(null);
    setStatus(null);

    const trimmedName = categoryName.trim();
    if (!trimmedName) {
      setError("Bitte einen Kategorienamen eingeben.");
      return;
    }

    const parsedSortOrder = Number(categorySortOrder.trim().replace(",", "."));
    if (!Number.isInteger(parsedSortOrder) || parsedSortOrder < 0) {
      setError("Sortierung muss eine ganze Zahl >= 0 sein.");
      return;
    }

    const response = await fetch("/api/drink-categories", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: trimmedName,
        sortOrder: parsedSortOrder,
        active: true,
      }),
    });

    if (!response.ok) {
      setError(await readError(response, "Kategorie konnte nicht angelegt werden."));
      return;
    }

    setCategoryName("");
    setStatus("Kategorie angelegt.");
    await loadAll();
  }

  function addBatchRow() {
    setBatchRows((current) => [
      ...current,
      { id: `row-${Date.now()}-${current.length + 1}`, name: "", description: "" },
    ]);
  }

  function updateBatchRow(id: string, patch: Partial<BatchDrinkRow>) {
    setBatchRows((current) => current.map((row) => (row.id === id ? { ...row, ...patch } : row)));
  }

  function removeBatchRow(id: string) {
    setBatchRows((current) => {
      if (current.length <= 1) {
        return current;
      }
      return current.filter((row) => row.id !== id);
    });
  }

  function addBatchVariantRow() {
    setSelectedVariantTemplateId(null);
    setBatchVariantRows((current) => [
      ...current,
      { id: createClientId("variant"), label: "", price: "" },
    ]);
  }

  function updateBatchVariantRow(id: string, patch: Partial<BatchVariantRow>) {
    setSelectedVariantTemplateId(null);
    setBatchVariantRows((current) => current.map((row) => (row.id === id ? { ...row, ...patch } : row)));
  }

  function removeBatchVariantRow(id: string) {
    setSelectedVariantTemplateId(null);
    setBatchVariantRows((current) => {
      if (current.length <= 1) {
        return current;
      }
      return current.filter((row) => row.id !== id);
    });
  }

  function applyBatchVariantTemplate(templateId: string) {
    const template = BATCH_VARIANT_TEMPLATES.find((entry) => entry.id === templateId);
    if (!template) return;

    setBatchVariantRows(
      template.rows.map((row) => ({
        id: createClientId("variant"),
        label: row.label,
        price: row.price,
      })),
    );
    setSelectedVariantTemplateId(templateId);
  }

  async function createDrinkBatch() {
    setError(null);
    setStatus(null);

    const categoryId = Number(drinkCategoryId);

    if (!Number.isInteger(categoryId) || categoryId <= 0) {
      setError("Bitte eine Kategorie auswählen.");
      return;
    }

    const rowsToCreate = batchRows
      .map((row) => ({ ...row, name: row.name.trim(), description: row.description.trim() }))
      .filter((row) => row.name.length > 0);

    const variantsToCreate = batchVariantRows
      .map((variant) => ({
        ...variant,
        label: variant.label.trim(),
        price: Number(variant.price),
      }))
      .filter((variant) => variant.label.length > 0 && Number.isFinite(variant.price));

    if (rowsToCreate.length === 0) {
      setError("Bitte mindestens ein Getränk eintragen.");
      return;
    }

    if (variantsToCreate.length === 0) {
      setError("Bitte mindestens eine Variante mit Label und Preis angeben.");
      return;
    }

    for (let index = 0; index < variantsToCreate.length; index += 1) {
      const variant = variantsToCreate[index];
      if (!variant.label) {
        setError(`Bitte ein Varianten-Label in Zeile ${index + 1} angeben.`);
        return;
      }
      if (!Number.isFinite(variant.price) || variant.price < 0) {
        setError(`Bitte einen gültigen Preis in Varianten-Zeile ${index + 1} eingeben.`);
        return;
      }
    }

    const duplicateNames = rowsToCreate
      .map((row) => row.name.toLocaleLowerCase("de-DE"))
      .filter((name, index, all) => all.indexOf(name) !== index);
    if (duplicateNames.length > 0) {
      setError("Bitte doppelte Getränkenamen in der Liste entfernen.");
      return;
    }

    const duplicateVariantLabels = variantsToCreate
      .map((variant) => variant.label.toLocaleLowerCase("de-DE"))
      .filter((label, index, all) => all.indexOf(label) !== index);
    if (duplicateVariantLabels.length > 0) {
      setError("Bitte doppelte Varianten-Labels in der Liste entfernen.");
      return;
    }

    setIsCreatingBatch(true);
    let createdCount = 0;
    let createdVariantCount = 0;

    for (let index = 0; index < rowsToCreate.length; index += 1) {
      const row = rowsToCreate[index];
      const drinkResponse = await fetch("/api/drinks", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          categoryId,
          name: row.name,
          description: row.description || null,
          imageUrl: null,
          active: true,
        }),
      });

      if (!drinkResponse.ok) {
        setIsCreatingBatch(false);
        const reason = await readError(drinkResponse, "Getraenk konnte nicht angelegt werden.");
        setError(`Fehler in Zeile ${index + 1} (${row.name}): ${reason}`);
        await loadAll();
        return;
      }

      const createdDrink = (await drinkResponse.json().catch(() => null)) as Drink | null;
      if (!createdDrink?.id) {
        setIsCreatingBatch(false);
        setError(`Getraenk '${row.name}' wurde angelegt, konnte aber nicht weiterverarbeitet werden.`);
        await loadAll();
        return;
      }

      for (let variantIndex = 0; variantIndex < variantsToCreate.length; variantIndex += 1) {
        const variant = variantsToCreate[variantIndex];
        const labelToVolumeMl = getLabelToVolumeMlMap();
        const volumeMl = labelToVolumeMl[variant.label];
        if (!volumeMl) {
          setIsCreatingBatch(false);
          setError(`Unbekanntes Label '${variant.label}' in Varianten-Zeile ${variantIndex + 1}. Bitte ein Standard-Label wählen.`);
          await loadAll();
          return;
        }

        const variantResponse = await fetch("/api/drink-variants", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            drinkId: createdDrink.id,
            displayVolumeName: variant.label,
            volumeMl,
            useStandardPrice: false,
            price: Number(variant.price).toFixed(2),
            sku: null,
            active: true,
          }),
        });

        if (!variantResponse.ok) {
          setIsCreatingBatch(false);
          const reason = await readError(variantResponse, "Variante konnte nicht angelegt werden.");
          setError(`Getraenk '${row.name}' wurde erstellt, aber Variante ${variant.label} fehlt: ${reason}`);
          await loadAll();
          return;
        }

        createdVariantCount += 1;
      }

      createdCount += 1;
    }

    setBatchRows((current) => current.map((row) => ({ ...row, name: "", description: "" })));
    setStatus(`${createdCount} Getränk${createdCount === 1 ? "" : "e"} und ${createdVariantCount} Varianten angelegt.`);
    setIsCreatingBatch(false);
    await loadAll();
  }

  async function updateDrink() {
    if (editMode?.type !== "drink") return;
    setError(null);
    setStatus(null);
    const response = await fetch(`/api/drinks/${editMode.id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        categoryId: Number(editMode.categoryId),
        name: editMode.name.trim(),
        description: editMode.description.trim() || null,
        imageUrl: null,
        active: true,
      }),
    });

    if (!response.ok) {
      setError(await readError(response, "Getrank konnte nicht aktualisiert werden."));
      return;
    }

    setEditMode(null);
    setStatus("Getrank aktualisiert.");
    await loadAll();
  }

  async function deleteDrink(id: number) {
    setError(null);
    setStatus(null);
    setDeletingDrink(true);
    const response = await fetch(`/api/drinks/${id}`, { method: "DELETE" });

    if (!response.ok) {
      setError(await readError(response, "Drink konnte nicht gelöscht werden."));
      setDeletingDrink(false);
      return;
    }

    setDeleteDrinkModal(null);
    setStatus("Drink gelöscht.");
    await loadAll();
    setDeletingDrink(false);
  }

  async function deleteCategory(id: number) {
    setError(null);
    setStatus(null);
    setDeletingCategory(true);
    const response = await fetch(`/api/drink-categories/${id}`, { method: "DELETE" });

    if (!response.ok) {
      setError(await readError(response, "Kategorie konnte nicht gelöscht werden."));
      setDeletingCategory(false);
      return;
    }

    setDeleteCategoryModal(null);
    setStatus("Kategorie gelöscht.");
    await loadAll();
    setDeletingCategory(false);
  }

  async function deleteVariant(id: number) {
    if (!confirm("Diese Variante wirklich löschen?")) return;
    setError(null);
    setStatus(null);
    const response = await fetch(`/api/drink-variants/${id}`, { method: "DELETE" });

    if (!response.ok) {
      setError(await readError(response, "Variante konnte nicht gelöscht werden."));
      return;
    }

    setStatus("Variante gelöscht.");
    await loadAll();
  }

  async function createVariant() {
    setError(null);
    setStatus(null);
    const response = await fetch("/api/drink-variants", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        drinkId: Number(variantDrinkId),
        displayVolumeName: variantLabel.trim(),
        volumeMl: Number(variantVolumeMl),
        useStandardPrice: true,
        sku: variantSku.trim() || null,
        active: true,
      }),
    });

    if (!response.ok) {
      setError(await readError(response, "Variante konnte nicht angelegt werden."));
      return;
    }

    setVariantLabel("");
    setVariantSku("");
    setStatus("Variante angelegt.");
    await loadAll();
  }

  async function updateVariant() {
    if (editMode?.type !== "variant") return;
    setError(null);
    setStatus(null);
    if (!editMode.useStandardPrice) {
      const customPrice = Number(editMode.price);
      if (!Number.isFinite(customPrice) || customPrice < 0) {
        setError("Bitte einen gueltigen individuellen Preis eingeben.");
        return;
      }
    }
    const response = await fetch(`/api/drink-variants/${editMode.id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        drinkId: editMode.drinkId,
        displayVolumeName: editMode.label.trim(),
        volumeMl: Number(editMode.volumeMl),
        useStandardPrice: editMode.useStandardPrice,
        price: editMode.useStandardPrice ? null : Number(editMode.price).toFixed(2),
        sku: editMode.sku.trim() || null,
        active: true,
      }),
    });

    if (!response.ok) {
      setError(await readError(response, "Variante konnte nicht aktualisiert werden."));
      return;
    }

    setEditMode(null);
    setStatus("Variante aktualisiert.");
    await loadAll();
  }

  async function applySelectedVolumePriceChange() {
    setError(null);
    setStatus(null);

    if (!selectedVolumeMlForAdjust) {
      setError("Bitte eine Volumengroesse auswaehlen.");
      return;
    }

    const selectedVolumeMl = Number(selectedVolumeMlForAdjust);
    const selectedEntry = volumePrices.find((entry) => entry.volumeMl === selectedVolumeMl);
    if (!selectedEntry) {
      setError("Der ausgewaehlte Volumenpreis wurde nicht gefunden.");
      return;
    }

    const value = Number(bulkPriceValue);
    if (!Number.isFinite(value)) {
      setError("Bitte einen gueltigen Anpassungswert eingeben.");
      return;
    }

    const currentPrice = Number(selectedEntry.price);
    if (!Number.isFinite(currentPrice)) {
      setError("Der aktuelle Preis der Auswahl ist ungueltig.");
      return;
    }

    const nextPriceRaw = bulkPriceMode === "ABSOLUTE"
      ? currentPrice + value
      : currentPrice * (1 + (value / 100));
    const nextPrice = Math.max(0, Number(nextPriceRaw.toFixed(2)));

    setApplyingSelectedPriceAdjust(true);
    const response = await fetch(`/api/volume-prices/${selectedEntry.volumeMl}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ price: nextPrice.toFixed(2) }),
    });
    setApplyingSelectedPriceAdjust(false);

    if (!response.ok) {
      setError(await readError(response, "Ausgewaehlter Volumenpreis konnte nicht aktualisiert werden."));
      await loadAll();
      return;
    }

    setStatus(
      bulkPriceMode === "ABSOLUTE"
        ? `${selectedEntry.volumeMl} ml wurde um ${value.toFixed(2)} EUR angepasst.`
        : `${selectedEntry.volumeMl} ml wurde um ${value.toFixed(2)}% angepasst.`,
    );
    await loadAll();
  }

  const deleteModalVariants = useMemo(() => {
    if (!deleteDrinkModal) return [];
    return variants.filter((variant) => variant.drinkId === deleteDrinkModal.id);
  }, [deleteDrinkModal, variants]);

  const deleteModalCategoryDrinks = useMemo(() => {
    if (!deleteCategoryModal) return [];
    return drinks.filter((drink) => drink.categoryId === deleteCategoryModal.id);
  }, [deleteCategoryModal, drinks]);

  const drinksWithoutInventory = useMemo(() => {
    const linkedDrinkIds = new Set(
      inventoryItems
        .filter((item) => item.linkedDrinkId !== null)
        .map((item) => item.linkedDrinkId)
    );
    return drinks.filter((drink) => !linkedDrinkIds.has(drink.id));
  }, [drinks, inventoryItems]);

  const parsedCategorySortOrder = Number(categorySortOrder.trim().replace(",", "."));
  const isCategorySortOrderValid = Number.isInteger(parsedCategorySortOrder) && parsedCategorySortOrder >= 0;
  const canCreateCategory = categoryName.trim().length > 0 && isCategorySortOrderValid;
  const hasAtLeastOneValidBatchVariant = batchVariantRows.some((row) => {
    const price = Number(row.price);
    return row.label.trim().length > 0 && Number.isFinite(price) && price > 0;
  });
  const canCreateBatch = drinkCategoryId
    && hasAtLeastOneValidBatchVariant
    && batchRows.some((row) => row.name.trim().length > 0);

  return (
    <section className="space-y-6 p-4 sm:p-6 animate-in fade-in slide-in-from-bottom-4 duration-500">
      <Card>
        <CardHeader>
          <CardTitle className="inline-flex items-center gap-2 text-2xl"><Beer className="h-6 w-6 text-cyan-400" />Bar Admin</CardTitle>
          <CardDescription>Hier kannst du Kategorien, Getränke und Varianten anlegen (Admin only).</CardDescription>
        </CardHeader>
        <CardContent>
          {status ? <div className="rounded-lg bg-emerald-500/15 border border-emerald-500/40 px-4 py-3 text-sm text-emerald-100">{status}</div> : null}
          {error ? <div className="rounded-lg bg-red-500/15 border border-red-500/40 px-4 py-3 text-sm text-red-100">{error}</div> : null}
        </CardContent>
      </Card>

      <div className="grid gap-5 xl:grid-cols-2">

        <Card className="border-cyan-500/30 bg-gradient-to-br from-cyan-500/5 to-transparent">
          <CardHeader>
            <CardTitle className="text-lg">Kategorie anlegen</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2.5">
              <Label>Name</Label>
              <Input value={categoryName} onChange={(event) => setCategoryName(event.target.value)} placeholder="z. B. Weine" />
            </div>
            <div className="space-y-2.5">
              <Label>Sortierung</Label>
              <Input type="number" value={categorySortOrder} onChange={(event) => setCategorySortOrder(event.target.value)} />
              <p className={`text-xs ${isCategorySortOrderValid ? "text-[color:var(--color-muted-foreground)]" : "text-amber-200"}`}>
                Ganze Zahl ab 0 (z. B. 10, 20, 30).
              </p>
            </div>
            <Button className="w-full" onClick={() => void createCategory()} disabled={!canCreateCategory}>
              <PlusCircle className="h-4 w-4" />Kategorie speichern
            </Button>
          </CardContent>
        </Card>

        <Card className="border-cyan-500/30 bg-gradient-to-br from-cyan-500/5 to-transparent">
          <CardHeader>
            <CardTitle className="text-lg">Getränke schnell anlegen</CardTitle>
            <CardDescription>
              Kategorie wählen und mehrere Produkte mit Varianten in einem Durchgang speichern.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2.5">
              <Label>Kategorie</Label>
              <Select value={drinkCategoryId} onChange={(event) => setDrinkCategoryId(event.target.value)}>
                {categories.map((category) => (
                  <option key={category.id} value={String(category.id)}>{category.name}</option>
                ))}
              </Select>
            </div>
            <div className="rounded-lg border border-cyan-500/20 bg-cyan-500/5 p-3 space-y-3">
              <p className="text-sm font-semibold">Varianten für alle neuen Getränke</p>
              <div className="flex flex-wrap gap-2">
                {BATCH_VARIANT_TEMPLATES.map((template) => (
                  <Button
                    key={template.id}
                    type="button"
                    size="sm"
                    variant={selectedVariantTemplateId === template.id ? "default" : "outline"}
                    onClick={() => applyBatchVariantTemplate(template.id)}
                  >
                    {template.name}
                  </Button>
                ))}
              </div>
              {batchVariantRows.map((row) => (
                <div key={row.id} className="grid gap-2 rounded-lg border border-[color:var(--color-border-strong)] p-2 sm:grid-cols-[1fr_1fr_auto]">
                  <Select
                    value={row.label}
                    onChange={(event) => updateBatchVariantRow(row.id, { label: event.target.value })}
                  >
                    <option value="">Label wählen...</option>
                    {Object.keys(getLabelToVolumeMlMap())
                      .sort()
                      .map((label) => (
                        <option key={label} value={label}>{label}</option>
                      ))}
                  </Select>
                  <Input
                    type="number"
                    min="0"
                    step="0.01"
                    value={row.price}
                    onChange={(event) => updateBatchVariantRow(row.id, { price: event.target.value })}
                    placeholder="Preis EUR"
                  />
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => removeBatchVariantRow(row.id)}
                    disabled={batchVariantRows.length <= 1}
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              ))}
              <Button type="button" variant="secondary" onClick={addBatchVariantRow}>
                <PlusCircle className="h-4 w-4" />
                Varianten-Zeile hinzufügen
              </Button>
            </div>

            <div className="rounded-lg border border-cyan-500/20 bg-cyan-500/5 p-3 space-y-3">
              <p className="text-sm font-semibold">Produktliste</p>
              {batchRows.map((row, index) => (
                <div key={row.id} className="grid gap-2 rounded-lg border border-[color:var(--color-border-strong)] p-2 sm:grid-cols-[1.1fr_1fr_auto]">
                  <Input
                    value={row.name}
                    onChange={(event) => updateBatchRow(row.id, { name: event.target.value })}
                    placeholder={`Getränk ${index + 1} (z. B. Grauburgunder)`}
                  />
                  <Input
                    value={row.description}
                    onChange={(event) => updateBatchRow(row.id, { description: event.target.value })}
                    placeholder="Beschreibung (optional)"
                  />
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => removeBatchRow(row.id)}
                    disabled={batchRows.length <= 1}
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              ))}
              <Button type="button" variant="secondary" onClick={addBatchRow}>
                <PlusCircle className="h-4 w-4" />
                Zeile hinzufügen
              </Button>
            </div>

            <Button className="w-full" onClick={() => void createDrinkBatch()} disabled={!canCreateBatch || isCreatingBatch}>
              <PlusCircle className="h-4 w-4" />
              {isCreatingBatch ? "Lege Getränke an..." : "Alle Getränke mit Variante speichern"}
            </Button>
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-5 xl:grid-cols-2">
        <Card className="border-cyan-500/30 bg-gradient-to-br from-cyan-500/5 to-transparent">
          <CardHeader>
            <CardTitle className="text-lg">Variante für bestehendes Getränk</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2.5">
              <Label>Getränk</Label>
              <Select value={variantDrinkId} onChange={(event) => setVariantDrinkId(event.target.value)}>
                {drinks.map((drink) => (
                  <option key={drink.id} value={String(drink.id)}>{drink.name}</option>
                ))}
              </Select>
            </div>
            <div className="space-y-2.5">
              <Label>Label</Label>
              <Input value={variantLabel} onChange={(event) => setVariantLabel(event.target.value)} placeholder="z. B. Pint" />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2.5">
                <Label>ml</Label>
                <Input type="number" value={variantVolumeMl} onChange={(event) => setVariantVolumeMl(event.target.value)} />
              </div>
            </div>
            <p className="text-xs text-[color:var(--color-muted-foreground)]">
              Der Preis wird global ueber die Volumengroesse (ml) gesteuert.
            </p>
            <div className="space-y-2.5">
              <Label>SKU (optional)</Label>
              <Input value={variantSku} onChange={(event) => setVariantSku(event.target.value)} />
            </div>
            <Button className="w-full" onClick={() => void createVariant()} disabled={!variantDrinkId || !variantLabel.trim()}>
              <PlusCircle className="h-4 w-4" />Variante speichern
            </Button>
          </CardContent>
        </Card>

        <Card className="border-cyan-500/30 bg-gradient-to-br from-cyan-500/5 to-transparent">
          <CardHeader>
            <CardTitle className="text-lg">Volumenpreise</CardTitle>
            <CardDescription>Preis gezielt pro Volumen anpassen.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="rounded-lg border border-cyan-500/20 bg-cyan-500/5 p-3">
              <p className="mb-3 text-sm font-semibold">Gezielte Anpassung</p>
              <div className="grid gap-3 sm:grid-cols-[minmax(120px,max-content)_minmax(190px,max-content)_minmax(140px,1fr)]">
                <div className="space-y-2.5">
                  <Label>Volumen</Label>
                  <Select
                    className="w-fit min-w-[120px]"
                    value={selectedVolumeMlForAdjust}
                    onChange={(event) => setSelectedVolumeMlForAdjust(event.target.value)}
                  >
                    {volumePrices.map((entry) => (
                      <option key={entry.id} value={String(entry.volumeMl)}>{entry.volumeMl} ml</option>
                    ))}
                  </Select>
                </div>
                <div className="space-y-2.5">
                  <Label>Modus</Label>
                  <Select
                    className="w-fit min-w-[190px]"
                    value={bulkPriceMode}
                    onChange={(event) => setBulkPriceMode(event.target.value as "ABSOLUTE" | "PERCENT")}
                  >
                    <option value="ABSOLUTE">+/- EUR</option>
                    <option value="PERCENT">+/- Prozent</option>
                  </Select>
                </div>
                <div className="space-y-2.5">
                  <Label>Wert</Label>
                  <Input
                    type="number"
                    step="0.01"
                    value={bulkPriceValue}
                    onChange={(event) => setBulkPriceValue(event.target.value)}
                  />
                </div>
              </div>
              <Button
                className="mt-3 w-full"
                variant="secondary"
                onClick={() => void applySelectedVolumePriceChange()}
                disabled={applyingSelectedPriceAdjust || !selectedVolumeMlForAdjust || volumePrices.length === 0}
              >
                {applyingSelectedPriceAdjust ? "Passe an..." : "Ausgewählten Volumenpreis anpassen"}
              </Button>
            </div>
            {volumePrices.length === 0 ? (
              <p className="text-sm text-[color:var(--color-muted-foreground)]">
                Keine Volumenpreise vorhanden. Lege zuerst Volumenpreise im Konfigurationsbereich an.
              </p>
            ) : null}
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-5">
          <Card>
            <CardHeader>
              <CardTitle className="text-xl">Kategorien verwalten</CardTitle>
              <CardDescription>Bestehende Kategorien löschen.</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="space-y-3">
                {categories.length === 0 ? (
                  <p className="text-sm text-[color:var(--color-muted-foreground)]">Keine Kategorien vorhanden.</p>
                ) : (
                  categories.map((category) => (
                    <div key={category.id} className="flex items-start justify-between gap-3 rounded-lg border border-cyan-500/20 bg-cyan-500/5 p-4">
                      <div>
                        <p className="font-semibold text-[color:var(--color-foreground)]">{category.name}</p>
                        <p className="text-sm text-[color:var(--color-muted-foreground)]">Sortierung: {category.sortOrder}</p>
                      </div>
                      <Button
                        size="sm"
                        variant="destructive"
                        onClick={() => setDeleteCategoryModal({ id: category.id, name: category.name })}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  ))
                )}
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-xl">Getränke verwalten</CardTitle>
              <CardDescription>Bestehende Getränke bearbeiten oder löschen.</CardDescription>
            </CardHeader>
            <CardContent>
              {drinksWithoutInventory.length > 0 ? (
                <div className="mb-4 rounded-lg border border-amber-500/40 bg-amber-500/10 p-3 flex gap-3 items-start">
                  <AlertCircle className="h-5 w-5 text-amber-500 flex-shrink-0 mt-0.5" />
                  <div className="text-sm text-amber-200">
                    <p className="font-semibold mb-1">Getränke ohne Lagerverknüpfung</p>
                    <p className="text-amber-100">
                      {drinksWithoutInventory.length} Getränk{drinksWithoutInventory.length === 1 ? "" : "e"} {drinksWithoutInventory.length === 1 ? "ist" : "sind"} nicht mit dem Lager verknüpft.
                    </p>
                  </div>
                </div>
              ) : null}
              <div className="space-y-3">
                {drinks.length === 0 ? (
                  <p className="text-sm text-[color:var(--color-muted-foreground)]">Keine Getränke vorhanden.</p>
                ) : (
                  drinks.map((drink) => {
                    const category = categories.find((c) => c.id === drink.categoryId);
                    const hasInventoryLink = inventoryItems.some((item) => item.linkedDrinkId === drink.id);
                    return (
                      <div key={drink.id} className="flex items-start justify-between gap-3 rounded-lg border border-cyan-500/20 bg-cyan-500/5 p-4">
                        <div className="flex-1">
                          <div className="flex items-center gap-2">
                            <p className="font-semibold text-[color:var(--color-foreground)]">{drink.name}</p>
                            {!hasInventoryLink && (
                              <Badge variant="destructive" className="flex gap-1 items-center">
                                <AlertCircle className="h-3 w-3" />Kein Lager
                              </Badge>
                            )}
                          </div>
                          <p className="text-sm text-[color:var(--color-muted-foreground)]">{category?.name ?? "?"} {drink.description ? `· ${drink.description}` : ""}</p>
                        </div>
                        <div className="flex gap-2">
                          <Button size="sm" variant="secondary" onClick={() => setEditMode({ type: "drink", id: drink.id, name: drink.name, categoryId: String(drink.categoryId), description: drink.description || "" })}>
                            <Edit2 className="h-4 w-4" />
                          </Button>
                          <Button
                            size="sm"
                            variant="destructive"
                            onClick={() => setDeleteDrinkModal({ id: drink.id, name: drink.name })}
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-xl">Varianten verwalten</CardTitle>
              <CardDescription>Bestehende Varianten bearbeiten oder löschen.</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="space-y-3">
                {variants.length === 0 ? (
                  <p className="text-sm text-[color:var(--color-muted-foreground)]">Keine Varianten vorhanden.</p>
                ) : (
                  variants.map((variant) => (
                    <div key={variant.id} className="flex items-start justify-between gap-3 rounded-lg border border-cyan-500/20 bg-cyan-500/5 p-4">
                      <div>
                        <div className="flex items-center gap-2">
                          <p className="font-semibold text-[color:var(--color-foreground)]">{variant.drinkName} · {variant.displayVolumeName}</p>
                          <Badge variant={variant.useStandardPrice ? "muted" : "warning"}>
                            {variant.useStandardPrice ? "Standardpreis" : "Sonderpreis"}
                          </Badge>
                        </div>
                        <p className="text-sm text-[color:var(--color-muted-foreground)]">{variant.volumeMl} ml · {variant.price} EUR</p>
                      </div>
                      <div className="flex gap-2">
                        <Button size="sm" variant="secondary" onClick={() => setEditMode({
                          type: "variant",
                          id: variant.id,
                          drinkId: variant.drinkId,
                          label: variant.displayVolumeName,
                          volumeMl: String(variant.volumeMl),
                          useStandardPrice: variant.useStandardPrice,
                          price: variant.price,
                          sku: variant.sku || "",
                        })}>
                          <Edit2 className="h-4 w-4" />
                        </Button>
                        <Button size="sm" variant="destructive" onClick={() => void deleteVariant(variant.id)}>
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </CardContent>
          </Card>
      </div>

      {editMode ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <Card className="w-full max-w-md">
            <CardHeader className="flex flex-row items-center justify-between gap-2">
              <CardTitle>{editMode.type === "drink" ? "Getränk bearbeiten" : "Variante bearbeiten"}</CardTitle>
              <button onClick={() => setEditMode(null)} className="text-[color:var(--color-muted-foreground)] hover:text-[color:var(--color-foreground)]">
                <X className="h-5 w-5" />
              </button>
            </CardHeader>
            <CardContent className="space-y-4">
              {editMode.type === "drink" ? (
                <>
                  <div className="space-y-2">
                    <Label>Kategorie</Label>
                    <Select value={editMode.categoryId} onChange={(e) => setEditMode({ ...editMode, categoryId: e.target.value })}>
                      {categories.map((cat) => (
                        <option key={cat.id} value={String(cat.id)}>{cat.name}</option>
                      ))}
                    </Select>
                  </div>
                  <div className="space-y-2">
                    <Label>Name</Label>
                    <Input value={editMode.name} onChange={(e) => setEditMode({ ...editMode, name: e.target.value })} />
                  </div>
                  <div className="space-y-2">
                    <Label>Beschreibung</Label>
                    <Input value={editMode.description} onChange={(e) => setEditMode({ ...editMode, description: e.target.value })} />
                  </div>
                  <div className="flex gap-2 pt-4">
                    <Button className="flex-1" onClick={() => void updateDrink()}>Speichern</Button>
                    <Button className="flex-1" variant="outline" onClick={() => setEditMode(null)}>Abbrechen</Button>
                  </div>
                </>
              ) : (
                <>
                  <div className="space-y-2">
                    <Label>Label</Label>
                    <Input value={editMode.label} onChange={(e) => setEditMode({ ...editMode, label: e.target.value })} />
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div className="space-y-2">
                      <Label>ml</Label>
                      <Input type="number" value={editMode.volumeMl} onChange={(e) => setEditMode({ ...editMode, volumeMl: e.target.value })} />
                    </div>
                  </div>
                  <div className="space-y-2">
                    <Label>Preisquelle</Label>
                    <Select
                      value={editMode.useStandardPrice ? "STANDARD" : "CUSTOM"}
                      onChange={(e) => setEditMode({ ...editMode, useStandardPrice: e.target.value === "STANDARD" })}
                    >
                      <option value="STANDARD">Standard-Volumenpreis verwenden</option>
                      <option value="CUSTOM">Individuellen Preis für dieses Getränk setzen</option>
                    </Select>
                  </div>
                  {editMode.useStandardPrice ? (
                    <p className="text-xs text-[color:var(--color-muted-foreground)]">
                      Aktueller Standardpreis für diese Größe: {editMode.price} EUR
                    </p>
                  ) : (
                    <div className="space-y-2">
                      <Label>Individueller Preis EUR</Label>
                      <Input
                        type="number"
                        min="0"
                        step="0.01"
                        value={editMode.price}
                        onChange={(e) => setEditMode({ ...editMode, price: e.target.value })}
                      />
                    </div>
                  )}
                  <div className="space-y-2">
                    <Label>SKU</Label>
                    <Input value={editMode.sku} onChange={(e) => setEditMode({ ...editMode, sku: e.target.value })} />
                  </div>
                  <div className="flex gap-2 pt-4">
                    <Button className="flex-1" onClick={() => void updateVariant()}>Speichern</Button>
                    <Button className="flex-1" variant="outline" onClick={() => setEditMode(null)}>Abbrechen</Button>
                  </div>
                </>
              )}
              {status ? <p className="rounded-lg bg-emerald-500/15 border border-emerald-500/40 px-4 py-3 text-sm text-emerald-100">{status}</p> : null}
              {error ? <p className="rounded-lg bg-red-500/15 border border-red-500/40 px-4 py-3 text-sm text-red-100">{error}</p> : null}
            </CardContent>
          </Card>
        </div>
      ) : null}

      {deleteDrinkModal ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <Card className="w-full max-w-md">
            <CardHeader className="flex flex-row items-center justify-between gap-2">
              <CardTitle>Drink löschen?</CardTitle>
              <button
                type="button"
                onClick={() => !deletingDrink && setDeleteDrinkModal(null)}
                className="text-[color:var(--color-muted-foreground)] hover:text-[color:var(--color-foreground)]"
                disabled={deletingDrink}
              >
                <X className="h-5 w-5" />
              </button>
            </CardHeader>
            <CardContent className="space-y-4">
              <p className="text-sm text-[color:var(--color-muted-foreground)]">
                Willst du <span className="font-semibold text-[color:var(--color-foreground)]">{deleteDrinkModal.name}</span> wirklich löschen?
                {" "}Es werden <span className="font-semibold text-[color:var(--color-foreground)]">{deleteModalVariants.length}</span>{" "}
                Variante{deleteModalVariants.length === 1 ? "" : "n"} ebenfalls dauerhaft aus der Datenbank entfernt.
              </p>
              {deleteModalVariants.length > 0 ? (
                <div className="rounded-lg border border-[color:var(--color-border-strong)] bg-[color:var(--color-surface-muted)]/60 px-3 py-2 text-xs text-[color:var(--color-muted-foreground)]">
                  <p className="font-semibold text-[color:var(--color-foreground)]">Betroffene Varianten</p>
                  <p className="mt-1">
                    {deleteModalVariants
                      .slice(0, 3)
                      .map((variant) => variant.displayVolumeName)
                      .join(", ")}
                    {deleteModalVariants.length > 3 ? ` ... und ${deleteModalVariants.length - 3} weitere` : ""}
                  </p>
                </div>
              ) : null}
              <div className="flex gap-2">
                <Button
                  className="flex-1"
                  variant="destructive"
                  onClick={() => void deleteDrink(deleteDrinkModal.id)}
                  disabled={deletingDrink}
                >
                  {deletingDrink ? "Lösche..." : "Ja, endgültig löschen"}
                </Button>
                <Button
                  className="flex-1"
                  variant="outline"
                  onClick={() => setDeleteDrinkModal(null)}
                  disabled={deletingDrink}
                >
                  Abbrechen
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      ) : null}

      {deleteCategoryModal ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <Card className="w-full max-w-md">
            <CardHeader className="flex flex-row items-center justify-between gap-2">
              <CardTitle>Kategorie löschen?</CardTitle>
              <button
                type="button"
                onClick={() => !deletingCategory && setDeleteCategoryModal(null)}
                className="text-[color:var(--color-muted-foreground)] hover:text-[color:var(--color-foreground)]"
                disabled={deletingCategory}
              >
                <X className="h-5 w-5" />
              </button>
            </CardHeader>
            <CardContent className="space-y-4">
              <p className="text-sm text-[color:var(--color-muted-foreground)]">
                Willst du <span className="font-semibold text-[color:var(--color-foreground)]">{deleteCategoryModal.name}</span> wirklich löschen?
                {" "}Es sind aktuell <span className="font-semibold text-[color:var(--color-foreground)]">{deleteModalCategoryDrinks.length}</span>{" "}
                Getränk{deleteModalCategoryDrinks.length === 1 ? "" : "e"} dieser Kategorie zugeordnet.
              </p>
              <div className="flex gap-2">
                <Button
                  className="flex-1"
                  variant="destructive"
                  onClick={() => void deleteCategory(deleteCategoryModal.id)}
                  disabled={deletingCategory}
                >
                  {deletingCategory ? "Lösche..." : "Ja, endgültig löschen"}
                </Button>
                <Button
                  className="flex-1"
                  variant="outline"
                  onClick={() => setDeleteCategoryModal(null)}
                  disabled={deletingCategory}
                >
                  Abbrechen
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      ) : null}
    </section>
  );
}

