"use client";

import { useEffect, useMemo, useState } from "react";
import { Beer, Martini, Sparkles } from "lucide-react";
import type { DrinkVariant } from "@/types/api";

function formatPrice(value: string): string {
  const amount = Number(value);
  if (Number.isNaN(amount)) {
    return `${value} EUR`;
  }

  return new Intl.NumberFormat("de-DE", {
    style: "currency",
    currency: "EUR",
  }).format(amount);
}

export default function Home() {
  const [variants, setVariants] = useState<DrinkVariant[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;

    async function load() {
      const response = await fetch("/api/drink-variants", { cache: "no-store" });
      if (!active) {
        return;
      }

      if (!response.ok) {
        setVariants([]);
        setLoading(false);
        return;
      }

      const payload = (await response.json()) as DrinkVariant[];
      setVariants(payload);
      setLoading(false);
    }

    void load();
    return () => {
      active = false;
    };
  }, []);

  const grouped = useMemo(() => {
    const activeVariants = variants
      .filter((variant) => variant.active)
      .sort((a, b) => a.drinkName.localeCompare(b.drinkName) || a.volumeMl - b.volumeMl);

    const result = new Map<string, DrinkVariant[]>();
    for (const variant of activeVariants) {
      const list = result.get(variant.drinkName) ?? [];
      list.push(variant);
      result.set(variant.drinkName, list);
    }
    return result;
  }, [variants]);

  return (
    <section className="mx-auto w-full max-w-5xl p-4 sm:p-6">
      <div className="mb-6 rounded-2xl border border-indigo-200/80 bg-gradient-to-r from-indigo-600 via-violet-600 to-cyan-500 p-5 text-white shadow-xl shadow-indigo-400/30">
        <div className="mb-2 inline-flex items-center gap-2 rounded-full bg-white/20 px-3 py-1 text-xs font-medium backdrop-blur">
          <Sparkles className="h-3.5 w-3.5" />
          FassWerk Drinks
        </div>
        <h1 className="text-3xl font-semibold sm:text-4xl">Getraenkekarte</h1>
        <p className="mt-2 text-sm text-indigo-50">Willkommen bei FassWerk. Alle Preise inkl. MwSt.</p>
      </div>

      {loading || grouped.size === 0 ? (
        <div className="rounded-2xl border border-indigo-200 bg-white/90 p-5 text-sm text-slate-600">
          {loading
            ? "Getraenkekarte wird geladen..."
            : "Die Getraenkekarte ist derzeit nicht verfuegbar."}
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          {Array.from(grouped.entries()).map(([drinkName, drinkVariants]) => (
            <article key={drinkName} className="rounded-2xl border border-indigo-200 bg-white/90 p-4 shadow-sm shadow-indigo-100/60">
              <h2 className="inline-flex items-center gap-2 text-lg font-semibold text-slate-900">
                {drinkName.toLowerCase().includes("beer") ? (
                  <Beer className="h-4 w-4 text-amber-500" />
                ) : (
                  <Martini className="h-4 w-4 text-indigo-500" />
                )}
                {drinkName}
              </h2>
              <ul className="mt-3 space-y-2">
                {drinkVariants.map((variant) => (
                  <li
                    key={variant.id}
                    className="flex items-center justify-between gap-3 rounded-xl border border-indigo-100 bg-[color:var(--color-surface-muted)] px-3 py-2"
                  >
                    <span className="text-sm text-slate-700">{variant.displayVolumeName}</span>
                    <span className="rounded-full bg-indigo-100 px-2.5 py-1 text-sm font-semibold text-indigo-700">
                      {formatPrice(variant.price)}
                    </span>
                  </li>
                ))}
              </ul>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
