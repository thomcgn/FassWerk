"use client";

import { useState, useEffect } from "react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { SalesConfigurationDashboard } from "@/components/sales-configuration-dashboard";
import { ItemConfigurationList } from "@/components/consumption-metadata-form";
import { AlertCircle, Settings, Sliders, Package } from "lucide-react";
import type { InventoryItem } from "@/types/api";

export function SalesConfigurationPage() {
  const [inventoryItems, setInventoryItems] = useState<InventoryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    const fetchInventory = async () => {
      try {
        setLoading(true);
        const response = await fetch("/api/inventory");
        if (!response.ok) throw new Error("Lagerartikel konnten nicht geladen werden");
        const data = await response.json() as InventoryItem[];
        setInventoryItems(data);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Fehler beim Laden");
      } finally {
        setLoading(false);
      }
    };

    void fetchInventory();
  }, [refreshKey]);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight flex items-center gap-2">
          <Settings className="h-8 w-8 text-cyan-400" />
          Nachbestellberechnung - Konfiguration
        </h1>
        <p className="mt-2 text-muted-foreground">
          Verwalten Sie die Parameter für die automatische Nachbestellberechnung
        </p>
      </div>

      {error && (
        <div className="flex items-center gap-2 rounded-lg border border-red-500/40 bg-red-500/15 p-4 text-red-100">
          <AlertCircle className="h-5 w-5" />
          {error}
        </div>
      )}

      <Tabs defaultValue="global" className="w-full">
        <TabsList className="grid w-full grid-cols-2">
          <TabsTrigger value="global" className="gap-2">
            <Sliders className="h-4 w-4" />
            Global Einstellungen
          </TabsTrigger>
          <TabsTrigger value="items" className="gap-2">
            <Package className="h-4 w-4" />
            Pro Artikel ({inventoryItems.length})
          </TabsTrigger>
        </TabsList>

        <TabsContent value="global" className="space-y-6 mt-6">
          <Card className="border-blue-500/30 bg-blue-500/10">
            <CardHeader>
              <CardTitle>Über Global Einstellungen</CardTitle>
              <CardDescription>
                Diese Parameter dienen als Standard für alle Lagerartikel
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-2 text-sm text-muted-foreground">
              <p>
                ✓ Werden verwendet, wenn kein spezifischer Wert für einen Artikel konfiguriert ist
              </p>
              <p>
                ✓ Änderungen wirken sich auf alle nachfolgenden Berechnungen aus
              </p>
              <p>
                ✓ Pro-Artikel-Konfigurationen können diese Werte überschreiben
              </p>
            </CardContent>
          </Card>

          <SalesConfigurationDashboard />
        </TabsContent>

        <TabsContent value="items" className="space-y-6 mt-6">
          <Card className="border-cyan-500/30 bg-cyan-500/10">
            <CardHeader>
              <CardTitle>Spezifische Artikel-Konfiguration</CardTitle>
              <CardDescription>
                Passen Sie die Parameter für einzelne Lagerartikel an
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-2 text-sm text-muted-foreground">
              <p>
                ✓ Überschreibt die Global Einstellungen für den jeweiligen Artikel
              </p>
              <p>
                ✓ Nützlich für Getränke mit unregelmäßigen Umsatzmustern
              </p>
              <p>
                ✓ Klicken Sie auf einen Artikel um die Einstellungen zu bearbeiten
              </p>
            </CardContent>
          </Card>

          {loading ? (
            <div className="py-8 text-center text-muted-foreground">Lädt Lagerartikel...</div>
          ) : (
            <ItemConfigurationList
              inventoryItems={inventoryItems}
              onRefresh={() => setRefreshKey((k) => k + 1)}
            />
          )}
        </TabsContent>
      </Tabs>

      {/* Quick Reference */}
      <Card className="border-amber-500/30 bg-amber-500/10">
        <CardHeader>
          <CardTitle className="text-base">📖 Referenz: Berechnungsparameter</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-sm">
            <div className="space-y-2">
              <div className="font-semibold text-foreground">Lookback-Periode</div>
              <div className="text-muted-foreground">
                <p className="mb-2">Bestimmt wie viele Wochen bei der Durchschnittsberechnung berücksichtigt werden</p>
                <p className="text-xs">📌 Standard: 4 Wochen</p>
              </div>
            </div>
            <div className="space-y-2">
              <div className="font-semibold text-foreground">Sicherheitsbestands-Faktor</div>
              <div className="text-muted-foreground">
                <p className="mb-2">Multiplikator für den Puffer gegen unvorhergesehene Spitzen</p>
                <p className="text-xs">📌 Standard: 1.5× wöchentlicher Umsatz</p>
              </div>
            </div>
            <div className="space-y-2">
              <div className="font-semibold text-foreground">Lieferzeitraum</div>
              <div className="text-muted-foreground">
                <p className="mb-2">Typische Zeit bis eine Bestellung vom Lieferanten ankommt</p>
                <p className="text-xs">📌 Standard: 3 Tage</p>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

