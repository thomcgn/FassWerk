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

const UNPAID_ARCHIVE_STORAGE_KEY = "table-billing-unpaid-archive";
const BUSINESS_DATE_STORAGE_KEY = "table-billing-business-date";

function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10);
}

function toGermanDateLabel(value: string): string {
  if (!value) return "";
  const parsed = new Date(`${value}T00:00:00`);
  if (Number.isNaN(parsed.getTime())) return value;
  return new Intl.DateTimeFormat("de-DE", { dateStyle: "medium" }).format(parsed);
}

function toCurrency(value: string): string {
  const amount = Number(value);
  if (Number.isNaN(amount)) return `${value} EUR`;
  return new Intl.NumberFormat("de-DE", { style: "currency", currency: "EUR" }).format(amount);
}

function matchesBusinessDate(closedAt: string | null, businessDate: string): boolean {
  if (!closedAt || !businessDate) return false;
  return closedAt.slice(0, 10) === businessDate;
}

function sortByClosedAtDesc(entries: TableOrder[]): TableOrder[] {
  return [...entries].sort((a, b) => {
    const aTime = a.closedAt ? new Date(a.closedAt).getTime() : 0;
    const bTime = b.closedAt ? new Date(b.closedAt).getTime() : 0;
    return bTime - aTime;
  });
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
  const [unpaidArchive, setUnpaidArchive] = useState<TableOrder[]>(() => {
    if (typeof window === "undefined") return [];
    const raw = window.sessionStorage.getItem(UNPAID_ARCHIVE_STORAGE_KEY);
    if (!raw) return [];
    try {
      const parsed = JSON.parse(raw) as TableOrder[];
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  });
  const [archiveError, setArchiveError] = useState<string | null>(null);
  const [businessDate, setBusinessDate] = useState(() => {
    if (typeof window === "undefined") return todayIsoDate();
    return window.sessionStorage.getItem(BUSINESS_DATE_STORAGE_KEY) ?? todayIsoDate();
  });
  const actionableTables = tables.filter((table) => table.status !== "FREE");

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

  const loadUnpaidArchive = useCallback(async (dateOverride?: string) => {
    try {
      setArchiveError(null);
      const targetDate = dateOverride ?? businessDate;
      const query = new URLSearchParams({ payment: "UNPAID" });
      const response = await fetch(`/api/table-orders/archive?${query.toString()}`, { cache: "no-store" });

      if (response.status === 401 || response.status === 403) {
        router.replace("/login");
        return;
      }

      if (!response.ok) {
        const payload = (await response.json().catch(() => ({}))) as { message?: string; error?: string };
        const detail = payload.message ?? payload.error ?? `HTTP ${response.status}`;
        setArchiveError(`Archiv konnte nicht geladen werden (${detail}).`);

        const cached = window.sessionStorage.getItem(UNPAID_ARCHIVE_STORAGE_KEY);
        if (cached) {
          try {
            const parsed = JSON.parse(cached) as TableOrder[];
            setUnpaidArchive(Array.isArray(parsed) ? parsed : []);
          } catch {
            // ignore invalid cache payload
          }
        }
        return;
      }

      const payload = sortByClosedAtDesc((await response.json()) as TableOrder[]);
      setUnpaidArchive(payload);
      window.sessionStorage.setItem(UNPAID_ARCHIVE_STORAGE_KEY, JSON.stringify(payload));
      if (targetDate) {
        window.sessionStorage.setItem(BUSINESS_DATE_STORAGE_KEY, targetDate);
      }
    } catch {
      setArchiveError("Archiv konnte nicht geladen werden (Netzwerk/Backend nicht erreichbar).");

      const cached = window.sessionStorage.getItem(UNPAID_ARCHIVE_STORAGE_KEY);
      if (cached) {
        try {
          const parsed = JSON.parse(cached) as TableOrder[];
          setUnpaidArchive(Array.isArray(parsed) ? parsed : []);
        } catch {
          // ignore invalid cache payload
        }
      }
    }
  }, [businessDate, router]);

  useEffect(() => {
    window.sessionStorage.setItem(BUSINESS_DATE_STORAGE_KEY, businessDate);
  }, [businessDate]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadMeta();
      void loadUnpaidArchive();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [loadMeta, loadUnpaidArchive]);

  useEffect(() => {
    function refreshArchiveOnReturn() {
      if (document.visibilityState === "visible") {
        void loadUnpaidArchive();
      }
    }

    window.addEventListener("focus", refreshArchiveOnReturn);
    document.addEventListener("visibilitychange", refreshArchiveOnReturn);
    return () => {
      window.removeEventListener("focus", refreshArchiveOnReturn);
      document.removeEventListener("visibilitychange", refreshArchiveOnReturn);
    };
  }, [loadUnpaidArchive]);

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
      setStatus(`Tisch ${table.name} geladen (Betriebstag ${toGermanDateLabel(businessDate)}).`);
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
    setStatus(`Tisch ${table.name} geöffnet (Betriebstag ${toGermanDateLabel(businessDate)}).`);
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
      setError("Bezahlung konnte nicht abgeschlossen werden.");
      return;
    }
    const payload = (await response.json()) as TableOrder;
    setOrder(null);
    setSelectedTableId("");
    setStatus(`Bon #${payload.id} bezahlt. Betrag ${toCurrency(payload.total)} wurde in die Umsatzauswertung uebernommen.`);
    setIsModalOpen(false);
    await loadMeta();
    await loadUnpaidArchive(businessDate);
  }

  async function markOrderUnpaid() {
    if (!order) return;
    setError(null);
    setStatus(null);

    const response = await fetch(`/api/table-orders/${order.id}/mark-unpaid`, { method: "POST" });
    if (!response.ok) {
      const payload = (await response.json().catch(() => ({}))) as { message?: string };
      setError(payload.message ?? "Bon konnte nicht zurückgestellt werden.");
      return;
    }

    const payload = (await response.json()) as TableOrder;
    if (matchesBusinessDate(payload.closedAt, businessDate)) {
      setUnpaidArchive((current) => [payload, ...current.filter((entry) => entry.id !== payload.id)]);
    }
    setOrder(null);
    setSelectedTableId("");
    setStatus(`Bon #${payload.id} als unbezahlt zurückgestellt und ins Archiv verschoben.`);
    setIsModalOpen(false);
    await loadMeta();
    await loadUnpaidArchive(businessDate);
  }

  async function reopenUnpaidOrder() {
    if (!order) return;
    setError(null);
    setStatus(null);

    const response = await fetch(`/api/table-orders/${order.id}/reopen-unpaid`, { method: "POST" });
    if (!response.ok) {
      const payload = (await response.json().catch(() => ({}))) as { message?: string };
      setError(payload.message ?? "Bon konnte nicht wieder geoeffnet werden.");
      return;
    }

    const payload = (await response.json()) as TableOrder;
    setOrder(payload);
    setSelectedTableId(String(payload.tableId));
    setStatus(`Bon #${payload.id} wurde wieder geoeffnet und kann jetzt bezahlt werden.`);
    await loadMeta();
    await loadUnpaidArchive(businessDate);
  }

  async function reopenUnpaidOrderById(orderId: number) {
    setError(null);
    setStatus(null);

    const response = await fetch(`/api/table-orders/${orderId}/reopen-unpaid`, { method: "POST" });
    if (!response.ok) {
      const payload = (await response.json().catch(() => ({}))) as { message?: string };
      setError(payload.message ?? "Bon konnte nicht wieder geoeffnet werden.");
      return;
    }

    const payload = (await response.json()) as TableOrder;
    setOrder(payload);
    setOrderLookupId(String(payload.id));
    setSelectedTableId(String(payload.tableId));
    setIsModalOpen(true);
    setStatus(`Bon #${payload.id} wurde aus dem Archiv wieder geoeffnet.`);
    await loadMeta();
    await loadUnpaidArchive(businessDate);
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
      setOrder(null);
      setSelectedTableId("");
      setIsModalOpen(false);
      await loadMeta();
      await loadUnpaidArchive(businessDate);
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
        status: "OCCUPIED",
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
    setStatus(`Tisch ${payload.name} wurde für ${toGermanDateLabel(businessDate)} angelegt.`);
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
            <div className="mt-6 grid gap-3 sm:grid-cols-4">
              {[
                { label: "Aktive Tische", value: actionableTables.length },
                { label: "Kategorien", value: categories.length },
                { label: "Bon", value: order ? `#${order.id}` : "—" },
                { label: "Betriebstag", value: toGermanDateLabel(businessDate) },
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
            <CardTitle>Archiv: Unbezahlt</CardTitle>
            <CardDescription>Zurueckgestellte Bons koennen hier erneut geladen werden.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="grid gap-2 sm:grid-cols-[1fr_auto_auto]">
              <Input
                type="date"
                value={businessDate}
                onChange={(event) => setBusinessDate(event.target.value)}
                aria-label="Archivdatum"
              />
              <Button variant="outline" onClick={() => {
                const today = todayIsoDate();
                setBusinessDate(today);
                void loadUnpaidArchive(today);
              }}>Heute</Button>
              <Button variant="outline" onClick={() => void loadUnpaidArchive(businessDate)}>Aktualisieren</Button>
            </div>
            {archiveError ? <p className="text-sm text-red-300">{archiveError}</p> : null}
            {unpaidArchive.length === 0 ? (
              <p className="text-sm text-[color:var(--color-muted-foreground)]">Keine unbezahlten Bons im Archiv.</p>
            ) : (
              <div className="max-h-96 space-y-2 overflow-y-auto pr-1">
                {unpaidArchive.map((entry) => (
                <div key={entry.id} className="flex items-center justify-between gap-2 rounded-lg border border-white/10 bg-white/5 p-3">
                  <div>
                    <p className="text-sm font-semibold">Bon #{entry.id} · {entry.tableName}</p>
                    <p className="text-xs text-[color:var(--color-muted-foreground)]">
                      {toCurrency(entry.total)}{entry.closedAt ? ` · ${new Date(entry.closedAt).toLocaleString("de-DE")}` : ""}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Button variant="outline" size="sm" onClick={() => void fetchOrderById(String(entry.id))}>
                      Bon #{entry.id} laden
                    </Button>
                    <Button size="sm" onClick={() => void reopenUnpaidOrderById(entry.id)}>
                      Wieder oeffnen
                    </Button>
                  </div>
                </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Neuen Tisch anlegen</CardTitle>
            <CardDescription>Erzeuge eigene Tische/Deckel für Laufkundschaft und freie Plätze (Betriebstag {toGermanDateLabel(businessDate)}).</CardDescription>
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
          <CardTitle>Aktive Tische</CardTitle>
          <CardDescription>Es werden nur Tische mit aktivem Vorgang angezeigt; bezahlte oder zurückgestellte Bons verschwinden aus dieser Übersicht.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-3 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
          {actionableTables.map((table) => (
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
          {actionableTables.length === 0 ? (
            <p className="text-sm text-[color:var(--color-muted-foreground)] sm:col-span-2 md:col-span-3 lg:col-span-4">
              Aktuell keine aktiven Tische. Lege bei Bedarf einen neuen Tisch an.
            </p>
          ) : null}
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
        onMarkUnpaidOrder={markOrderUnpaid}
        onReopenUnpaidOrder={reopenUnpaidOrder}
        onSplitPayment={splitPayment}
        error={error}
        status={status}
      />

      {error && !isModalOpen ? <p className="w-full rounded-2xl bg-red-500/15 border border-red-500/30 px-4 py-3 text-sm text-red-100">{error}</p> : null}
    </main>
  );
}

