"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { GlassWater } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import type { DrinkVariant } from "@/types/api";

function formatPrice(value: string): string {
  const amount = Number(value);
  if (Number.isNaN(amount)) return `${value} EUR`;
  return new Intl.NumberFormat("de-DE", { style: "currency", currency: "EUR" }).format(amount);
}

function formatVolume(variant: DrinkVariant) {
  if (variant.displayVolumeName?.trim()) return variant.displayVolumeName;
  if (variant.volumeMl >= 1000) return `${variant.volumeMl / 1000} l`;
  return `${variant.volumeMl} ml`;
}

export default function Home() {
  const [variants, setVariants] = useState<DrinkVariant[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    async function load() {
      const response = await fetch("/api/drink-variants", { cache: "no-store" });
      if (!active) return;
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
    return Array.from(result.entries());
  }, [variants]);

  return (
    <section className="mx-auto w-full max-w-6xl p-4 sm:p-6 animate-in fade-in slide-in-from-bottom-4 duration-500">
      <Card className="border-cyan-500/20">
        <CardContent className="p-5 sm:p-6">
          <div className="mb-6 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div>
              <p className="text-xs uppercase tracking-[0.2em] font-semibold text-cyan-400">Getränkekarte</p>
              <h1 className="mt-2 text-3xl font-bold">Unsere Auswahl</h1>
            </div>
            <Link href="/bookings">
              <Button variant="secondary" className="bg-cyan-500/20 hover:bg-cyan-500/30 text-cyan-300 border border-cyan-500/40">Reservieren</Button>
            </Link>
          </div>

          {loading || grouped.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-cyan-500/20 bg-cyan-500/5 px-6 py-12 text-center">
              <div className="h-8 w-8 rounded-full bg-cyan-500/20 mx-auto mb-3 animate-pulse"></div>
              <p className="text-sm text-[color:var(--color-muted-foreground)]">
                {loading ? "Getränkekarte wird geladen..." : "Derzeit sind keine aktiven Getränke verfügbar."}
              </p>
            </div>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {grouped.map(([drinkName, drinkVariants], idx) => (
                <article
                  key={drinkName}
                  className="group rounded-2xl border border-cyan-500/20 bg-gradient-to-br from-cyan-500/5 to-blue-600/5 p-5 hover:border-cyan-500/40 hover:bg-cyan-500/10 transition-all duration-300 hover:shadow-lg hover:shadow-cyan-500/20 hover:-translate-y-1 animate-in fade-in slide-in-from-bottom-2"
                  style={{ animationDelay: `${idx * 50}ms` }}
                >
                  <div className="flex items-start justify-between gap-3 mb-4">
                    <div className="flex-1">
                      <h3 className="text-lg font-bold group-hover:text-cyan-300 transition-colors">{drinkName}</h3>
                      <p className="text-sm text-[color:var(--color-muted-foreground)]">{drinkVariants.length} Variante{drinkVariants.length !== 1 ? 'n' : ''}</p>
                    </div>
                    <span className="inline-flex items-center gap-1.5 rounded-lg bg-cyan-500/25 px-2.5 py-1.5 text-xs font-semibold text-cyan-200 border border-cyan-500/40">
                      <GlassWater className="h-3.5 w-3.5" />
                      Bar
                    </span>
                  </div>
                  <div className="space-y-2">
                    {drinkVariants.map((variant) => (
                      <div key={variant.id} className="flex items-center justify-between rounded-xl bg-white/5 hover:bg-white/10 px-3 py-3 text-sm transition-colors duration-200">
                        <span className="font-semibold text-[color:var(--color-foreground)]">{formatVolume(variant)}</span>
                        <span className="font-bold text-cyan-300">{formatPrice(variant.price)}</span>
                      </div>
                    ))}
                  </div>
                </article>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </section>
  );
}
