// Diese Komponente kann in bar-admin-client.tsx integriert werden
// Sie zeigt die empfohlene Nachbestellmenge basierend auf Wochenumsatz

import { useEffect, useState } from "react";
import { TrendingUp, AlertTriangle } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import type { ReorderCalculation, InventoryItem } from "@/types/api";

export function ReorderCalculationPanel({ inventoryItem }: { inventoryItem: InventoryItem }) {
  const [calculation, setCalculation] = useState<ReorderCalculation | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchCalculation = async () => {
      try {
        setLoading(true);
        const response = await fetch(`/api/inventory/${inventoryItem.id}/reorder-calculation`);
        if (!response.ok) {
          throw new Error("Nachberechnung konnte nicht geladen werden");
        }
        const data = await response.json() as ReorderCalculation;
        setCalculation(data);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Fehler beim Laden");
      } finally {
        setLoading(false);
      }
    };

    void fetchCalculation();
  }, [inventoryItem.id]);

  if (loading) return <div className="text-sm text-muted">Lädt...</div>;
  if (error) return <div className="text-sm text-red-500">{error}</div>;
  if (!calculation) return null;

  const weeksUntilStockout = calculation.weeksUntilStockout
    ? parseFloat(calculation.weeksUntilStockout)
    : null;
  const isUrgent = calculation.isBelowThreshold || (weeksUntilStockout !== null && weeksUntilStockout < 1);

  return (
    <Card className={isUrgent ? "border-red-500/50 bg-red-500/5" : "border-cyan-500/30 bg-cyan-500/5"}>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <TrendingUp className="h-4 w-4 text-cyan-400" />
            <CardTitle className="text-sm">Nachbestellberechnung</CardTitle>
          </div>
          {isUrgent && (
            <Badge variant="destructive" className="flex gap-1 items-center">
              <AlertTriangle className="h-3 w-3" />
              Dringend
            </Badge>
          )}
        </div>
        <CardDescription className="text-xs">
          Basierend auf Wochenumsatz vom {new Date(calculation.calculationDate).toLocaleDateString("de-DE")}
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3 text-sm">
        <div className="grid grid-cols-2 gap-3">
          <div>
            <p className="text-xs text-muted mb-1">Aktueller Bestand</p>
            <p className="font-semibold">{Number(calculation.currentStockAmount).toFixed(2)} {inventoryItem.contentUnit}</p>
          </div>
          <div>
            <p className="text-xs text-muted mb-1">Wöchentlicher Durchschnitt</p>
            <p className="font-semibold">{Number(calculation.weeklyAverageConsumption).toFixed(2)} {inventoryItem.contentUnit}/Wo.</p>
          </div>
        </div>

        <div className="rounded-lg bg-white/50 p-3 border border-cyan-500/20">
          <p className="text-xs text-muted mb-1">Empfohlene Nachbestellmenge</p>
          <p className="text-lg font-bold text-cyan-400">
            {Number(calculation.recommendedReorderAmount).toFixed(2)} {inventoryItem.contentUnit}
          </p>
        </div>

        {weeksUntilStockout !== null && weeksUntilStockout >= 0 && (
          <div>
            <p className="text-xs text-muted mb-1">Wochen bis zur Leere</p>
            <p className="font-semibold">{weeksUntilStockout.toFixed(1)} Wochen</p>
          </div>
        )}

        <div className="text-xs text-muted pt-2 border-t border-cyan-500/10">
          {calculation.isBelowThreshold ? (
            <p className="text-orange-500">⚠️ Bestand liegt unter Schwellwert</p>
          ) : (
            <p className="text-green-500">✓ Bestand ausreichend</p>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

// Komponente zur Anzeige von Artikel mit kritischem Bestand
export function CriticalReorderItems() {
  const [items, setItems] = useState<ReorderCalculation[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchItems = async () => {
      try {
        setLoading(true);
        const response = await fetch("/api/inventory/reorder-calculations/below-threshold");
        if (!response.ok) throw new Error("Kritische Artikel konnten nicht geladen werden");
        const data = await response.json() as ReorderCalculation[];
        setItems(data);
      } catch (err) {
        console.error("Fehler beim Laden kritischer Artikel:", err);
      } finally {
        setLoading(false);
      }
    };

    void fetchItems();
  }, []);

  if (loading) return <div>Lädt...</div>;

  if (items.length === 0) {
    return (
      <Card className="border-green-500/30 bg-green-500/5">
        <CardHeader>
          <CardTitle className="text-lg">Kritische Artikel</CardTitle>
          <CardDescription>Alle Artikel haben ausreichende Bestände</CardDescription>
        </CardHeader>
      </Card>
    );
  }

  return (
    <Card className="border-red-500/40 bg-red-500/10">
      <CardHeader>
        <CardTitle className="text-lg flex items-center gap-2">
          <AlertTriangle className="h-5 w-5 text-red-500" />
          Kritische Artikel ({items.length})
        </CardTitle>
        <CardDescription>Artikel, deren Bestand unter dem Schwellwert liegt</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="space-y-3">
          {items.map((item) => (
            <div key={item.id} className="rounded-lg border border-red-500/30 bg-red-500/5 p-3">
              <div className="flex justify-between items-start mb-2">
                <div>
                  <p className="font-semibold">{item.inventoryItemName}</p>
                  <p className="text-xs text-muted">Bestand: {Number(item.currentStockAmount).toFixed(2)}</p>
                </div>
                <Badge variant="destructive">
                  {Number(item.recommendedReorderAmount).toFixed(2)} nachbestellen
                </Badge>
              </div>
              <div className="text-xs text-muted">
                Wöchentlicher Umsatz: {Number(item.weeklyAverageConsumption).toFixed(2)}
              </div>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}

