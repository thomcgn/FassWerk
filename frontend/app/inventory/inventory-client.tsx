"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { AlertTriangle, Boxes, ChevronDown, FileDown, Search, Trash2, Truck } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select } from "@/components/ui/select";
import { useToastFeedback } from "@/lib/use-toast-feedback";
import { ReorderDashboard } from "@/components/reorder-dashboard";
import type { Drink, DrinkVariant, InventoryItem, InventoryItemUpsertRequest, InventoryPackageDefaults } from "@/types/api";

type LoadState = "loading" | "ready" | "error";
type PackageType = InventoryItemUpsertRequest["packageType"];
const INVENTORY_CONFIG_VISIBILITY_KEY = "inventory.config.visible";

const crateDefaults = {
  bottlesPerCrate: "12",
  litersPerBottle: "0.75",
};

const contentDefaultsByType: Record<PackageType, { contentUnit: "LITER" | "PIECE" | "MILLILITER"; contentPerPackage: string }> = {
  BARREL: { contentUnit: "LITER", contentPerPackage: "50" },
  CRATE: { contentUnit: "LITER", contentPerPackage: "4.5" },
  BOTTLE: { contentUnit: "LITER", contentPerPackage: "0.5" },
  SINGLE_BOTTLE: { contentUnit: "LITER", contentPerPackage: "0.3" },
  BOX: { contentUnit: "PIECE", contentPerPackage: "1" },
};

function parseLocaleNumber(value: string): number {
  return Number(value.replace(",", "."));
}

function calculateCrateContentPerPackage(contentUnit: "LITER" | "PIECE" | "MILLILITER" | string, bottlesPerCrate: string, litersPerBottle: string): string {
  const bottles = parseLocaleNumber(bottlesPerCrate);
  const liters = parseLocaleNumber(litersPerBottle);
  if (!Number.isFinite(bottles) || bottles <= 0) {
    return "0";
  }
  if (contentUnit === "PIECE") {
    return String(bottles);
  }
  if (!Number.isFinite(liters) || liters <= 0) {
    return "0";
  }
  if (contentUnit === "MILLILITER") {
    return String(bottles * liters * 1000);
  }
  return String(bottles * liters);
}

function packageAmountLabelLower(value: InventoryItemUpsertRequest["packageType"] | string): string {
  const label = packageAmountLabel(value);
  return label.charAt(0).toLowerCase() + label.slice(1);
}

function getStatus(item: InventoryItem): { label: string; variant: "success" | "warning" | "destructive" | "muted" } {
  if (!item.active) return { label: "Inaktiv", variant: "muted" };
  const stock = Number(item.totalStockAmount);
  const threshold = Number(item.reorderThreshold);
  if (Number.isNaN(stock) || Number.isNaN(threshold) || stock <= threshold) {
    return { label: "Nachbestellen", variant: "destructive" };
  }
  return { label: "Stabil", variant: "success" };
}

function packageTypeLabel(value: InventoryItemUpsertRequest["packageType"] | string): string {
  switch (value) {
    case "BARREL":
      return "Fass";
    case "CRATE":
      return "Kasten";
    case "BOTTLE":
      return "Flasche";
    case "SINGLE_BOTTLE":
      return "Einzelflasche";
    case "BOX":
      return "Karton / Box";
    default:
      return value;
  }
}

function packageAmountLabel(value: InventoryItemUpsertRequest["packageType"] | string): string {
  switch (value) {
    case "BARREL":
      return "Faesser";
    case "CRATE":
      return "Kaesten";
    case "BOTTLE":
      return "Flaschen";
    case "SINGLE_BOTTLE":
      return "Einzelflaschen";
    case "BOX":
      return "Boxen";
    default:
      return "Gebinde";
  }
}

function unitLabel(value: InventoryItemUpsertRequest["contentUnit"] | string): string {
  if (value === "LITER") return "Liter (l)";
  if (value === "PIECE") return "Stück";
  if (value === "MILLILITER") return "Milliliter (ml)";
  return value;
}

function shortUnit(value: string): string {
  if (value === "LITER") return "l";
  if (value === "PIECE") return "Stk";
  if (value === "MILLILITER") return "ml";
  return value;
}

function formatAmount(value: number): string {
  if (!Number.isFinite(value)) {
    return "-";
  }
  return new Intl.NumberFormat("de-DE", { maximumFractionDigits: 2 }).format(value);
}

function toMilliliter(value: number, unit: string): number | null {
  if (!Number.isFinite(value)) {
    return null;
  }
  if (unit === "MILLILITER") return value;
  if (unit === "LITER") return value * 1000;
  return null;
}

