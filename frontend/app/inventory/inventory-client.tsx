"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import type { InventoryItem } from "@/types/api";

type LoadState = "loading" | "ready" | "error";

export default function InventoryClient() {
  const router = useRouter();
  const [state, setState] = useState<LoadState>("loading");
  const [items, setItems] = useState<InventoryItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");

  const loadInventory = useCallback(async () => {
    try {
      const response = await fetch("/api/inventory", { cache: "no-store" });
      if (response.status === 401) {
        router.replace("/login");
        return;
      }
      if (!response.ok) {
        setError("Inventory konnte nicht geladen werden.");
        setState("error");
        return;
      }

      const payload = (await response.json()) as InventoryItem[];
      setItems(payload);
      setState("ready");
    } catch {
      setError("Unerwarteter Fehler beim Laden.");
      setState("error");
    }
  }, [router]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadInventory();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [loadInventory]);

  async function logout() {
    await fetch("/api/auth/logout", { method: "POST" });
    router.replace("/login");
    router.refresh();
  }

  const filteredItems = useMemo(() => {
    const query = search.trim().toLowerCase();
    if (!query) {
      return items;
    }
    return items.filter((item) => {
      const supplier = item.supplier?.toLowerCase() ?? "";
      return item.name.toLowerCase().includes(query) || supplier.includes(query);
    });
  }, [items, search]);

  function getStatus(item: InventoryItem): { label: string; className: string } {
    if (!item.active) {
      return { label: "INACTIVE", className: "bg-zinc-200 text-zinc-700" };
    }

    const stock = Number(item.totalStockAmount);
    const threshold = Number(item.reorderThreshold);
    if (Number.isNaN(stock) || Number.isNaN(threshold) || stock <= threshold) {
      return { label: "LOW", className: "bg-amber-100 text-amber-800" };
    }
    return { label: "OK", className: "bg-emerald-100 text-emerald-800" };
  }

  if (state === "loading") {
    return <main className="p-6">Lade Inventory...</main>;
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
              void loadInventory();
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
        <h1 className="text-3xl font-semibold">Inventory</h1>
        <div className="flex items-center gap-3">
          <a
            href="/api/reports/reorder-list"
            target="_blank"
            rel="noopener noreferrer"
            className="rounded-md border border-zinc-400 px-3 py-2"
          >
            Reorder PDF
          </a>
          <button
            onClick={logout}
            className="rounded-md bg-zinc-900 px-3 py-2 text-white"
          >
            Logout
          </button>
        </div>
      </div>

      <div className="mb-4 flex items-center justify-between gap-3">
        <input
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Suchen nach Name oder Lieferant"
          className="w-full max-w-sm rounded-md border border-zinc-300 px-3 py-2"
        />
        <p className="text-sm text-zinc-600">{filteredItems.length} Eintraege</p>
      </div>

      <div className="overflow-x-auto rounded-xl border border-zinc-300">
        <table className="min-w-full border-collapse text-left text-sm">
          <thead>
            <tr className="bg-zinc-100">
              <th className="px-3 py-2">Status</th>
              <th className="px-3 py-2">Name</th>
              <th className="px-3 py-2">Bestand</th>
              <th className="px-3 py-2">Schwelle</th>
              <th className="px-3 py-2">Gebinde</th>
              <th className="px-3 py-2">Lieferant</th>
            </tr>
          </thead>
          <tbody>
            {filteredItems.map((item) => {
              const status = getStatus(item);
              return (
                <tr key={item.id} className="border-t border-zinc-200">
                  <td className="px-3 py-2">
                    <span className={`rounded-full px-2 py-1 text-xs font-semibold ${status.className}`}>
                      {status.label}
                    </span>
                  </td>
                  <td className="px-3 py-2 font-medium">{item.name}</td>
                  <td className="px-3 py-2">
                    {item.totalStockAmount} {item.contentUnit}
                  </td>
                  <td className="px-3 py-2">
                    {item.reorderThreshold} {item.contentUnit}
                  </td>
                  <td className="px-3 py-2">
                    {item.packageType} ({item.contentPerPackage} {item.contentUnit})
                  </td>
                  <td className="px-3 py-2">{item.supplier ?? "-"}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </main>
  );
}

