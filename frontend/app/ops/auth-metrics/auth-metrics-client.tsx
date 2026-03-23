"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";

type MetricEntry = {
  name: string;
  value: number;
};

type State = "loading" | "ready" | "error";

function toLabel(name: string): string {
  return name.replaceAll(".", " ").toUpperCase();
}

export default function AuthMetricsClient() {
  const router = useRouter();
  const [state, setState] = useState<State>("loading");
  const [metrics, setMetrics] = useState<MetricEntry[]>([]);
  const [error, setError] = useState<string | null>(null);

  const loadMetrics = useCallback(async () => {
    try {
      const response = await fetch("/api/ops/auth-metrics", { cache: "no-store" });
      if (response.status === 401 || response.status === 403) {
        router.replace("/login");
        return;
      }
      if (!response.ok) {
        setError("Auth-Metriken konnten nicht geladen werden.");
        setState("error");
        return;
      }

      const payload = (await response.json()) as { metrics?: MetricEntry[] };
      setMetrics(payload.metrics ?? []);
      setState("ready");
    } catch {
      setError("Unerwarteter Fehler beim Laden der Metriken.");
      setState("error");
    }
  }, [router]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadMetrics();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [loadMetrics]);

  if (state === "loading") {
    return <main className="p-6">Lade Auth-Metriken...</main>;
  }

  if (state === "error") {
    return (
      <main className="mx-auto w-full max-w-3xl p-6">
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-red-700">
          <p className="font-medium">{error}</p>
          <button
            onClick={() => {
              setState("loading");
              setError(null);
              void loadMetrics();
            }}
            className="mt-3 rounded-md border border-red-400 px-3 py-2 text-sm"
          >
            Erneut versuchen
          </button>
        </div>
      </main>
    );
  }

  return (
    <main className="mx-auto w-full max-w-6xl p-6">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-3xl font-semibold">Auth Metrics</h1>
        <div className="flex items-center gap-2">
          <a href="/inventory" className="rounded-md border border-zinc-400 px-3 py-2">
            Zurueck zu Inventory
          </a>
          <button
            onClick={() => {
              setState("loading");
              setError(null);
              void loadMetrics();
            }}
            className="rounded-md bg-zinc-900 px-3 py-2 text-white"
          >
            Aktualisieren
          </button>
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {metrics.map((metric) => (
          <div key={metric.name} className="rounded-lg border border-zinc-300 p-4">
            <p className="text-xs text-zinc-500">{toLabel(metric.name)}</p>
            <p className="mt-2 text-2xl font-semibold">{metric.value.toFixed(0)}</p>
          </div>
        ))}
      </div>
    </main>
  );
}

