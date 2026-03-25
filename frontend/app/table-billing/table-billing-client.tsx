"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Receipt } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { TableDetailModal } from "@/components/table-detail-modal";
import { useToastFeedback } from "@/lib/use-toast-feedback";
import type { Drink, DrinkCategory, DrinkVariant, InventoryItem, SplitPaymentItemRequest, SplitPaymentResponse, Table, TableOrder } from "@/types/api";

type LoadState = "loading" | "ready" | "error";

function toCurrency(value: string): string {
  const amount = Number(value);
  if (Number.isNaN(amount)) return `${value} EUR`;
  return new Intl.NumberFormat("de-DE", { style: "currency", currency: "EUR" }).format(amount);
}

function tableStatusVariant(status: Table["status"]): "success" | "warning" | "destructive" | "muted" {
  if (status === "FREE") return "success";
  if (status === "READY_FOR_PAYMENT") return "warning";
  if (status === "RESERVED") return "muted";
  return "destructive";
}

export default function TableBillingClient() {
  const router = useRouter();
  const [state, setState] = useState<LoadState>("loading");
  const [tables, setTables] = useState<Table[]>([]);
  const [categories, setCategories] = useState<DrinkCategory[]>([]);
  const [drinks, setDrinks] = useState<Drink[]>([]);
  const [variants, setVariants] = useState<DrinkVariant[]>([]);
  const [order, setOrder] = useState<TableOrder | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [orderLookupId, setOrderLookupId] = useState("");
  const [selectedTableId, setSelectedTableId] = useState("");
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [newTableName, setNewTableName] = useState("");
  const [newTableArea, setNewTableArea] = useState("INSIDE");
  const [creatingTable, setCreatingTable] = useState(false);

  useToastFeedback(error, "error");
  useToastFeedback(status, "success");

  const sellableVariants = useCallback((allVariants: DrinkVariant[], allInventoryItems: InventoryItem[]) => {
    const activeItemsWithStock = allInventoryItems.filter((item) => item.active && Number(item.totalStockAmount) > 0);
    const variantIdsWithStock = new Set<number>();
    const drinkLinkCounts = new Map<number, number>();

    for (const item of activeItemsWithStock) {
      if (item.linkedDrinkVariantId != null) {
        variantIdsWithStock.add(item.linkedDrinkVariantId);
      }
      if (item.linkedDrinkId != null) {
        drinkLinkCounts.set(item.linkedDrinkId, (drinkLinkCounts.get(item.linkedDrinkId) ?? 0) + 1);
      }
    }

    const fallbackDrinkIds = new Set(
      Array.from(drinkLinkCounts.entries())
        .filter(([, count]) => count === 1)
        .map(([drinkId]) => drinkId),
    );

    return allVariants.filter((variant) => variant.active && (variantIdsWithStock.has(variant.id) || fallbackDrinkIds.has(variant.drinkId)));
  }, []);

  const loadMeta = useCallback(async () => {
    try {
      const [tablesResponse, categoriesResponse, drinksResponse, variantsResponse, inventoryResponse] = await Promise.all([
        fetch("/api/tables", { cache: "no-store" }),
        fetch("/api/drink-categories", { cache: "no-store" }),
        fetch("/api/drinks", { cache: "no-store" }),
        fetch("/api/drink-variants", { cache: "no-store" }),
        fetch("/api/inventory", { cache: "no-store" }),
      ]);

      if ([tablesResponse.status, categoriesResponse.status, drinksResponse.status, variantsResponse.status, inventoryResponse.status].some((code) => code === 401 || code === 403)) {
        router.replace("/login");
        return;
      }

      if (!tablesResponse.ok || !categoriesResponse.ok || !drinksResponse.ok || !variantsResponse.ok || !inventoryResponse.ok) {
        setError("Stammdaten für Tischabrechnung konnten nicht geladen werden.");
        setState("error");
        return;
      }

      const [tablesPayload, categoriesPayload, drinksPayload, variantsPayload, inventoryPayload] = (await Promise.all([
        tablesResponse.json(),
        categoriesResponse.json(),
        drinksResponse.json(),
        variantsResponse.json(),
        inventoryResponse.json(),
      ])) as [Table[], DrinkCategory[], Drink[], DrinkVariant[], InventoryItem[]];

      const sellable = sellableVariants(variantsPayload, inventoryPayload);
      const sellableDrinkIds = new Set(sellable.map((variant) => variant.drinkId));
      const sellableCategoryIds = new Set(
        drinksPayload
          .filter((drink) => sellableDrinkIds.has(drink.id))
          .map((drink) => drink.categoryId),
      );

      setTables(tablesPayload.filter((table) => table.active));
      setVariants(sellable);
      setDrinks(drinksPayload.filter((drink) => drink.active && sellableDrinkIds.has(drink.id)));
      setCategories(categoriesPayload.filter((category) => category.active && sellableCategoryIds.has(category.id)));
      setState("ready");
    } catch {
      setError("Unerwarteter Fehler beim Laden der Stammdaten.");
      setState("error");
    }
  }, [router, sellableVariants]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadMeta(), 0);
    return () => window.clearTimeout(timer);
  }, [loadMeta]);

  async function openOrLoadTable(table: Table) {
    setError(null);
    setStatus(null);
    setSelectedTableId(String(table.id));

    const openResponse = await fetch(`/api/table-orders/open/table/${table.id}`, { cache: "no-store" });
    if (openResponse.ok) {
      const payload = (await openResponse.json()) as TableOrder;
      setOrder(payload);
      setOrderLookupId(String(payload.id));
      setIsModalOpen(true);
      setStatus(`Tisch ${table.name} geladen.`);
      return;
    }

    if (openResponse.status !== 404) {
      const payload = (await openResponse.json().catch(() => ({}))) as { message?: string };
      setError(payload.message ?? "Tisch konnte nicht geladen werden.");
      return;
    }

    const createResponse = await fetch("/api/table-orders/open", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ tableId: table.id, reservationId: null }),
    });

    if (!createResponse.ok) {
      const payload = (await createResponse.json().catch(() => ({}))) as { message?: string };
      setError(payload.message ?? "Tisch konnte nicht geöffnet werden.");
      return;
    }

    const payload = (await createResponse.json()) as TableOrder;
    setOrder(payload);
    setOrderLookupId(String(payload.id));
    setIsModalOpen(true);
    setStatus(`Tisch ${table.name} geöffnet.`);
  }

  async function fetchOrderById(id: string) {
    if (!id.trim()) return;
    setError(null);
    setStatus(null);
    const response = await fetch(`/api/table-orders/${id}`, { cache: "no-store" });
    if (!response.ok) {
      setError("Tischbon wurde nicht gefunden.");
      return;
    }
    const payload = (await response.json()) as TableOrder;
    setOrder(payload);
    setSelectedTableId(String(payload.tableId));
    setIsModalOpen(true);
    setStatus(`Bon #${payload.id} geladen.`);
  }

  async function addItem(drinkVariantId: number, quantity: number) {
    if (!order) return;
    setError(null);
    setStatus(null);
    const response = await fetch(`/api/table-orders/${order.id}/items`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ drinkVariantId, quantity }),
    });
    if (!response.ok) {
      const payload = (await response.json().catch(() => ({}))) as { message?: string };
      setError(payload.message ?? "Position konnte nicht hinzugefügt werden.");
      return;
    }
    const payload = (await response.json()) as TableOrder;
    setOrder(payload);
    setStatus(`${quantity}x Position hinzugefügt.`);
  }

  async function removeItem(itemId: number) {
    if (!order) return;
    setError(null);
    setStatus(null);
    const response = await fetch(`/api/table-orders/${order.id}/items/${itemId}`, { method: "DELETE" });
    if (!response.ok) {
      setError("Position konnte nicht entfernt werden.");
      return;
    }
    const payload = (await response.json()) as TableOrder;
    setOrder(payload);
    setStatus("Position entfernt.");
  }

  async function closeOrder() {
    if (!order) return;
    setError(null);
    setStatus(null);
    const response = await fetch(`/api/table-orders/${order.id}/close`, { method: "POST" });
    if (!response.ok) {
      setError("Bon konnte nicht geschlossen werden.");
      return;
    }
    const payload = (await response.json()) as TableOrder;
    setOrder(payload);
    setStatus(`Bon #${payload.id} abgeschlossen. Betrag ${toCurrency(payload.total)} wurde in die Umsatzauswertung übernommen.`);
    setIsModalOpen(false);
    await loadMeta();
  }

  async function splitPayment(items: SplitPaymentItemRequest[]) {
    if (!order) return;
    if (items.length === 0) {
      setError("Bitte mindestens eine Position für die Teilzahlung auswählen.");
      return;
    }

    setError(null);
    setStatus(null);
    const response = await fetch(`/api/table-orders/${order.id}/split-payment`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ items }),
    });

    if (!response.ok) {
      const payload = (await response.json().catch(() => ({}))) as { message?: string };
      setError(payload.message ?? "Teilzahlung konnte nicht durchgeführt werden.");
      return;
    }

    const payload = (await response.json()) as SplitPaymentResponse;
    setOrder(payload.openOrder);
    setStatus(`Teilzahlung als Bon #${payload.paidOrder.id} erfasst: ${toCurrency(payload.paidOrder.total)}.`);

    if (payload.openOrder.status !== "OPEN") {
      setIsModalOpen(false);
      await loadMeta();
    }
  }

  async function createTable() {
    if (!newTableName.trim()) {
      setError("Bitte einen Tischnamen eingeben.");
      return;
    }
    setError(null);
    setStatus(null);
    setCreatingTable(true);

    const response = await fetch("/api/tables", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: newTableName.trim(),
        area: newTableArea,
        status: "FREE",
        active: true,
      }),
    });

    if (!response.ok) {
      const payload = (await response.json().catch(() => ({}))) as { message?: string; error?: string };
      setError(payload.message || payload.error || "Tisch konnte nicht angelegt werden.");
      setCreatingTable(false);
      return;
    }

    const payload = (await response.json()) as Table;
    setNewTableName("");
    setNewTableArea("INSIDE");
    setStatus(`Tisch ${payload.name} wurde angelegt.`);
    await loadMeta();
    setCreatingTable(false);
  }


  if (state === "loading") {
    return (
      <main className="p-4 text-sm text-[color:var(--color-muted-foreground)] flex items-center gap-2">
        <div className="h-5 w-5 rounded-full border-2 border-cyan-500 border-r-transparent animate-spin"></div>
        Lade Tischabrechnung...
      </main>
    );
  }

  if (state === "error") {
    return (
      <main className="w-full p-4 md:p-6">
        <Card className="border-red-500/40 bg-red-500/10">
          <CardHeader>
            <CardTitle className="text-red-300">Fehler</CardTitle>
            <CardDescription className="text-red-300/70">{error}</CardDescription>
          </CardHeader>
          <CardContent>
            <Button variant="outline" onClick={() => void loadMeta()}>Neu laden</Button>
          </CardContent>
        </Card>
      </main>
    );
  }

  return (
    <main className="dashboard-grid animate-in fade-in duration-500">
      <section className="grid gap-5 md:grid-cols-[0.95fr_1.05fr]">
        <Card className="border-cyan-500/30 bg-gradient-to-br from-cyan-500/10 to-transparent">
          <CardContent className="p-6">
            <p className="text-xs uppercase tracking-[0.2em] font-semibold text-cyan-400">Floor Ops</p>
            <h1 className="mt-3 text-3xl font-bold tracking-tight">Tischabrechnung für dein Team.</h1>
            <p className="mt-3 text-sm leading-6 text-[color:var(--color-muted-foreground)]">
              Offene Tische antippen, direkt ins Tischdetail springen und Bestellungen per Tap erfassen.
            </p>
            <div className="mt-6 grid gap-3 sm:grid-cols-3">
              {[
                { label: "Tische", value: tables.length },
                { label: "Kategorien", value: categories.length },
                { label: "Bon", value: order ? `#${order.id}` : "—" },
              ].map((item) => (
                <div key={item.label} className="rounded-lg border border-cyan-500/30 bg-cyan-500/10 p-4">
                  <p className="text-xs uppercase tracking-[0.2em] font-semibold text-cyan-400">{item.label}</p>
                  <p className="mt-2 text-2xl font-bold">{item.value}</p>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="inline-flex items-center gap-2"><Receipt className="h-4 w-4" />Bon laden</CardTitle>
            <CardDescription>Direkter Zugriff auf offene oder geschlossene Bons via ID.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-2 md:grid-cols-[1fr_auto]">
            <Input value={orderLookupId} onChange={(event) => setOrderLookupId(event.target.value)} placeholder="Order ID" />
            <Button className="md:min-w-32" variant="outline" onClick={() => void fetchOrderById(orderLookupId)}>Laden</Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Neuen Tisch anlegen</CardTitle>
            <CardDescription>Erzeuge eigene Tische/Deckel für Laufkundschaft und freie Plätze.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-3 sm:grid-cols-2">
            <Input
              value={newTableName}
              onChange={(event) => setNewTableName(event.target.value)}
              placeholder="z. B. Terrasse-5"
              className="h-12"
            />
            <select
              value={newTableArea}
              onChange={(event) => setNewTableArea(event.target.value)}
              className="h-12 w-full rounded-lg border border-[color:var(--color-border-strong)] bg-[color:var(--color-surface)]/75 px-4 text-sm"
            >
              <option value="INSIDE">INSIDE</option>
              <option value="OUTSIDE">OUTSIDE</option>
              <option value="BAR">BAR</option>
            </select>
            <Button onClick={() => void createTable()} disabled={creatingTable || !newTableName.trim()} className="sm:col-span-2">
              {creatingTable ? "Lege an..." : "Tisch anlegen"}
            </Button>
          </CardContent>
        </Card>
      </section>

      <Card>
        <CardHeader>
          <CardTitle>Offene Tische</CardTitle>
          <CardDescription>Tippe/Klicke auf einen Tisch, um ihn zu öffnen oder den aktiven Bon zu laden.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-3 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
          {tables.map((table) => (
            <button
              key={table.id}
              type="button"
              onClick={() => void openOrLoadTable(table)}
              className={`min-h-28 rounded-xl border p-4 text-left transition-all active:scale-[0.99] ${selectedTableId === String(table.id)
                ? "border-cyan-500/60 bg-cyan-500/15"
                : "border-cyan-500/20 bg-cyan-500/5 hover:border-cyan-500/40 hover:bg-cyan-500/10"}`}
            >
              <div className="flex items-start justify-between gap-2">
                <div>
                  <p className="text-lg font-semibold text-[color:var(--color-foreground)]">{table.name}</p>
                  <p className="text-xs text-[color:var(--color-muted-foreground)]">{table.area ?? "BEREICH OFFEN"}</p>
                </div>
                <Badge variant={tableStatusVariant(table.status)}>{table.status}</Badge>
              </div>
            </button>
          ))}
        </CardContent>
      </Card>

      <TableDetailModal
        isOpen={isModalOpen}
        order={order}
        categories={categories}
        drinks={drinks}
        variants={variants}
        onClose={() => setIsModalOpen(false)}
        onAddItem={addItem}
        onRemoveItem={removeItem}
        onCloseOrder={closeOrder}
        onSplitPayment={splitPayment}
        error={error}
        status={status}
      />

      {error && !isModalOpen ? <p className="w-full rounded-2xl bg-red-500/15 border border-red-500/30 px-4 py-3 text-sm text-red-100">{error}</p> : null}
    </main>
  );
}