function formatStockForArticle(item: InventoryItem, variants: DrinkVariant[]): { primary: string; secondary: string } {
  const total = Number(item.totalStockAmount);
  const perPackage = Number(item.contentPerPackage);
  const packageCount = Number.isFinite(total) && Number.isFinite(perPackage) && perPackage > 0 ? total / perPackage : NaN;
  const baseUnitValue = Number.isFinite(total)
    ? `${formatAmount(total)} ${shortUnit(item.contentUnit)}`
    : `${item.totalStockAmount} ${shortUnit(item.contentUnit)}`;
  const packageValue = Number.isFinite(packageCount)
    ? `${formatAmount(packageCount)} ${packageAmountLabel(item.packageType)}`
    : "-";

  if (item.packageType === "CRATE" || item.packageType === "BOTTLE" || item.packageType === "SINGLE_BOTTLE") {
    const linkedVariant = item.linkedDrinkVariantId != null
      ? variants.find((variant) => variant.id === item.linkedDrinkVariantId)
      : undefined;
    if (linkedVariant && linkedVariant.volumeMl > 0) {
      const totalMl = toMilliliter(total, item.contentUnit);
      if (totalMl != null) {
        return {
          primary: `${formatAmount(totalMl / linkedVariant.volumeMl)} Flaschen`,
          secondary: baseUnitValue,
        };
      }
    }
    if (Number.isFinite(packageCount)) {
      return {
        primary: packageValue,
        secondary: baseUnitValue,
      };
    }
  }

  if (item.packageType === "BARREL" && Number.isFinite(packageCount)) {
    return {
      primary: `${formatAmount(packageCount)} ${packageAmountLabel(item.packageType)}`,
      secondary: baseUnitValue,
    };
  }

  if (item.packageType === "BOX") {
    if (item.contentUnit === "PIECE" && Number.isFinite(total)) {
      return {
        primary: `${formatAmount(total)} Stk`,
        secondary: packageValue,
      };
    }
    if (Number.isFinite(packageCount)) {
      return {
        primary: packageValue,
        secondary: baseUnitValue,
      };
    }
  }

  return {
    primary: baseUnitValue,
    secondary: packageValue,
  };
}

function formatThresholdPackages(item: InventoryItem): string {
  const threshold = Number(item.reorderThreshold);
  const perPackage = Number(item.contentPerPackage);
  if (!Number.isFinite(threshold) || !Number.isFinite(perPackage) || perPackage <= 0) {
    return "-";
  }
  return new Intl.NumberFormat("de-DE", { maximumFractionDigits: 2 }).format(threshold / perPackage);
}

