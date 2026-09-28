"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { PendingMutationNotice } from "@/components/pending-mutation-notice";
import { usePendingMutation } from "@/lib/use-pending-mutation";
import { parseDrinkVariants, parseDrinks, parseDrinkCategories, parseInventoryItems, parseTableOrder, selectSellableCatalog, toCurrency } from "@/features/billing/model";
import type { DrinkVariant, TableOrder } from "@/types/api";

type Line = { variant: DrinkVariant; quantity: number };
export default function DirectSalesClient() {
  const [catalog, setCatalog] = useState<DrinkVariant[]>([]);
  const [cart, setCart] = useState<Line[]>([]);
  const [method, setMethod] = useState<"CASH" | "CARD">("CASH");
  const [receipt, setReceipt] = useState<TableOrder | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const mutation = usePendingMutation("fasswerk-direct-sale-pending-v1");

  async function loadCatalog() {
    setLoading(true);
    try {
      const responses = await Promise.all(["drink-variants", "inventory", "drinks", "drink-categories"].map(path => fetch(`/api/${path}`, { cache: "no-store" })));
      if (responses.some(r => !r.ok)) throw new Error("Getränke konnten nicht geladen werden. Bitte Anmeldung prüfen und erneut laden.");
      const data = await Promise.all(responses.map(r => r.json()));
      const variants = parseDrinkVariants(data[0]), stock = parseInventoryItems(data[1]), drinks = parseDrinks(data[2]), categories = parseDrinkCategories(data[3]);
      if (!variants || !stock || !drinks || !categories) throw new Error("Ungültige Getränkedaten.");
      const selected = selectSellableCatalog(variants, stock, drinks, categories);
      setCatalog(selected.variants.filter(v => selected.drinks.some(d => d.id === v.drinkId)));
    } catch (e) { setError(e instanceof Error ? e.message : "Laden fehlgeschlagen."); }
    finally { setLoading(false); }
  }
  useEffect(() => { void loadCatalog(); }, []);

  function add(variant: DrinkVariant) {
    setReceipt(null);
    setCart(lines => {
      const existing = lines.find(l => l.variant.id === variant.id);
      if (existing) return lines.map(l => l === existing ? { ...l, quantity: Math.min(l.quantity + 1, 1000) } : l);
      return lines.length < 100 ? [...lines, { variant, quantity: 1 }] : lines;
    });
  }
  async function pay(retry = false) {
    setError(null);
    try {
      const result = await mutation.run(retry ? undefined : {
        url: "/api/table-orders/direct", method: "POST", label: `Barverkauf · ${method === "CASH" ? "Bar" : "Karte"}`,
        body: JSON.stringify({ paymentMethod: method, items: cart.map(l => ({ drinkVariantId: l.variant.id, quantity: l.quantity, expectedUnitPrice: l.variant.price })) }),
      }, async response => {
        const order = parseTableOrder(await response.json());
        if (!order || order.saleType !== "DIRECT" || order.status !== "CLOSED" || !order.paid) throw new Error("Abschluss konnte nicht bestätigt werden.");
        return order;
      });
      if (result) { setReceipt(result.value); setCart([]); await loadCatalog(); }
    } catch (e) { setError(e instanceof Error ? e.message : "Buchung fehlgeschlagen."); }
  }
  const blocked = mutation.blocked || loading;
  const total = cart.reduce((sum, l) => sum + Math.round(l.variant.price * 100) * l.quantity, 0) / 100;
  return <main className="mx-auto max-w-5xl space-y-6 p-4 md:p-6">
    <h1 className="text-2xl font-semibold">Barverkauf</h1>
    <p>Getränke auswählen, Zahlung entgegennehmen und Abschluss bestätigen. Ohne Tischbelegung.</p>
    <PendingMutationNotice label={mutation.pending?.label} busy={mutation.busy} error={mutation.initializationError} onRetry={() => void pay(true)} />
    {error && <p role="alert">{error}</p>}
    {receipt && <p role="status" className="rounded-lg border p-4">Bon #{receipt.id} abgeschlossen · {toCurrency(receipt.total)} · {receipt.paymentMethod === "CARD" ? "Karte" : "Bar"}. Bereit für den nächsten Verkauf.</p>}
    <div className="grid gap-6 md:grid-cols-2">
      <section className="space-y-3" aria-label="Getränke">
        <div className="flex items-center justify-between"><h2 className="text-lg font-semibold">Getränke</h2><Button variant="outline" disabled={blocked} onClick={() => void loadCatalog()}>Katalog aktualisieren</Button></div>
        {loading ? <p>Laden …</p> : catalog.length === 0 ? <p>Keine verfügbaren Getränke.</p> : catalog.map(v => <Button className="w-full justify-between" variant="outline" key={v.id} disabled={blocked} onClick={() => add(v)}><span>{v.drinkName} · {v.displayVolumeName}</span><span>{toCurrency(v.price)}</span></Button>)}
      </section>
      <section className="space-y-4 rounded-lg border p-4" aria-label="Warenkorb">
        <h2 className="text-lg font-semibold">Aktueller Verkauf</h2>
        <p className="text-sm">Ungebuchter Entwurf. Bestand und Preise werden beim Abschluss geprüft. Ein Neuladen verwirft den Entwurf; eine bereits gesendete Zahlung bleibt zur Wiederholung gespeichert.</p>
        {cart.length === 0 && <p>Noch keine Positionen.</p>}
        {cart.map(l => <div key={l.variant.id} className="flex items-center justify-between gap-2"><span>{l.quantity} × {l.variant.drinkName} · {l.variant.displayVolumeName} · {toCurrency(l.variant.price)}</span><Button variant="outline" disabled={blocked} aria-label={`${l.variant.drinkName} einmal entfernen`} onClick={() => setCart(lines => lines.flatMap(x => x !== l ? [x] : x.quantity > 1 ? [{ ...x, quantity: x.quantity - 1 }] : []))}>−</Button></div>)}
        <p className="text-lg font-semibold">Summe: {toCurrency(total)}</p>
        <label className="block">Zahlungsart <select className="ml-2 rounded border bg-background p-2" value={method} disabled={blocked} onChange={e => setMethod(e.target.value as "CASH" | "CARD")}><option value="CASH">Bar</option><option value="CARD">Karte</option></select></label>
        <p className="text-sm">Kartenzahlung am Terminal separat ausführen. Hier wird die erhaltene Zahlung erfasst.</p>
        <Button disabled={blocked || cart.length === 0} onClick={() => void pay()}>Zahlung erhalten – abschließen</Button>
        <Button variant="outline" disabled={blocked || cart.length === 0} onClick={() => setCart([])}>Entwurf verwerfen</Button>
        <Button variant="outline" disabled={blocked || cart.length === 0} onClick={() => setCart(lines => lines.map(l => ({ ...l, variant: catalog.find(v => v.id === l.variant.id) ?? l.variant })))}>Aktuelle Katalogpreise übernehmen</Button>
      </section>
    </div>
  </main>;
}
