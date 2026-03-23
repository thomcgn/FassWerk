"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowRightLeft, Beer, Plus, Receipt, Trash2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select } from "@/components/ui/select";
import type { DrinkVariant, Table, TableOrder } from "@/types/api";

type LoadState = "loading" | "ready" | "error";

function toCurrency(value: string): string {
  const amount = Number(value);
  if (Number.isNaN(amount)) {
    return `${value} EUR`;
  }
  return new Intl.NumberFormat("de-DE", { style: "currency", currency: "EUR" }).format(amount);
}

export default function TableBillingClient() {
  const router = useRouter();
  const [state, setState] = useState<LoadState>("loading");
  const [tables, setTables] = useState<Table[]>([]);
  const [variants, setVariants] = useState<DrinkVariant[]>([]);
  const [order, setOrder] = useState<TableOrder | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [tableId, setTableId] = useState<string>("");
  const [reservationId, setReservationId] = useState<string>("");
  const [orderLookupId, setOrderLookupId] = useState<string>("");
  const [variantId, setVariantId] = useState<string>("");
  const [quantity, setQuantity] = useState<string>("1");

  const loadMeta = useCallback(async () => {
    try {
      const [tablesResponse, variantsResponse] = await Promise.all([
        fetch("/api/tables", { cache: "no-store" }),
        fetch("/api/drink-variants", { cache: "no-store" }),
      ]);

      if ([tablesResponse.status, variantsResponse.status].some((code) => code === 401 || code === 403)) {
        router.replace("/login");
        return;
      }

      if (!tablesResponse.ok || !variantsResponse.ok) {
        setError("Stammdaten fuer Tischabrechnung konnten nicht geladen werden.");
        setState("error");
        return;
      }

      const [tablesPayload, variantsPayload] = (await Promise.all([
        tablesResponse.json(),
        variantsResponse.json(),
      ])) as [Table[], DrinkVariant[]];

      setTables(tablesPayload.filter((table) => table.active));
      setVariants(variantsPayload.filter((variant) => variant.active));
      setTableId((current) => current || String(tablesPayload[0]?.id ?? ""));
      setVariantId((current) => current || String(variantsPayload[0]?.id ?? ""));
      setState("ready");
    } catch {
      setError("Unerwarteter Fehler beim Laden der Stammdaten.");
      setState("error");
    }
  }, [router]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadMeta();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [loadMeta]);

  async function openOrder() {
    setError(null);
    const response = await fetch("/api/table-orders/open", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        tableId: Number(tableId),
        reservationId: reservationId.trim() ? Number(reservationId) : null,
      }),
    });

    if (!response.ok) {
      setError("Tischbon konnte nicht geoeffnet werden.");
      return;
    }

    const payload = (await response.json()) as TableOrder;
    setOrder(payload);
    setOrderLookupId(String(payload.id));
  }

  async function fetchOrderById(id: string) {
    if (!id.trim()) return;
    setError(null);
    const response = await fetch(`/api/table-orders/${id}`, { cache: "no-store" });
    if (!response.ok) {
      setError("Tischbon wurde nicht gefunden.");
      return;
    }
    const payload = (await response.json()) as TableOrder;
    setOrder(payload);
  }

  async function addItem() {
    if (!order) return;
    setError(null);
    const response = await fetch(`/api/table-orders/${order.id}/items`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ drinkVariantId: Number(variantId), quantity: Number(quantity) }),
    });

    if (!response.ok) {
      setError("Position konnte nicht hinzugefuegt werden.");
      return;
    }

    const payload = (await response.json()) as TableOrder;
    setOrder(payload);
  }

  async function removeItem(itemId: number) {
    if (!order) return;
    setError(null);
    const response = await fetch(`/api/table-orders/${order.id}/items/${itemId}`, {
      method: "DELETE",
    });

    if (!response.ok) {
      setError("Position konnte nicht entfernt werden.");
      return;
    }

    const payload = (await response.json()) as TableOrder;
    setOrder(payload);
  }

  async function closeOrder() {
    if (!order) return;
    setError(null);
    const response = await fetch(`/api/table-orders/${order.id}/close`, { method: "POST" });
    if (!response.ok) {
      setError("Bon konnte nicht geschlossen werden.");
      return;
    }
    const payload = (await response.json()) as TableOrder;
    setOrder(payload);
  }

  const canEditOrder = useMemo(() => order?.status === "OPEN", [order]);

  if (state === "loading") {
    return <main className="p-4 md:p-6">Lade Tischabrechnung...</main>;
  }

  if (state === "error") {
    return (
      <main className="mx-auto w-full max-w-3xl p-4 md:p-6">
        <Card className="border-red-200 bg-red-50">
          <CardHeader>
            <CardTitle className="text-red-700">Fehler</CardTitle>
            <CardDescription className="text-red-700">{error}</CardDescription>
          </CardHeader>
          <CardContent>
            <Button variant="outline" onClick={() => void loadMeta()}>Neu laden</Button>
          </CardContent>
        </Card>
      </main>
    );
  }

  return (
    <main className="mx-auto w-full max-w-7xl p-3 sm:p-4 md:p-6">
      <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-zinc-900 sm:text-3xl">Tischabrechnung</h1>
          <p className="text-sm text-zinc-600">Touch-freundlich fuer iPhone 12 mini und iPad 10.5.</p>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-[340px_1fr]">
        <div className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle className="inline-flex items-center gap-2"><Receipt className="h-4 w-4" />Bon oeffnen</CardTitle>
              <CardDescription>Neuen Bon fuer Tisch starten.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="space-y-1">
                <Label htmlFor="tableId">Tisch</Label>
                <Select id="tableId" value={tableId} onChange={(event) => setTableId(event.target.value)}>
                  {tables.map((table) => (
                    <option key={table.id} value={String(table.id)}>
                      {table.name} ({table.capacity} Pers.)
                    </option>
                  ))}
                </Select>
              </div>
              <div className="space-y-1">
                <Label htmlFor="reservationId">Reservierung-ID (optional)</Label>
                <Input
                  id="reservationId"
                  value={reservationId}
                  onChange={(event) => setReservationId(event.target.value)}
                  placeholder="z. B. 42"
                />
              </div>
              <Button onClick={() => void openOrder()} className="w-full" disabled={!tableId}>
                <ArrowRightLeft className="h-4 w-4" />Bon starten
              </Button>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Bon laden</CardTitle>
              <CardDescription>Bereits geoeffneten Bon per ID holen.</CardDescription>
            </CardHeader>
            <CardContent className="flex gap-2">
              <Input
                value={orderLookupId}
                onChange={(event) => setOrderLookupId(event.target.value)}
                placeholder="Order ID"
              />
              <Button variant="secondary" onClick={() => void fetchOrderById(orderLookupId)}>
                Laden
              </Button>
            </CardContent>
          </Card>
        </div>

        <Card>
          <CardHeader>
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <CardTitle>Aktueller Bon</CardTitle>
                <CardDescription>
                  {order ? `Tisch ${order.tableName} · #${order.id}` : "Noch kein Bon ausgewaehlt"}
                </CardDescription>
              </div>
              {order ? (
                <Badge variant={order.status === "OPEN" ? "warning" : "success"}>{order.status}</Badge>
              ) : null}
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            {order ? (
              <>
                <div className="grid gap-2 sm:grid-cols-[1fr_110px_140px]">
                  <Select value={variantId} onChange={(event) => setVariantId(event.target.value)} disabled={!canEditOrder}>
                    {variants.map((variant) => (
                      <option key={variant.id} value={String(variant.id)}>
                        {variant.drinkName} · {variant.displayVolumeName} ({toCurrency(variant.price)})
                      </option>
                    ))}
                  </Select>
                  <Input
                    type="number"
                    min={1}
                    value={quantity}
                    onChange={(event) => setQuantity(event.target.value)}
                    disabled={!canEditOrder}
                  />
                  <Button onClick={() => void addItem()} disabled={!canEditOrder || !variantId}>
                    <Plus className="h-4 w-4" />Hinzufuegen
                  </Button>
                </div>

                <div className="space-y-2">
                  {order.items.length === 0 ? (
                    <p className="rounded-md border border-zinc-200 p-3 text-sm text-zinc-600">Keine Positionen auf dem Bon.</p>
                  ) : (
                    order.items.map((item) => (
                      <div key={item.id} className="rounded-lg border border-zinc-200 p-3">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <p className="inline-flex items-center gap-2 text-sm font-semibold text-zinc-900">
                            <Beer className="h-4 w-4 text-zinc-500" />
                            {item.drinkLabel}
                          </p>
                          <p className="text-sm font-semibold">{toCurrency(item.totalPrice)}</p>
                        </div>
                        <p className="mt-1 text-xs text-zinc-600">
                          {item.quantity} x {toCurrency(item.unitPrice)} · Verbrauch {item.deductedVolumeMl} ml
                        </p>
                        <div className="mt-2">
                          <Button
                            size="sm"
                            variant="ghost"
                            disabled={!canEditOrder}
                            onClick={() => void removeItem(item.id)}
                          >
                            <Trash2 className="h-4 w-4" />Entfernen
                          </Button>
                        </div>
                      </div>
                    ))
                  )}
                </div>

                <div className="flex flex-col gap-2 rounded-md bg-zinc-50 p-3 sm:flex-row sm:items-center sm:justify-between">
                  <p className="text-base font-semibold">Gesamt: {toCurrency(order.total)}</p>
                  <Button onClick={() => void closeOrder()} disabled={!canEditOrder}>
                    Bon schliessen
                  </Button>
                </div>
              </>
            ) : (
              <p className="rounded-md border border-zinc-200 p-3 text-sm text-zinc-600">
                Oeffne einen Bon oder lade eine Order-ID, um Positionen zu verwalten.
              </p>
            )}

            {error ? <p className="text-sm text-red-600">{error}</p> : null}
          </CardContent>
        </Card>
      </div>
    </main>
  );
}

