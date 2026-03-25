"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Activity, BarChart3, CalendarDays, TrendingUp } from "lucide-react";
import toast from "react-hot-toast";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useToastFeedback } from "@/lib/use-toast-feedback";

type RevenuePoint = {
  label: string;
  revenue: string;
};

type RevenueOverview = {
  dayRevenue: string;
  weekRevenue: string;
  monthRevenue: string;
  dayConsumedMl: string;
  weekConsumedMl: string;
  monthConsumedMl: string;
  strongestWeekday: string;
  weekPoints: RevenuePoint[];
  monthPoints: RevenuePoint[];
};

type State = "loading" | "ready" | "error";

function toCurrency(value: string): string {
  const amount = Number(value);
  if (Number.isNaN(amount)) return `${value} EUR`;
  return new Intl.NumberFormat("de-DE", { style: "currency", currency: "EUR" }).format(amount);
}

function toNumber(value: string): number {
  const amount = Number(value);
  return Number.isNaN(amount) ? 0 : amount;
}

function mlToLitersLabel(value: string | undefined): string {
  const amountMl = Number(value ?? "0");
  if (!Number.isFinite(amountMl)) return "0,00 l";
  return `${new Intl.NumberFormat("de-DE", { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(amountMl / 1000)} l`;
}

export default function AuthMetricsClient() {
  const router = useRouter();
  const [state, setState] = useState<State>("loading");
  const [overview, setOverview] = useState<RevenueOverview | null>(null);
  const [error, setError] = useState<string | null>(null);

  useToastFeedback(error, "error");

  const loadMetrics = useCallback(async (manual = false) => {
    try {
      const response = await fetch("/api/ops/auth-metrics", { cache: "no-store" });
      if (response.status === 401 || response.status === 403) {
        router.replace("/login");
        return;
      }
      if (!response.ok) {
        setError("Umsatzdaten konnten nicht geladen werden.");
        setState("error");
        return;
      }
      const payload = (await response.json()) as RevenueOverview;
      setOverview(payload);
      setState("ready");
      if (manual) {
        toast.success("Ops-Daten aktualisiert.");
      }
    } catch {
      setError("Unerwarteter Fehler beim Laden der Umsatzdaten.");
      setState("error");
    }
  }, [router]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadMetrics(false), 0);
    return () => window.clearTimeout(timer);
  }, [loadMetrics]);

  if (state === "loading") return <main className="p-6 text-sm text-zinc-500">Lade Umsatz-Dashboard...</main>;

  if (state === "error") {
    return (
      <main className="mx-auto w-full max-w-3xl p-6">
        <Card className="border-rose-100 bg-rose-50/80">
          <CardHeader>
            <CardTitle className="text-rose-700">Fehler</CardTitle>
            <CardDescription className="text-rose-700">{error}</CardDescription>
          </CardHeader>
          <CardContent>
            <Button variant="outline" onClick={() => void loadMetrics(true)}>Erneut versuchen</Button>
          </CardContent>
        </Card>
      </main>
    );
  }

  return (
    <main className="dashboard-grid">
      <section className="grid gap-4 lg:grid-cols-[1fr_auto] lg:items-end">
        <Card className="bg-zinc-950 text-white">
          <CardContent className="p-6">
            <h1 className="mt-3 text-3xl font-semibold tracking-tight">Tagesumsatz, Wochenumsatz und Monatsübersicht.</h1>
            <p className="mt-3 max-w-2xl text-sm leading-6 text-white/70">Live-Einblick in den Verkauf inkl. stärkstem Wochentag und grafischer Verlaufssicht.</p>
            <p className="mt-2 text-xs text-white/70">
              Verbrauch: Heute {mlToLitersLabel(overview?.dayConsumedMl)} · Woche {mlToLitersLabel(overview?.weekConsumedMl)} · Monat {mlToLitersLabel(overview?.monthConsumedMl)}
            </p>
          </CardContent>
        </Card>
        <Button onClick={() => void loadMetrics(true)} size="lg">
          <Activity className="h-4 w-4" />
          Aktualisieren
        </Button>
      </section>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Card>
          <CardContent className="p-5">
            <CalendarDays className="h-5 w-5 text-cyan-400" />
            <p className="mt-4 text-xs uppercase tracking-[0.16em] text-[color:var(--color-muted-foreground)]">Tagesumsatz</p>
            <p className="mt-2 text-3xl font-semibold text-[color:var(--color-foreground)]">{toCurrency(overview?.dayRevenue ?? "0")}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-5">
            <TrendingUp className="h-5 w-5 text-emerald-400" />
            <p className="mt-4 text-xs uppercase tracking-[0.16em] text-[color:var(--color-muted-foreground)]">Wochenumsatz</p>
            <p className="mt-2 text-3xl font-semibold text-[color:var(--color-foreground)]">{toCurrency(overview?.weekRevenue ?? "0")}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-5">
            <BarChart3 className="h-5 w-5 text-amber-400" />
            <p className="mt-4 text-xs uppercase tracking-[0.16em] text-[color:var(--color-muted-foreground)]">Monatsumsatz</p>
            <p className="mt-2 text-3xl font-semibold text-[color:var(--color-foreground)]">{toCurrency(overview?.monthRevenue ?? "0")}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-5">
            <Activity className="h-5 w-5 text-fuchsia-400" />
            <p className="mt-4 text-xs uppercase tracking-[0.16em] text-[color:var(--color-muted-foreground)]">Stärkster Wochentag</p>
            <p className="mt-2 text-3xl font-semibold text-[color:var(--color-foreground)]">{overview?.strongestWeekday ?? "-"}</p>
          </CardContent>
        </Card>
      </div>

      <section className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Wochenverlauf</CardTitle>
            <CardDescription>Umsatz pro Wochentag</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {(overview?.weekPoints ?? []).map((point) => {
              const max = Math.max(...(overview?.weekPoints ?? [{ label: "", revenue: "0" }]).map((p) => toNumber(p.revenue)), 1);
              const width = `${Math.max(6, (toNumber(point.revenue) / max) * 100)}%`;
              return (
                <div key={point.label} className="space-y-1">
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-[color:var(--color-muted-foreground)]">{point.label}</span>
                    <span className="font-semibold text-[color:var(--color-foreground)]">{toCurrency(point.revenue)}</span>
                  </div>
                  <div className="h-2 rounded-full bg-white/8">
                    <div className="h-2 rounded-full bg-gradient-to-r from-cyan-500 to-blue-600" style={{ width }} />
                  </div>
                </div>
              );
            })}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Monatsübersicht</CardTitle>
            <CardDescription>Tagesumsätze im aktuellen Monat</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-8 gap-2">
              {(overview?.monthPoints ?? []).map((point) => {
                const max = Math.max(...(overview?.monthPoints ?? [{ label: "", revenue: "0" }]).map((p) => toNumber(p.revenue)), 1);
                const height = Math.max(10, (toNumber(point.revenue) / max) * 100);
                return (
                  <div key={point.label} className="flex flex-col items-center gap-1">
                    <div className="flex h-24 w-6 items-end rounded bg-white/8 p-0.5">
                      <div className="w-full rounded bg-gradient-to-t from-fuchsia-500 to-cyan-500" style={{ height: `${height}%` }} />
                    </div>
                    <span className="text-[10px] text-[color:var(--color-muted-foreground)]">{point.label}</span>
                  </div>
                );
              })}
            </div>
          </CardContent>
        </Card>
      </section>
    </main>
  );
}
