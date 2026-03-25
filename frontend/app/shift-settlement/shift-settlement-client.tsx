"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useToastFeedback } from "@/lib/use-toast-feedback";
import type { ShiftSettlement, ShiftSettlementUpsertRequest, ShiftWorkerEntry } from "@/types/api";

type LoadState = "loading" | "ready" | "error";

type WorkerEntryForm = {
  key: string;
  employeeName: string;
  shiftStart: string;
  shiftEnd: string;
  hourlyWage: string;
};

type HistoryPreset = "WEEK" | "MONTH";

function formatCurrency(value: string): string {
  const amount = Number(value);
  if (!Number.isFinite(amount)) return `${value} EUR`;
  return new Intl.NumberFormat("de-DE", { style: "currency", currency: "EUR" }).format(amount);
}

function normalizeTimeInput(value: string): string {
  if (!value) return "";
  return value.slice(0, 5);
}

function todayDateIso(): string {
  return new Date().toISOString().slice(0, 10);
}

function toIsoDate(value: Date): string {
  return value.toISOString().slice(0, 10);
}

function resolveHistoryRange(baseDateIso: string, preset: HistoryPreset): { from: string; to: string } {
  const [year, month, day] = baseDateIso.split("-").map(Number);
  const baseDate = new Date(Date.UTC(year, month - 1, day));

  if (preset === "MONTH") {
    const from = new Date(Date.UTC(baseDate.getUTCFullYear(), baseDate.getUTCMonth(), 1));
    const to = new Date(Date.UTC(baseDate.getUTCFullYear(), baseDate.getUTCMonth() + 1, 0));
    return { from: toIsoDate(from), to: toIsoDate(to) };
  }

  const weekday = baseDate.getUTCDay();
  const deltaToMonday = (weekday + 6) % 7;
  const from = new Date(baseDate);
  from.setUTCDate(baseDate.getUTCDate() - deltaToMonday);
  const to = new Date(from);
  to.setUTCDate(from.getUTCDate() + 6);
  return { from: toIsoDate(from), to: toIsoDate(to) };
}

function toFormEntries(entries: ShiftWorkerEntry[]): WorkerEntryForm[] {
  if (entries.length === 0) {
    return [{ key: crypto.randomUUID(), employeeName: "", shiftStart: "18:00", shiftEnd: "23:00", hourlyWage: "15.00" }];
  }
  return entries.map((entry) => ({
    key: entry.id != null ? `existing-${entry.id}` : crypto.randomUUID(),
    employeeName: entry.employeeName,
    shiftStart: normalizeTimeInput(entry.shiftStart),
    shiftEnd: normalizeTimeInput(entry.shiftEnd),
    hourlyWage: entry.hourlyWage,
  }));
}

