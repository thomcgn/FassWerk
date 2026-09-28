"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { Table } from "@/types/api";

export function TableCapacityEditor({ tables, onSaved }: { tables: Table[]; onSaved: () => Promise<void> }) {
  const [values, setValues] = useState<Record<number, string>>({});
  const [message, setMessage] = useState("");
  const [saving, setSaving] = useState<number | null>(null);
  async function save(table: Table) {
    setSaving(table.id);
    setMessage("");
    try {
      const seats = Number(values[table.id] ?? table.seats);
      const response = await fetch(`/api/tables/${table.id}`, {
        method: "PUT", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: table.name, area: table.area, status: table.status, active: table.active, seats }),
      });
      const body = await response.json();
      if (!response.ok) { setMessage(body.message || "Kapazitaet konnte nicht gespeichert werden."); return; }
      await onSaved();
      setMessage("Tischkapazitaet gespeichert.");
    } catch { setMessage("Verbindung fehlgeschlagen."); }
    finally { setSaving(null); }
  }
  return <details className="rounded-xl border border-[color:var(--color-border-strong)] p-4">
    <summary className="cursor-pointer font-semibold">Tischkapazitaeten fuer Reservierungen</summary>
    <p className="my-3 text-sm">Ohne gepflegte Sitzplaetze ist ein Tisch nicht automatisch buchbar. Gruppen werden nur innerhalb desselben Bereichs kombiniert.</p>
    <div className="grid gap-3 sm:grid-cols-2">
      {tables.map(table => <div key={table.id} className="flex items-end gap-2">
        <label className="flex-1">{table.name} ({table.area || "ohne Bereich"})
          <Input type="number" min={1} aria-label={`Sitzplaetze ${table.name}`}
            value={values[table.id] ?? table.seats ?? ""}
            onChange={event => setValues(current => ({ ...current, [table.id]: event.target.value }))}
            placeholder="Noch nicht gepflegt" />
        </label>
        <Button disabled={saving !== null || !Number(values[table.id] ?? table.seats)} onClick={() => void save(table)}>Speichern</Button>
      </div>)}
    </div>
    {message && <p role="status" className="mt-3">{message}</p>}
  </details>;
}
