"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { usePendingMutation } from "@/lib/use-pending-mutation";
import type { MutationCommand } from "@/lib/pending-mutation";
import { PendingMutationNotice } from "@/components/pending-mutation-notice";
import { Receipt } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { TableCapacityEditor } from "@/components/table-capacity-editor";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { TableDetailModal } from "@/components/table-detail-modal";
import { useToastFeedback } from "@/lib/use-toast-feedback";
import { parseJsonResponse, readApiError } from "@/lib/api-client";
import {
  parseDrinkCategories, parseDrinks, parseDrinkVariants, parseInventoryItems,
  parseSplitPayment, parseTable, parseTableOrder, parseTableOrders, parseTables, readCachedOrders,
  selectSellableCatalog, sortByClosedAtDesc, tableStatusVariant, todayIsoDate, toCurrency, toGermanDateLabel,
} from "@/features/billing/model";
import type { Drink, DrinkCategory, DrinkVariant, SplitPaymentItemRequest, Table, TableOrder } from "@/types/api";

type LoadState = "loading" | "ready" | "error";

const UNPAID_ARCHIVE_STORAGE_KEY = "table-billing-unpaid-archive";
const BUSINESS_DATE_STORAGE_KEY = "table-billing-business-date";

export default function TableBillingClient() {
  const router = useRouter();
  const mutation = usePendingMutation("billing-pending-command-v1");
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
    return readCachedOrders(raw);
  });
  const [archiveError, setArchiveError] = useState<string | null>(null);
  const [businessDate, setBusinessDate] = useState(() => {
    if (typeof window === "undefined") return todayIsoDate();
    return window.sessionStorage.getItem(BUSINESS_DATE_STORAGE_KEY) ?? todayIsoDate();
  });
  const actionableTables = tables;

  useToastFeedback(error, "error");
  useToastFeedback(status, "success");

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

      const [tablesPayload, categoriesPayload, drinksPayload, variantsPayload, inventoryPayload] = await Promise.all([
        parseJsonResponse(tablesResponse, parseTables, "Tische laden"),
        parseJsonResponse(categoriesResponse, parseDrinkCategories, "Kategorien laden"),
        parseJsonResponse(drinksResponse, parseDrinks, "Getränke laden"),
        parseJsonResponse(variantsResponse, parseDrinkVariants, "Varianten laden"),
        parseJsonResponse(inventoryResponse, parseInventoryItems, "Bestand laden"),
      ]);
      const catalog = selectSellableCatalog(variantsPayload, inventoryPayload, drinksPayload, categoriesPayload);
      setTables(tablesPayload.filter((table) => table.active));
      setVariants(catalog.variants);
      setDrinks(catalog.drinks);
      setCategories(catalog.categories);
      setState("ready");
    } catch {
      setError("Unerwarteter Fehler beim Laden der Stammdaten.");
      setState("error");
    }
  }, [router]);

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
        const detail = await readApiError(response, `HTTP ${response.status}`);
        setArchiveError(`Archiv konnte nicht geladen werden (${detail}).`);

        const cached = window.sessionStorage.getItem(UNPAID_ARCHIVE_STORAGE_KEY);
        if (cached) {
          setUnpaidArchive(readCachedOrders(cached));
        }
        return;
      }

      const payload = sortByClosedAtDesc(await parseJsonResponse(response, parseTableOrders, "Archiv laden"));
      setUnpaidArchive(payload);
      window.sessionStorage.setItem(UNPAID_ARCHIVE_STORAGE_KEY, JSON.stringify(payload));
      if (targetDate) {
        window.sessionStorage.setItem(BUSINESS_DATE_STORAGE_KEY, targetDate);
      }
    } catch {
      setArchiveError("Archiv konnte nicht geladen werden (Netzwerk/Backend nicht erreichbar).");

      const cached = window.sessionStorage.getItem(UNPAID_ARCHIVE_STORAGE_KEY);
      if (cached) {
        setUnpaidArchive(readCachedOrders(cached));
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
        void loadMeta();
      }
    }

    window.addEventListener("focus", refreshArchiveOnReturn);
    document.addEventListener("visibilitychange", refreshArchiveOnReturn);
    return () => {
      window.removeEventListener("focus", refreshArchiveOnReturn);
      document.removeEventListener("visibilitychange", refreshArchiveOnReturn);
    };
  }, [loadUnpaidArchive, loadMeta]);

  async function openOrLoadTable(table: Table) {
    if (mutation.blocked) { setError("Bitte zuerst die offene Aktion wiederholen."); return; }
    setError(null);
    setStatus(null);
    setSelectedTableId(String(table.id));

    const openResponse = await fetch(`/api/table-orders/open/table/${table.id}`, { cache: "no-store" });
    if (openResponse.ok) {
      const payload = await parseJsonResponse(openResponse, parseTableOrder, "Bon laden");
      setOrder(payload);
      setOrderLookupId(String(payload.id));
      setIsModalOpen(true);
      setStatus(`Tisch ${table.name} geladen (Betriebstag ${toGermanDateLabel(businessDate)}).`);
      return;
    }

    if (openResponse.status !== 404) {
      setError(await readApiError(openResponse, "Tisch konnte nicht geladen werden."));
      return;
    }

    const createResponse = await fetch("/api/table-orders/open", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ tableId: table.id, reservationId: null }),
    });

    if (!createResponse.ok) {
      setError(await readApiError(createResponse, "Tisch konnte nicht geöffnet werden."));
      return;
    }

    const payload = await parseJsonResponse(createResponse, parseTableOrder, "Tisch öffnen");
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
    const payload = await parseJsonResponse(response, parseTableOrder, "Bon verarbeiten");
    setOrder(payload);
    setSelectedTableId(String(payload.tableId));
    setIsModalOpen(true);
    setStatus(`Bon #${payload.id} geladen.`);
  }

  async function runBillingCommand(command?: MutationCommand) {
    setError(null);
    setStatus(null);
    try {
      const result = await mutation.run(command, async (response, sent) => {
        if (sent.url.endsWith("/split-payment")) {
          const split = await parseJsonResponse(response, parseSplitPayment, "Teilzahlung");
          return { current: split.openOrder, paid: split.paidOrder };
        }
        return { current: await parseJsonResponse(response, parseTableOrder, "Bon verarbeiten"), paid: null };
      });
      if (!result) return;
      const { current, paid } = result.value;
      setOrderLookupId(String(current.id));
      setOrder(current.status === "OPEN" ? current : null);
      setSelectedTableId(current.status === "OPEN" ? String(current.tableId) : "");
      setIsModalOpen(current.status === "OPEN");
      setStatus(paid
        ? `Teilzahlung als Bon #${paid.id} erfasst: ${toCurrency(paid.total)}.`
        : `${result.command.label} bestätigt. Bon #${current.id}: ${current.status === "OPEN" ? "offen" : current.paid ? "bezahlt" : "unbezahlt archiviert"}.`);
      await loadMeta();
      await loadUnpaidArchive(businessDate);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Aktion konnte nicht bestätigt werden.");
    }
  }

  async function addItem(drinkVariantId: number, quantity: number) {
    if (!order) return;
    await runBillingCommand({ url: `/api/table-orders/${order.id}/items`, method: "POST",
      body: JSON.stringify({ drinkVariantId, quantity }), label: `${quantity}x Position hinzufügen · Bon #${order.id}` });
  }
  async function removeItem(itemId: number) {
    if (!order) return;
    await runBillingCommand({ url: `/api/table-orders/${order.id}/items/${itemId}`, method: "DELETE", label: `Position entfernen · Bon #${order.id}` });
  }
  async function closeOrder() {
    if (!order) return;
    await runBillingCommand({ url: `/api/table-orders/${order.id}/close`, method: "POST", label: `Bezahlung · Bon #${order.id}` });
  }
  async function markOrderUnpaid() {
    if (!order) return;
    await runBillingCommand({ url: `/api/table-orders/${order.id}/mark-unpaid`, method: "POST", label: `Zurückstellen · Bon #${order.id}` });
  }
  async function reopenUnpaidOrder() {
    if (order) await reopenUnpaidOrderById(order.id);
  }
  async function reopenUnpaidOrderById(orderId: number) {
    await runBillingCommand({ url: `/api/table-orders/${orderId}/reopen-unpaid`, method: "POST", label: `Wieder öffnen · Bon #${orderId}` });
  }
  async function splitPayment(items: SplitPaymentItemRequest[]) {
    if (!order || items.length === 0) return;
    await runBillingCommand({ url: `/api/table-orders/${order.id}/split-payment`, method: "POST",
      body: JSON.stringify({ items: [...items].sort((a, b) => a.itemId - b.itemId) }), label: `Teilzahlung · Bon #${order.id}` });
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
      setError(await readApiError(response, "Tisch konnte nicht angelegt werden."));
      setCreatingTable(false);
      return;
    }

    const payload = await parseJsonResponse(response, parseTable, "Tisch anlegen");
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
                    <Button size="sm" disabled={mutation.blocked} onClick={() => void reopenUnpaidOrderById(entry.id)}>
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

      <TableCapacityEditor tables={tables} onSaved={loadMeta} />

      <Card>
        <CardHeader>
          <CardTitle>Aktive Tische</CardTitle>
          <CardDescription>Freie Tische koennen erneut geoeffnet werden. Laufende Bons halten den Tisch belegt. Bezahlen oder einen Deckel unbezahlt archivieren gibt den Tisch frei; der offene Betrag bleibt im Archiv.</CardDescription>
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

      {!isModalOpen && <PendingMutationNotice label={mutation.pending?.label} busy={mutation.busy}
        error={mutation.initializationError} onRetry={() => void runBillingCommand()} />}
      <TableDetailModal
        mutationBlocked={mutation.blocked}
        recovery={<PendingMutationNotice label={mutation.pending?.label} busy={mutation.busy}
          error={mutation.initializationError} onRetry={() => void runBillingCommand()} />}
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