export default function ShiftSettlementClient() {
  const router = useRouter();
  const [state, setState] = useState<LoadState>("loading");
  const [selectedDate, setSelectedDate] = useState(todayDateIso());
  const [settlement, setSettlement] = useState<ShiftSettlement | null>(null);
  const [openingCash, setOpeningCash] = useState("0.00");
  const [otherExpenses, setOtherExpenses] = useState("0.00");
  const [entries, setEntries] = useState<WorkerEntryForm[]>([]);
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [historyPreset, setHistoryPreset] = useState<HistoryPreset>("WEEK");
  const [historyEntries, setHistoryEntries] = useState<ShiftSettlement[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);

  useToastFeedback(error, "error");
  useToastFeedback(status, "success");

  const loadSettlement = useCallback(async (date: string) => {
    setError(null);
    setStatus(null);
    setState("loading");
    try {
      const response = await fetch(`/api/shift-settlements/${date}`, { cache: "no-store" });
      if (response.status === 401) {
        router.replace("/login");
        return;
      }
      if (!response.ok) {
        setError("Schichtabrechnung konnte nicht geladen werden.");
        setState("error");
        return;
      }

      const payload = (await response.json()) as ShiftSettlement;
      setSettlement(payload);
      setOpeningCash(payload.openingCash);
      setOtherExpenses(payload.otherExpenses);
      setEntries(toFormEntries(payload.entries));
      setState("ready");
    } catch {
      setError("Unerwarteter Fehler beim Laden der Schichtabrechnung.");
      setState("error");
    }
  }, [router]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadSettlement(selectedDate);
    }, 0);
    return () => window.clearTimeout(timer);
  }, [selectedDate, loadSettlement]);

  const loadHistory = useCallback(async (date: string, preset: HistoryPreset) => {
    const range = resolveHistoryRange(date, preset);
    setHistoryLoading(true);
    try {
      const response = await fetch(`/api/shift-settlements?from=${range.from}&to=${range.to}`, { cache: "no-store" });
      if (!response.ok) {
        setHistoryEntries([]);
        setHistoryLoading(false);
        return;
      }
      const payload = (await response.json()) as ShiftSettlement[];
      setHistoryEntries(payload);
      setHistoryLoading(false);
    } catch {
      setHistoryEntries([]);
      setHistoryLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadHistory(selectedDate, historyPreset);
    }, 0);
    return () => window.clearTimeout(timer);
  }, [selectedDate, historyPreset, loadHistory]);

  function addEntry() {
    setEntries((current) => [
      ...current,
      { key: crypto.randomUUID(), employeeName: "", shiftStart: "18:00", shiftEnd: "23:00", hourlyWage: "15.00" },
    ]);
  }

  function removeEntry(key: string) {
    setEntries((current) => current.filter((entry) => entry.key !== key));
  }

  async function saveSettlement() {
    setError(null);
    setStatus(null);

    const opening = Number(openingCash.replace(",", "."));
    const expenses = Number(otherExpenses.replace(",", "."));
    if (!Number.isFinite(opening) || opening < 0) {
      setError("Bitte einen gültigen Kassenbestand zu Beginn eingeben.");
      return;
    }
    if (!Number.isFinite(expenses) || expenses < 0) {
      setError("Bitte gültige sonstige Ausgaben eingeben.");
      return;
    }

    const upsertEntries = entries
      .map((entry) => ({
        employeeName: entry.employeeName.trim(),
        shiftStart: entry.shiftStart,
        shiftEnd: entry.shiftEnd,
        hourlyWage: Number(entry.hourlyWage.replace(",", ".")),
      }))
      .filter((entry) => entry.employeeName && entry.shiftStart && entry.shiftEnd);

    if (upsertEntries.some((entry) => !Number.isFinite(entry.hourlyWage) || entry.hourlyWage < 0)) {
      setError("Bitte gültige Stundensätze für alle Schichtpositionen eingeben.");
      return;
    }

    const payload: ShiftSettlementUpsertRequest = {
      openingCash: opening,
      otherExpenses: expenses,
      entries: upsertEntries,
    };

    setSaving(true);
    const response = await fetch(`/api/shift-settlements/${selectedDate}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    setSaving(false);

    if (!response.ok) {
      const body = (await response.json().catch(() => ({}))) as { message?: string; error?: string };
      setError(body.message || body.error || "Schichtabrechnung konnte nicht gespeichert werden.");
      return;
    }

    const saved = (await response.json()) as ShiftSettlement;
    setSettlement(saved);
    setOpeningCash(saved.openingCash);
    setOtherExpenses(saved.otherExpenses);
    setEntries(toFormEntries(saved.entries));
    setStatus("Schichtabrechnung wurde gespeichert.");
  }

  const totals = useMemo(() => {
    if (!settlement) {
      return {
        revenue: "0.00",
        wages: "0.00",
        expectedCash: "0.00",
      };
    }
    return {
      revenue: settlement.dailyRevenue,
      wages: settlement.totalWages,
      expectedCash: settlement.expectedClosingCash,
    };
  }, [settlement]);

  if (state === "loading") {
    return <main className="p-6 text-sm text-[color:var(--color-muted-foreground)]">Lade Schichtabrechnung...</main>;
  }

  if (state === "error") {
    return (
      <main className="mx-auto w-full max-w-3xl p-4 md:p-6">
        <Card className="border-red-500/40 bg-red-500/10">
          <CardHeader>
            <CardTitle className="text-red-300">Fehler</CardTitle>
            <CardDescription className="text-red-300/70">{error}</CardDescription>
          </CardHeader>
          <CardContent>
            <Button variant="outline" onClick={() => void loadSettlement(selectedDate)}>Neu laden</Button>
          </CardContent>
        </Card>
      </main>
    );
  }

  return (
    <main className="dashboard-grid animate-in fade-in duration-500">
      <Card className="border-cyan-500/20">
        <CardHeader>
          <CardTitle>Löhne & Schichtabrechnung</CardTitle>
          <CardDescription>
            Schichten mit von/bis, Stundensatz, sonstige Ausgaben und Kassenstart erfassen. Erwarteter Kassenbestand = Kassenstand Beginn + Umsatz - Löhne - Sonstige Ausgaben.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-3">
          <div className="space-y-2">
            <Label htmlFor="settlement-date">Datum</Label>
            <Input
              id="settlement-date"
              type="date"
              value={selectedDate}
              onChange={(event) => setSelectedDate(event.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="opening-cash">Kassenbestand zu Beginn (EUR)</Label>
            <Input
              id="opening-cash"
              type="number"
              min="0"
              step="0.01"
              value={openingCash}
              onChange={(event) => setOpeningCash(event.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="other-expenses">Sonstige Ausgaben (EUR)</Label>
            <Input
              id="other-expenses"
              type="number"
              min="0"
              step="0.01"
              value={otherExpenses}
              onChange={(event) => setOtherExpenses(event.target.value)}
            />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Schichtmitarbeiter</CardTitle>
          <CardDescription>Wer hat in der Schicht gearbeitet und zu welchem Stundensatz?</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {entries.map((entry, index) => (
            <div key={entry.key} className="grid gap-3 rounded-xl border border-cyan-500/20 bg-cyan-500/5 p-3 md:grid-cols-[1.4fr_0.8fr_0.8fr_0.8fr_auto]">
              <div className="space-y-1.5">
                <Label>Name</Label>
                <Input
                  value={entry.employeeName}
                  onChange={(event) => {
                    const value = event.target.value;
                    setEntries((current) => current.map((row) => row.key === entry.key ? { ...row, employeeName: value } : row));
                  }}
                  placeholder="Mitarbeitername"
                />
              </div>
              <div className="space-y-1.5">
                <Label>Von</Label>
                <Input
                  type="time"
                  value={entry.shiftStart}
                  onChange={(event) => {
                    const value = event.target.value;
                    setEntries((current) => current.map((row) => row.key === entry.key ? { ...row, shiftStart: value } : row));
                  }}
                />
              </div>
              <div className="space-y-1.5">
                <Label>Bis</Label>
                <Input
                  type="time"
                  value={entry.shiftEnd}
                  onChange={(event) => {
                    const value = event.target.value;
                    setEntries((current) => current.map((row) => row.key === entry.key ? { ...row, shiftEnd: value } : row));
                  }}
                />
              </div>
              <div className="space-y-1.5">
                <Label>Stundensatz EUR</Label>
                <Input
                  type="number"
                  min="0"
                  step="0.01"
                  value={entry.hourlyWage}
                  onChange={(event) => {
                    const value = event.target.value;
                    setEntries((current) => current.map((row) => row.key === entry.key ? { ...row, hourlyWage: value } : row));
                  }}
                />
              </div>
              <div className="self-end">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => removeEntry(entry.key)}
                  disabled={entries.length <= 1 && index === 0}
                >
                  Entfernen
                </Button>
              </div>
            </div>
          ))}
          <div className="flex gap-2">
            <Button type="button" variant="secondary" onClick={addEntry}>Mitarbeiter hinzufügen</Button>
            <Button type="button" onClick={() => void saveSettlement()} disabled={saving}>
              {saving ? "Speichere..." : "Schichtabrechnung speichern"}
            </Button>
          </div>
        </CardContent>
      </Card>

      <section className="grid gap-4 sm:grid-cols-3">
        <Card className="border-cyan-500/20">
          <CardContent className="p-5">
            <p className="text-sm text-[color:var(--color-muted-foreground)]">Tagesumsatz</p>
            <p className="mt-2 text-2xl font-semibold">{formatCurrency(totals.revenue)}</p>
          </CardContent>
        </Card>
        <Card className="border-cyan-500/20">
          <CardContent className="p-5">
            <p className="text-sm text-[color:var(--color-muted-foreground)]">Lohnsumme</p>
            <p className="mt-2 text-2xl font-semibold">{formatCurrency(totals.wages)}</p>
          </CardContent>
        </Card>
        <Card className="border-cyan-500/20">
          <CardContent className="p-5">
            <p className="text-sm text-[color:var(--color-muted-foreground)]">Erwarteter Kassenbestand</p>
            <p className="mt-2 text-2xl font-semibold">{formatCurrency(totals.expectedCash)}</p>
          </CardContent>
        </Card>
      </section>

      {settlement && settlement.entries.length > 0 ? (
        <Card>
          <CardHeader>
            <CardTitle>Berechnete Löhne</CardTitle>
            <CardDescription>Stunden und Lohnkosten pro Schichtposition.</CardDescription>
          </CardHeader>
          <CardContent className="overflow-x-auto">
            <table className="min-w-full border-collapse text-left text-sm">
              <thead className="text-[color:var(--color-muted-foreground)]">
                <tr>
                  <th className="px-3 py-2 font-medium">Name</th>
                  <th className="px-3 py-2 font-medium">Von</th>
                  <th className="px-3 py-2 font-medium">Bis</th>
                  <th className="px-3 py-2 font-medium">Stunden</th>
                  <th className="px-3 py-2 font-medium">Stundensatz</th>
                  <th className="px-3 py-2 font-medium">Lohnkosten</th>
                </tr>
              </thead>
              <tbody>
                {settlement.entries.map((entry) => (
                  <tr key={`${entry.id ?? entry.employeeName}-${entry.shiftStart}`} className="border-t border-[color:var(--color-border)]">
                    <td className="px-3 py-3">{entry.employeeName}</td>
                    <td className="px-3 py-3">{normalizeTimeInput(entry.shiftStart)}</td>
                    <td className="px-3 py-3">{normalizeTimeInput(entry.shiftEnd)}</td>
                    <td className="px-3 py-3">{entry.workedHours} h</td>
                    <td className="px-3 py-3">{formatCurrency(entry.hourlyWage)}</td>
                    <td className="px-3 py-3">{formatCurrency(entry.wageCost)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </CardContent>
        </Card>
      ) : null}

      <Card>
        <CardHeader className="gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <CardTitle>Historie</CardTitle>
            <CardDescription>Wochen- oder Monatsübersicht der gespeicherten Schichtabrechnungen.</CardDescription>
          </div>
          <div className="flex gap-2">
            <Button
              type="button"
              variant={historyPreset === "WEEK" ? "secondary" : "outline"}
              onClick={() => setHistoryPreset("WEEK")}
            >
              Woche
            </Button>
            <Button
              type="button"
              variant={historyPreset === "MONTH" ? "secondary" : "outline"}
              onClick={() => setHistoryPreset("MONTH")}
            >
              Monat
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          {historyLoading ? (
            <p className="text-sm text-[color:var(--color-muted-foreground)]">Lade Historie...</p>
          ) : historyEntries.length === 0 ? (
            <p className="text-sm text-[color:var(--color-muted-foreground)]">Keine gespeicherten Schichtabrechnungen im gewählten Zeitraum.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full border-collapse text-left text-sm">
                <thead className="text-[color:var(--color-muted-foreground)]">
                  <tr>
                    <th className="px-3 py-2 font-medium">Datum</th>
                    <th className="px-3 py-2 font-medium">Umsatz</th>
                    <th className="px-3 py-2 font-medium">Löhne</th>
                    <th className="px-3 py-2 font-medium">Sonstige Ausgaben</th>
                    <th className="px-3 py-2 font-medium">Erwarteter Kassenbestand</th>
                  </tr>
                </thead>
                <tbody>
                  {historyEntries.map((entry) => (
                    <tr key={entry.settlementDate} className="border-t border-[color:var(--color-border)]">
                      <td className="px-3 py-3">{entry.settlementDate}</td>
                      <td className="px-3 py-3">{formatCurrency(entry.dailyRevenue)}</td>
                      <td className="px-3 py-3">{formatCurrency(entry.totalWages)}</td>
                      <td className="px-3 py-3">{formatCurrency(entry.otherExpenses)}</td>
                      <td className="px-3 py-3">{formatCurrency(entry.expectedClosingCash)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      {status ? <p className="rounded-lg border border-green-500/30 bg-green-500/10 px-3 py-2 text-sm text-green-300">{status}</p> : null}
      {error ? <p className="rounded-lg border border-red-500/30 bg-red-500/10 px-3 py-2 text-sm text-red-300">{error}</p> : null}
    </main>
  );
}