function parseOptionalId(value: string): number | null {
  const trimmed = value.trim();
  if (!trimmed) {
    return null;
  }
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

export default function InventoryClient() {
  const router = useRouter();
  const [state, setState] = useState<LoadState>("loading");
  const [items, setItems] = useState<InventoryItem[]>([]);
  const [drinks, setDrinks] = useState<Drink[]>([]);
  const [variants, setVariants] = useState<DrinkVariant[]>([]);
  const [packageDefaults, setPackageDefaults] = useState<Partial<Record<PackageType, InventoryPackageDefaults>>>({});
  const [error, setError] = useState<string | null>(null);
  const [configError, setConfigError] = useState<string | null>(null);
  const [configStatus, setConfigStatus] = useState<string | null>(null);
  const [deletingItemId, setDeletingItemId] = useState<number | null>(null);
  const [deleteCandidate, setDeleteCandidate] = useState<{ id: number; name: string } | null>(null);
  const [search, setSearch] = useState("");
  const [reportSupplier, setReportSupplier] = useState("");
  const [linkItemId, setLinkItemId] = useState("");
  const [showInventoryConfig, setShowInventoryConfig] = useState(false);
  const [inventoryConfigVisibilityInitialized, setInventoryConfigVisibilityInitialized] = useState(false);
    const [form, setForm] = useState({
    name: "",
    linkedDrinkId: "",
    linkedDrinkVariantId: "",
    packageType: "CRATE",
    packagesInStock: "1",
    contentPerPackage: "1",
    contentUnit: "LITER",
    reorderThresholdPackages: "0",
    minimumStockPackages: "0",
    bottlesPerCrate: crateDefaults.bottlesPerCrate,
    litersPerBottle: crateDefaults.litersPerBottle,
    supplier: "",
    });

    const [expandedItemId, setExpandedItemId] = useState<number | null>(null);

  useToastFeedback(error, "error");
  useToastFeedback(configError, "error");
  useToastFeedback(configStatus, "success");

  const applyDefaultsForPackageType = useCallback((targetPackageType: PackageType, sourceContentPerPackage?: string) => {
    const dynamicDefaults = packageDefaults[targetPackageType];
    if (!dynamicDefaults) {
      return;
    }

    const fallbackContentPerPackage = sourceContentPerPackage ?? contentDefaultsByType[targetPackageType].contentPerPackage;
    const thresholdPackages = Number(dynamicDefaults.reorderThresholdPackages);
    const minimumStockPackages = Number(dynamicDefaults.minimumStockPackages);

    setForm((current) => ({
      ...current,
      packageType: targetPackageType,
      contentPerPackage: sourceContentPerPackage ?? current.contentPerPackage ?? fallbackContentPerPackage,
      reorderThresholdPackages: Number.isFinite(thresholdPackages) ? String(thresholdPackages) : current.reorderThresholdPackages,
      minimumStockPackages: Number.isFinite(minimumStockPackages) ? String(minimumStockPackages) : current.minimumStockPackages,
    }));
  }, [packageDefaults]);

  const loadInventory = useCallback(async () => {
    try {
      const response = await fetch("/api/inventory", { cache: "no-store" });
      if (response.status === 401) {
        router.replace("/login");
        return;
      }
      if (!response.ok) {
        setError("Inventory konnte nicht geladen werden.");
        setState("error");
        return;
      }
      const payload = (await response.json()) as InventoryItem[];
      setItems(payload);
      setLinkItemId((current) => current || String(payload[0]?.id ?? ""));
      setState("ready");
    } catch {
      setError("Unerwarteter Fehler beim Laden.");
      setState("error");
    }
  }, [router]);

  const loadVariants = useCallback(async () => {
    try {
      const response = await fetch("/api/drink-variants", { cache: "no-store" });
      if (!response.ok) {
        setVariants([]);
        return;
      }
      const payload = (await response.json()) as DrinkVariant[];
      setVariants(payload.filter((variant) => variant.active));
    } catch {
      setVariants([]);
    }
  }, []);

  const loadDrinks = useCallback(async () => {
    try {
      const response = await fetch("/api/drinks", { cache: "no-store" });
      if (!response.ok) {
        setDrinks([]);
        return;
      }
      const payload = (await response.json()) as Drink[];
      setDrinks(payload.filter((drink) => drink.active));
    } catch {
      setDrinks([]);
    }
  }, []);

  const loadPackageDefaults = useCallback(async () => {
    try {
      const response = await fetch("/api/inventory/defaults", { cache: "no-store" });
      if (!response.ok) {
        setPackageDefaults({});
        return;
      }
      const payload = (await response.json()) as InventoryPackageDefaults[];
      const defaultsMap = payload.reduce<Partial<Record<PackageType, InventoryPackageDefaults>>>((acc, entry) => {
        acc[entry.packageType] = entry;
        return acc;
      }, {});
      setPackageDefaults(defaultsMap);

      setForm((current) => {
        const dynamicDefaults = defaultsMap[current.packageType as PackageType];
        if (!dynamicDefaults) {
          return current;
        }
        return {
          ...current,
          reorderThresholdPackages: String(dynamicDefaults.reorderThresholdPackages),
          minimumStockPackages: String(dynamicDefaults.minimumStockPackages),
        };
      });
    } catch {
      setPackageDefaults({});
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadInventory();
      void loadDrinks();
      void loadVariants();
      void loadPackageDefaults();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [loadInventory, loadDrinks, loadVariants, loadPackageDefaults]);

  useEffect(() => {
    try {
      const persisted = window.localStorage.getItem(INVENTORY_CONFIG_VISIBILITY_KEY);
      if (persisted === "1") {
        setShowInventoryConfig(true);
      } else if (persisted === "0") {
        setShowInventoryConfig(false);
      }
    } catch {
      // Ignore localStorage access issues (private mode, disabled storage)
    } finally {
      setInventoryConfigVisibilityInitialized(true);
    }
  }, []);

  useEffect(() => {
    if (!inventoryConfigVisibilityInitialized) {
      return;
    }

    try {
      window.localStorage.setItem(INVENTORY_CONFIG_VISIBILITY_KEY, showInventoryConfig ? "1" : "0");
    } catch {
      // Ignore localStorage write issues.
    }
  }, [showInventoryConfig, inventoryConfigVisibilityInitialized]);

  const filteredVariantsForDrink = useMemo(() => {
    const drinkId = Number(form.linkedDrinkId);
    if (!Number.isFinite(drinkId) || !form.linkedDrinkId) {
      return variants;
    }
    return variants.filter((variant) => variant.drinkId === drinkId);
  }, [variants, form.linkedDrinkId]);

  function buildPayload(source: typeof form): InventoryItemUpsertRequest {
    const drinkId = parseOptionalId(source.linkedDrinkId);
    const variantId = parseOptionalId(source.linkedDrinkVariantId);
    const selectedVariant = variants.find((variant) => variant.id === variantId);
    const contentPerPackage = Number(source.contentPerPackage);
    const reorderThresholdPackages = Number(source.reorderThresholdPackages);
    const minimumStockPackages = Number(source.minimumStockPackages);
    return {
      name: source.name.trim(),
      linkedDrinkId: drinkId ?? selectedVariant?.drinkId ?? null,
      linkedDrinkVariantId: variantId,
      packageType: source.packageType as InventoryItemUpsertRequest["packageType"],
      packagesInStock: Number(source.packagesInStock),
      contentPerPackage,
      contentUnit: source.contentUnit as InventoryItemUpsertRequest["contentUnit"],
      reorderThreshold: reorderThresholdPackages * contentPerPackage,
      minimumStock: minimumStockPackages * contentPerPackage,
      reorderThresholdPackages,
      minimumStockPackages,
      supplier: source.supplier.trim() || null,
      active: true,
    };
  }

  async function createInventoryConfig() {
    setConfigError(null);
    setConfigStatus(null);

    if (!form.name.trim()) {
      setConfigError("Bitte einen Namen für den Lagerartikel eingeben.");
      return;
    }

    const payload = buildPayload(form);
    const response = await fetch("/api/inventory", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });

    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      setConfigError(body?.message || "Konfiguration konnte nicht angelegt werden.");
      return;
    }

    setConfigStatus("Lagerkonfiguration wurde angelegt.");
    await loadInventory();
  }

  async function updateInventoryLink() {
    setConfigError(null);
    setConfigStatus(null);
    if (!linkItemId) {
      setConfigError("Bitte Bestandseintrag auswählen.");
      return;
    }

    const existing = items.find((item) => item.id === Number(linkItemId));
    if (!existing) {
      setConfigError("Bestandseintrag nicht gefunden.");
      return;
    }

    const drinkId = parseOptionalId(form.linkedDrinkId);
    const variantId = parseOptionalId(form.linkedDrinkVariantId);
    const selectedVariant = variants.find((variant) => variant.id === variantId);

    const payload: InventoryItemUpsertRequest = {
      name: existing.name,
      linkedDrinkId: drinkId ?? selectedVariant?.drinkId ?? null,
      linkedDrinkVariantId: variantId,
      packageType: existing.packageType as InventoryItemUpsertRequest["packageType"],
      packagesInStock: Number(existing.packagesInStock),
      contentPerPackage: Number(existing.contentPerPackage),
      contentUnit: existing.contentUnit as InventoryItemUpsertRequest["contentUnit"],
      reorderThreshold: Number(existing.reorderThreshold),
      minimumStock: Number(existing.minimumStock),
      supplier: existing.supplier,
      active: existing.active,
    };

    const response = await fetch(`/api/inventory/${linkItemId}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });

    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      setConfigError(body?.message || "Verknüpfung konnte nicht gespeichert werden.");
      return;
    }

    setConfigStatus("Verknüpfung wurde gespeichert.");
    await loadInventory();
  }

  async function deleteInventoryItem(itemId: number, itemName: string) {
    setConfigError(null);
    setConfigStatus(null);
    setDeletingItemId(itemId);

    const response = await fetch(`/api/inventory/${itemId}`, { method: "DELETE" });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      setConfigError(body?.message || "Position konnte nicht gelöscht werden.");
      setDeletingItemId(null);
      return;
    }

    setConfigStatus(`Lagerposition \"${itemName}\" wurde gelöscht.`);
    setDeletingItemId(null);
    setDeleteCandidate(null);
    await loadInventory();
  }

  function openDeleteModal(itemId: number, itemName: string) {
    setDeleteCandidate({ id: itemId, name: itemName });
  }

  const filteredItems = useMemo(() => {
    const query = search.trim().toLowerCase();
    if (!query) return items;
    return items.filter((item) => {
      const supplier = item.supplier?.toLowerCase() ?? "";
      return item.name.toLowerCase().includes(query) || supplier.includes(query);
    });
  }, [items, search]);

  const criticalItems = useMemo(
    () => items.filter((item) => Number(item.totalStockAmount) <= Number(item.reorderThreshold)).length,
    [items],
  );

  const supplierOptions = useMemo(() => {
    const dynamicSuppliers = items
      .map((item) => item.supplier?.trim())
      .filter((supplier): supplier is string => Boolean(supplier));
    return Array.from(new Set(dynamicSuppliers)).sort((a, b) => a.localeCompare(b));
  }, [items]);

  const reorderPdfHref = reportSupplier
    ? `/api/reports/reorder-list?supplier=${encodeURIComponent(reportSupplier)}`
    : "/api/reports/reorder-list";

  if (state === "loading") return <main className="p-6 text-sm text-[color:var(--color-muted-foreground)]">Lade Dashboard...</main>;

  if (state === "error") {
    return (
      <main className="mx-auto w-full max-w-3xl p-4 md:p-6">
        <Card className="border-red-500/35 bg-red-500/12">
          <CardHeader>
            <CardTitle className="text-red-200">Fehler</CardTitle>
            <CardDescription className="text-red-100">{error}</CardDescription>
          </CardHeader>
          <CardContent>
            <Button variant="outline" onClick={() => void loadInventory()}>Erneut versuchen</Button>
          </CardContent>
        </Card>
      </main>
    );
  }

  return (
    <>
      <main className="dashboard-grid animate-in fade-in duration-500">
      <section className="grid gap-5 lg:grid-cols-[1.1fr_0.9fr]">
        <Card className="border-cyan-500/30 bg-gradient-to-br from-cyan-500/10 to-transparent">
          <CardContent className="p-4 sm:p-5">
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="text-xs uppercase tracking-[0.2em] font-semibold text-cyan-400">Dashboard</p>
                <h1 className="mt-1 text-2xl font-bold tracking-tight">Lagerbestand kompakt</h1>
              </div>
              <p className="text-xs text-[color:var(--color-muted-foreground)]">Live-Übersicht</p>
            </div>
            <div className="mt-4 grid gap-2 sm:grid-cols-3">
              {[
                { icon: Boxes, label: "Aktive Artikel", value: items.filter((item) => item.active).length },
                { icon: AlertTriangle, label: "Unter Schwelle", value: criticalItems },
                { icon: Truck, label: "Lieferanten", value: new Set(items.map((item) => item.supplier).filter(Boolean)).size },
              ].map((item) => {
                const Icon = item.icon;
                return (
                  <div key={item.label} className="rounded-lg border border-cyan-500/30 bg-cyan-500/10 p-3">
                    <div className="flex items-center justify-between gap-2">
                      <p className="text-xs font-semibold uppercase tracking-[0.16em] text-cyan-300">{item.label}</p>
                      <Icon className="h-4 w-4 text-cyan-300" />
                    </div>
                    <p className="mt-1 text-xl font-bold leading-tight">{item.value}</p>
                  </div>
                );
              })}
            </div>
          </CardContent>
        </Card>

        <Card className="border-cyan-500/30">
          <CardHeader>
            <CardTitle>Quick Actions</CardTitle>
            <CardDescription>PDF-Export gesamt oder je Lieferant.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="report-supplier">Lieferant für PDF-Export</Label>
              <Select id="report-supplier" value={reportSupplier} onChange={(event) => setReportSupplier(event.target.value)}>
                <option value="">Alle Lieferanten</option>
                {supplierOptions.map((supplier) => (
                  <option key={supplier} value={supplier}>
                    {supplier}
                  </option>
                ))}
              </Select>
            </div>
            <a href={reorderPdfHref} target="_blank" rel="noopener noreferrer" className="block">
              <Button className="w-full justify-between" size="lg">
                {reportSupplier ? `Reorder PDF: ${reportSupplier}` : "Reorder PDF exportieren"}
                <FileDown className="h-4 w-4" />
              </Button>
            </a>
            <div className="rounded-lg bg-cyan-500/10 border border-cyan-500/30 p-4 text-sm leading-6 text-[color:var(--color-muted-foreground)]">
              Nutze dieses Dashboard als deine persönliche Leitwarte für Lagerbestände und Nachbestellungen.
            </div>
          </CardContent>
        </Card>
      </section>

      {/* Doppelte KPI-Sektion entfernt: Kennzahlen sind bereits im Dashboard-Header enthalten. */}

      <Card className="border-cyan-500/20">
        <CardHeader className="gap-4 md:flex-row md:items-center md:justify-between">
          <div>
            <CardTitle>Inventory-Konfiguration</CardTitle>
            <CardDescription>
              Verknüpfe Drink-Varianten mit Lagerartikeln oder lege neue Lagerkonfigurationen an.
            </CardDescription>
          </div>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => setShowInventoryConfig((prev) => !prev)}
            className="gap-2"
            aria-expanded={showInventoryConfig}
            aria-controls="inventory-config-content"
          >
            <ChevronDown className={`h-4 w-4 transition-transform ${showInventoryConfig ? "rotate-180" : ""}`} />
            {showInventoryConfig ? "Konfiguration ausblenden" : "Konfiguration einblenden"}
          </Button>
        </CardHeader>
        {showInventoryConfig ? (
        <CardContent id="inventory-config-content" className="grid gap-4 lg:grid-cols-2">
          <div className="space-y-3 rounded-xl border border-cyan-500/20 bg-cyan-500/5 p-4">
            <p className="text-sm font-semibold">Bestehenden Lagerartikel verknüpfen</p>
            <div className="space-y-2">
              <Select value={linkItemId} onChange={(event) => setLinkItemId(event.target.value)}>
                {items.map((item) => (
                  <option key={item.id} value={String(item.id)}>
                    {item.name}
                  </option>
                ))}
              </Select>
              <Select
                value={form.linkedDrinkId}
                onChange={(event) => {
                  const nextDrinkId = event.target.value;
                  setForm((current) => {
                    const keepVariant = current.linkedDrinkVariantId
                      ? variants.some(
                        (variant) => String(variant.id) === current.linkedDrinkVariantId && String(variant.drinkId) === nextDrinkId,
                      )
                      : false;
                    return {
                      ...current,
                      linkedDrinkId: nextDrinkId,
                      linkedDrinkVariantId: keepVariant ? current.linkedDrinkVariantId : "",
                    };
                  });
                }}
              >
                <option value="">Drink wählen (optional)</option>
                {drinks.map((drink) => (
                  <option key={drink.id} value={String(drink.id)}>
                    {drink.name}
                  </option>
                ))}
              </Select>
              <Select
                value={form.linkedDrinkVariantId}
                onChange={(event) => setForm((current) => ({ ...current, linkedDrinkVariantId: event.target.value }))}
              >
                <option value="">Variante nicht gesetzt</option>
                {filteredVariantsForDrink.map((variant) => (
                  <option key={variant.id} value={String(variant.id)}>
                    {variant.drinkName} · {variant.displayVolumeName}
                  </option>
                ))}
              </Select>
            </div>
            <Button className="w-full" variant="secondary" onClick={() => void updateInventoryLink()}>
              Verknüpfung speichern
            </Button>
          </div>

          <div className="space-y-3 rounded-xl border border-cyan-500/20 bg-cyan-500/5 p-4">
            <p className="text-sm font-semibold">Neuen Lagerartikel anlegen</p>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-2 sm:col-span-2">
                <Label htmlFor="item-name">Artikelname</Label>
                <Input
                  id="item-name"
                  value={form.name}
                  onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
                  placeholder="z. B. Cola Kasten, Guinness Fass, Kerzen"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="item-package-type">Gebindeart</Label>
                <Select
                  id="item-package-type"
                  value={form.packageType}
                  onChange={(event) => {
                    const nextPackageType = event.target.value as PackageType;
                    const nextContentUnit = contentDefaultsByType[nextPackageType].contentUnit;
                    const nextContentPerPackage = nextPackageType === "CRATE"
                      ? calculateCrateContentPerPackage(nextContentUnit, form.bottlesPerCrate, form.litersPerBottle)
                      : contentDefaultsByType[nextPackageType].contentPerPackage;
                    setForm((current) => ({
                      ...current,
                      packageType: nextPackageType,
                      contentUnit: nextContentUnit,
                      contentPerPackage: nextContentPerPackage,
                    }));
                    applyDefaultsForPackageType(nextPackageType, nextContentPerPackage);
                  }}
                >
                  <option value="BARREL">{packageTypeLabel("BARREL")}</option>
                  <option value="CRATE">{packageTypeLabel("CRATE")}</option>
                  <option value="BOTTLE">{packageTypeLabel("BOTTLE")}</option>
                  <option value="SINGLE_BOTTLE">{packageTypeLabel("SINGLE_BOTTLE")}</option>
                  <option value="BOX">{packageTypeLabel("BOX")}</option>
                </Select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="item-content-unit">Einheit</Label>
                <Select
                  id="item-content-unit"
                  value={form.contentUnit}
                  onChange={(event) => {
                    const nextUnit = event.target.value;
                    setForm((current) => ({
                      ...current,
                      contentUnit: nextUnit,
                      contentPerPackage: current.packageType === "CRATE"
                        ? calculateCrateContentPerPackage(nextUnit, current.bottlesPerCrate, current.litersPerBottle)
                        : current.contentPerPackage,
                    }));
                  }}
                >
                  <option value="LITER">{unitLabel("LITER")}</option>
                  <option value="PIECE">{unitLabel("PIECE")}</option>
                  <option value="MILLILITER">{unitLabel("MILLILITER")}</option>
                </Select>
              </div>

              {form.packageType === "CRATE" ? (
                <>
                  <div className="space-y-2">
                    <Label htmlFor="item-bottles-per-crate">Flaschen pro Kiste</Label>
                    <Input
                      id="item-bottles-per-crate"
                      value={form.bottlesPerCrate}
                      onChange={(event) => {
                        const value = event.target.value;
                        setForm((current) => ({
                          ...current,
                          bottlesPerCrate: value,
                          contentPerPackage: calculateCrateContentPerPackage(current.contentUnit, value, current.litersPerBottle),
                        }));
                      }}
                      type="number"
                      min="1"
                      step="1"
                    />
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="item-liters-per-bottle">Liter pro Flasche</Label>
                    <Input
                      id="item-liters-per-bottle"
                      value={form.litersPerBottle}
                      onChange={(event) => {
                        const value = event.target.value;
                        setForm((current) => ({
                          ...current,
                          litersPerBottle: value,
                          contentPerPackage: calculateCrateContentPerPackage(current.contentUnit, current.bottlesPerCrate, value),
                        }));
                      }}
                      type="number"
                      min="0.01"
                      step="0.01"
                    />
                  </div>
                </>
              ) : null}

              <div className="space-y-2">
                <Label htmlFor="item-packages-stock">Anzahl {packageAmountLabelLower(form.packageType)} auf Lager</Label>
                <Input id="item-packages-stock" value={form.packagesInStock} onChange={(event) => setForm((current) => ({ ...current, packagesInStock: event.target.value }))} type="number" min="0" step="0.01" />
              </div>

              <div className="space-y-2">
                <Label htmlFor="item-content-per-package">{form.packageType === "CRATE" ? "Inhalt pro Kasten (gesamt)" : "Inhalt pro Gebinde"}</Label>
                <Input
                  id="item-content-per-package"
                  value={form.contentPerPackage}
                  onChange={(event) => setForm((current) => ({ ...current, contentPerPackage: event.target.value }))}
                  type="number"
                  min="0.01"
                  step="0.01"
                  readOnly={form.packageType === "CRATE"}
                />
                {form.packageType === "CRATE" ? (
                  <p className="text-xs text-[color:var(--color-muted-foreground)]">
                    Berechnung: {form.bottlesPerCrate || "0"} Flaschen x {form.litersPerBottle || "0"} l = {form.contentPerPackage || "0"} {shortUnit(form.contentUnit)} pro Kasten.
                  </p>
                ) : (
                  <p className="text-xs text-[color:var(--color-muted-foreground)]">
                    Das ist der Gesamtinhalt eines einzelnen Gebindes in der gewählten Einheit.
                  </p>
                )}
              </div>

              <div className="space-y-2">
                <Label htmlFor="item-reorder-threshold">Nachbestell-Schwelle ({packageAmountLabel(form.packageType)})</Label>
                <Input id="item-reorder-threshold" value={form.reorderThresholdPackages} onChange={(event) => setForm((current) => ({ ...current, reorderThresholdPackages: event.target.value }))} type="number" min="0" step="0.01" />
              </div>

              <div className="space-y-2">
                <Label htmlFor="item-minimum-stock">Mindestbestand ({packageAmountLabel(form.packageType)})</Label>
                <Input id="item-minimum-stock" value={form.minimumStockPackages} onChange={(event) => setForm((current) => ({ ...current, minimumStockPackages: event.target.value }))} type="number" min="0" step="0.01" />
              </div>

              <div className="space-y-2">
                <Label>Empfohlene Nachbestellmenge</Label>
                <div className="rounded-lg border border-[color:var(--color-border-strong)] bg-[color:var(--color-surface-muted)]/60 px-3 py-2 text-sm text-[color:var(--color-muted-foreground]">
                  Wird automatisch aus Verbrauch/Nachbestellung berechnet.
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="item-supplier">Lieferant</Label>
                <Input
                  id="item-supplier"
                  list="supplier-suggestions"
                  value={form.supplier}
                  onChange={(event) => setForm((current) => ({ ...current, supplier: event.target.value }))}
                  placeholder="z. B. Handelshof"
                />
                <datalist id="supplier-suggestions">
                  {supplierOptions.map((supplier) => (
                    <option key={supplier} value={supplier} />
                  ))}
                </datalist>
              </div>

              <div className="space-y-2 sm:col-span-2">
                <Label htmlFor="item-drink-link">Drink-Verknüpfung (optional)</Label>
                <Select
                  id="item-drink-link"
                  value={form.linkedDrinkId}
                  onChange={(event) => {
                    const nextDrinkId = event.target.value;
                    setForm((current) => {
                      const keepVariant = current.linkedDrinkVariantId
                        ? variants.some(
                          (variant) => String(variant.id) === current.linkedDrinkVariantId && String(variant.drinkId) === nextDrinkId,
                        )
                        : false;
                      return {
                        ...current,
                        linkedDrinkId: nextDrinkId,
                        linkedDrinkVariantId: keepVariant ? current.linkedDrinkVariantId : "",
                      };
                    });
                  }}
                >
                  <option value="">Kein Drink verknüpft</option>
                  {drinks.map((drink) => (
                    <option key={drink.id} value={String(drink.id)}>
                      {drink.name}
                    </option>
                  ))}
                </Select>
                <p className="text-xs text-[color:var(--color-muted-foreground)]">
                  Für den Start reicht die Drink-Verknüpfung. Eine Varianten-Verknüpfung kannst du später optional ergänzen.
                </p>
              </div>

              <div className="space-y-2 sm:col-span-2">
                <Label htmlFor="item-variant-link">Varianten-Verknüpfung (optional)</Label>
                <Select
                  id="item-variant-link"
                  value={form.linkedDrinkVariantId}
                  onChange={(event) => setForm((current) => ({ ...current, linkedDrinkVariantId: event.target.value }))}
                >
                  <option value="">Keine Verknüpfung</option>
                  {filteredVariantsForDrink.map((variant) => (
                    <option key={variant.id} value={String(variant.id)}>
                      {variant.drinkName} · {variant.displayVolumeName}
                    </option>
                  ))}
                </Select>
              </div>
            </div>
            <Button className="w-full" onClick={() => void createInventoryConfig()}>
              Lagerartikel anlegen
            </Button>
          </div>

          {configStatus ? <p className="rounded-lg border border-green-500/30 bg-green-500/10 px-3 py-2 text-sm text-green-300 lg:col-span-2">{configStatus}</p> : null}
          {configError ? <p className="rounded-lg border border-red-500/30 bg-red-500/10 px-3 py-2 text-sm text-red-300 lg:col-span-2">{configError}</p> : null}
        </CardContent>
        ) : (
          <CardContent id="inventory-config-content">
            <p className="text-sm text-[color:var(--color-muted-foreground)]">
              Ausgeblendet für bessere Übersicht. Bei Bedarf über den Button oben einblenden.
            </p>
          </CardContent>
        )}
      </Card>

      <Card>
        <CardHeader className="gap-4 md:flex-row md:items-end md:justify-between">
          <div>
            <CardTitle>Bestandsübersicht</CardTitle>
            <CardDescription>Tabletaugliche Tabelle mit klarer Priorisierung und schnellen Scan-Mustern.</CardDescription>
          </div>
          <div className="relative w-full md:max-w-sm">
            <Search className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-[color:var(--color-muted-foreground)]" />
            <Input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Nach Name oder Lieferant suchen" className="pl-10" />
          </div>
        </CardHeader>
        <CardContent>
          <div className="space-y-3 md:hidden">
            {filteredItems.map((item) => {
              const status = getStatus(item);
              const stockDisplay = formatStockForArticle(item, variants);
              return (
                <div key={item.id} className="rounded-[24px] border border-[color:var(--color-border-strong)] bg-[color:var(--color-surface)]/82 p-4 shadow-sm">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="text-base font-semibold text-[color:var(--color-foreground)]">{item.name}</p>
                      <p className="text-sm text-[color:var(--color-muted-foreground)]">{packageTypeLabel(item.packageType)} · {item.contentPerPackage} {shortUnit(item.contentUnit)}</p>
                    </div>
                    <Badge variant={status.variant}>{status.label}</Badge>
                  </div>
                  <div className="mt-4 rounded-2xl bg-[color:var(--color-surface-muted)]/70 p-3 text-sm">
                    <p className="text-[color:var(--color-muted-foreground)]">
                      Bestand: <span className="font-semibold text-[color:var(--color-foreground)]">{stockDisplay.primary}</span>
                      <span className="mx-2">•</span>
                      Schwelle: <span className="font-semibold text-[color:var(--color-foreground)]">{formatThresholdPackages(item)} Gebinde</span>
                    </p>
                    <p className="mt-1 text-xs text-[color:var(--color-muted-foreground)]">{stockDisplay.secondary}</p>
                  </div>
                  <Button
                    className="mt-3 w-full"
                    size="sm"
                    variant="outline"
                    disabled={deletingItemId === item.id}
                    onClick={() => openDeleteModal(item.id, item.name)}
                  >
                    <Trash2 className="h-4 w-4" />
                    {deletingItemId === item.id ? "Lösche..." : "Löschen"}
                  </Button>
                </div>
              );
            })}
          </div>

          <div className="hidden overflow-hidden rounded-[24px] border border-[color:var(--color-border-strong)] md:block">
            <table className="min-w-full border-collapse text-left text-sm bg-[color:var(--color-surface)]">
              <thead className="bg-[color:var(--color-surface-muted)] text-[color:var(--color-muted-foreground)]">
                <tr>
                  <th className="px-4 py-3 font-medium">Status</th>
                  <th className="px-4 py-3 font-medium">Artikel</th>
                  <th className="px-4 py-3 font-medium">Bestand</th>
                  <th className="px-4 py-3 font-medium">Schwelle</th>
                  <th className="px-4 py-3 font-medium">Lieferant</th>
                  <th className="px-4 py-3 font-medium text-right">Aktion</th>
                </tr>
              </thead>
              <tbody>
                {filteredItems.map((item) => {
                  const status = getStatus(item);
                  const stockDisplay = formatStockForArticle(item, variants);
                  return (
                    <tr key={item.id} className="border-t border-[color:var(--color-border)] bg-[color:var(--color-surface)]/50">
                      <td className="px-4 py-4"><Badge variant={status.variant}>{status.label}</Badge></td>
                      <td className="px-4 py-4 text-[color:var(--color-foreground)]">
                        <p className="font-medium">{item.name}</p>
                        <p className="mt-1 text-xs text-[color:var(--color-muted-foreground)]">
                          {packageTypeLabel(item.packageType)} ({item.contentPerPackage} {shortUnit(item.contentUnit)})
                        </p>
                      </td>
                      <td className="px-4 py-4 text-[color:var(--color-foreground)]">
                        <p>{stockDisplay.primary}</p>
                        <p className="mt-1 text-xs text-[color:var(--color-muted-foreground)]">{stockDisplay.secondary}</p>
                      </td>
                      <td className="px-4 py-4 text-(--color-foreground)">{formatThresholdPackages(item)} Gebinde</td>
                      <td className="px-4 py-4 text-(--color-muted-foreground)">{item.supplier ?? "-"}</td>
                      <td className="px-4 py-4 text-right">
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={deletingItemId === item.id}
                          onClick={() => openDeleteModal(item.id, item.name)}
                        >
                          <Trash2 className="h-4 w-4" />
                          {deletingItemId === item.id ? "Lösche..." : "Löschen"}
                        </Button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
      </main>

      {deleteCandidate ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <Card className="w-full max-w-md">
            <CardHeader>
              <CardTitle>Position löschen?</CardTitle>
              <CardDescription>
                Soll die Lagerposition &quot;{deleteCandidate.name}&quot; wirklich gelöscht werden?
              </CardDescription>
            </CardHeader>
            <CardContent className="flex items-center justify-end gap-2">
              <Button
                variant="outline"
                onClick={() => setDeleteCandidate(null)}
                disabled={deletingItemId === deleteCandidate.id}
              >
                Abbrechen
              </Button>
              <Button
                variant="destructive"
                onClick={() => void deleteInventoryItem(deleteCandidate.id, deleteCandidate.name)}
                disabled={deletingItemId === deleteCandidate.id}
              >
                {deletingItemId === deleteCandidate.id ? "Lösche..." : "Ja, löschen"}
              </Button>
            </CardContent>
          </Card>
        </div>
      ) : null}
    </>
  );
}
