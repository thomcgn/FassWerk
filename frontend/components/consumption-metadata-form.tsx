"use client";

import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import {
  X,
  Save,
  AlertCircle,
  CheckCircle,
  Plus,
} from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import type { InventoryItem, ConsumptionMetadata } from "@/types/api";

interface ConsumptionMetadataFormProps {
  inventoryItem: InventoryItem;
  onClose: () => void;
  onSave: () => void;
}

export function ConsumptionMetadataForm({
  inventoryItem,
  onClose,
  onSave,
}: ConsumptionMetadataFormProps) {
  const [editedMetadata, setEditedMetadata] = useState<ConsumptionMetadata | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [status, setStatus] = useState<{ type: "success" | "error"; message: string } | null>(
    null
  );

  useEffect(() => {
    if (!status) return;
    if (status.type === "success") {
      toast.success(status.message);
      return;
    }
    toast.error(status.message);
  }, [status]);

  useEffect(() => {
    const fetchMetadata = async () => {
      try {
        setLoading(true);
        const response = await fetch(`/api/inventory/${inventoryItem.id}/consumption-metadata`);
        if (response.ok) {
          const data = await response.json() as ConsumptionMetadata;
          setEditedMetadata(data);
        } else {
          // Metadata not found, initialize with defaults
          const defaults = {
            id: 0,
            inventoryItemId: inventoryItem.id,
            leadTimeDays: 3,
            safetyStockFactor: "1.5",
            weeksLookback: 4,
          };
          setEditedMetadata(defaults);
        }
      } catch {
        setStatus({
          type: "error",
          message: "Metadaten konnten nicht geladen werden",
        });
      } finally {
        setLoading(false);
      }
    };

    void fetchMetadata();
  }, [inventoryItem.id]);

  const handleSave = async () => {
    if (!editedMetadata) return;

    try {
      setSaving(true);
      const response = await fetch(`/api/inventory/${inventoryItem.id}/consumption-metadata`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          leadTimeDays: editedMetadata.leadTimeDays,
          safetyStockFactor: Number(editedMetadata.safetyStockFactor),
          weeksLookback: editedMetadata.weeksLookback,
        }),
      });

      if (!response.ok) {
        const error = await response.json() as { message?: string };
        throw new Error(error.message || "Speichern fehlgeschlagen");
      }

      setStatus({ type: "success", message: "✓ Gespeichert" });
      setTimeout(() => {
        onSave();
        onClose();
      }, 1000);
    } catch (err) {
      setStatus({
        type: "error",
        message: err instanceof Error ? err.message : "Fehler beim Speichern",
      });
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50">
        <Card className="w-full max-w-md">
          <CardContent className="p-6">Lädt...</CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50">
      <Card className="w-full max-w-2xl max-h-[90vh] overflow-y-auto">
        <CardHeader className="flex flex-row items-start justify-between space-y-0 pb-4">
          <div>
            <CardTitle>Lieferzeitraum & Parameter</CardTitle>
            <CardDescription className="mt-1">{inventoryItem.name}</CardDescription>
          </div>
          <Button variant="ghost" size="sm" onClick={onClose}>
            <X className="h-4 w-4" />
          </Button>
        </CardHeader>

        <CardContent className="space-y-6">
          {status && (
            <div
              className={`flex items-center gap-2 rounded border p-3 text-sm ${
                status.type === "success"
                  ? "border-emerald-500/40 bg-emerald-500/15 text-emerald-100"
                  : "border-red-500/40 bg-red-500/15 text-red-100"
              }`}
            >
              {status.type === "success" ? (
                <CheckCircle className="h-4 w-4" />
              ) : (
                <AlertCircle className="h-4 w-4" />
              )}
              {status.message}
            </div>
          )}

          {editedMetadata && (
            <div className="space-y-6">
              {/* Lead Time */}
              <div className="space-y-3 rounded-lg border border-orange-500/30 bg-orange-500/10 p-4">
                <Label htmlFor="leadTime" className="text-base font-semibold">
                  Lieferzeitraum (Tage)
                </Label>
                <p className="text-sm text-muted-foreground">
                  Wie lange dauert es normalerweise, bis eine Bestellung ankommt?
                </p>
                <div className="flex items-center gap-2">
                  <Input
                    id="leadTime"
                    type="number"
                    min="1"
                    max="30"
                    value={editedMetadata.leadTimeDays}
                    onChange={(e) =>
                      setEditedMetadata({
                        ...editedMetadata,
                        leadTimeDays: parseInt(e.target.value, 10),
                      })
                    }
                    className="w-24"
                  />
                  <span className="text-sm text-muted-foreground">Tage</span>
                </div>
                <div className="rounded bg-background/70 p-3 text-sm text-foreground">
                  <strong>Umgerechnet:</strong> {(editedMetadata.leadTimeDays / 7).toFixed(2)}{" "}
                  Wochen
                </div>
              </div>

              {/* Safety Stock Factor */}
              <div className="space-y-3 rounded-lg border border-amber-500/30 bg-amber-500/10 p-4">
                <Label htmlFor="safetyFactor" className="text-base font-semibold">
                  Sicherheitsbestands-Faktor
                </Label>
                <p className="text-sm text-muted-foreground">
                  Zusätzlicher Puffer als Multiplikator des Wochenumsatzes
                </p>
                <div className="flex items-center gap-2">
                  <Input
                    id="safetyFactor"
                    type="number"
                    step="0.1"
                    min="0.5"
                    max="5"
                    value={editedMetadata.safetyStockFactor}
                    onChange={(e) =>
                      setEditedMetadata({
                        ...editedMetadata,
                        safetyStockFactor: e.target.value,
                      })
                    }
                    className="w-24"
                  />
                  <span className="text-sm text-muted-foreground">×</span>
                </div>
                <div className="space-y-1 rounded bg-background/70 p-3 text-sm text-foreground">
                  <div>
                    <strong>1.0:</strong> Minimal - Genau der wöchentliche Umsatz
                  </div>
                  <div>
                    <strong>1.5:</strong> Standard - 50% Puffer
                  </div>
                  <div>
                    <strong>2.0+:</strong> Höher - Für volatile/unsichere Getränke
                  </div>
                </div>
              </div>

              {/* Weeks Lookback */}
              <div className="space-y-3 rounded-lg border border-cyan-500/30 bg-cyan-500/10 p-4">
                <Label htmlFor="weeksLookback" className="text-base font-semibold">
                  Lookback-Periode (Wochen)
                </Label>
                <p className="text-sm text-muted-foreground">
                  Zeitraum für die Berechnung des durchschnittlichen Umsatzes
                </p>
                <div className="flex items-center gap-2">
                  <Input
                    id="weeksLookback"
                    type="number"
                    min="1"
                    max="52"
                    value={editedMetadata.weeksLookback}
                    onChange={(e) =>
                      setEditedMetadata({
                        ...editedMetadata,
                        weeksLookback: parseInt(e.target.value, 10),
                      })
                    }
                    className="w-24"
                  />
                  <span className="text-sm text-muted-foreground">Wochen</span>
                </div>
                <div className="space-y-1 rounded bg-background/70 p-3 text-sm text-foreground">
                  <div>
                    <strong>&lt;4 Wochen:</strong> Schnell reagierend, aber anfällig für
                    Schwankungen
                  </div>
                  <div>
                    <strong>4 Wochen:</strong> Standard-Empfehlung
                  </div>
                  <div>
                    <strong>&gt;4 Wochen:</strong> Glätten von Spitzen, langfristige Trends
                  </div>
                </div>
              </div>

              {/* Current Stock Info */}
              <div className="rounded-lg border border-blue-500/30 bg-blue-500/10 p-4">
                <div className="space-y-2 text-sm">
                  <div>
                    <span className="text-muted-foreground">Aktueller Bestand:</span>{" "}
                    <strong>{inventoryItem.totalStockAmount}</strong> {inventoryItem.contentUnit}
                  </div>
                  <div>
                    <span className="text-muted-foreground">Schwellwert:</span>{" "}
                    <strong>{inventoryItem.reorderThreshold}</strong> {inventoryItem.contentUnit}
                  </div>
                  <div>
                    <span className="text-muted-foreground">Lieferant:</span>{" "}
                    <strong>{inventoryItem.supplier || "Nicht angegeben"}</strong>
                  </div>
                </div>
              </div>
            </div>
          )}
        </CardContent>

        <div className="border-t px-6 py-4 flex gap-3 justify-end">
          <Button variant="outline" onClick={onClose}>
            Abbrechen
          </Button>
          <Button onClick={handleSave} disabled={saving} className="gap-2">
            <Save className="h-4 w-4" />
            {saving ? "Speichert..." : "Speichern"}
          </Button>
        </div>
      </Card>
    </div>
  );
}

interface ItemConfigListProps {
  inventoryItems: InventoryItem[];
  onRefresh: () => void;
}

export function ItemConfigurationList({ inventoryItems, onRefresh }: ItemConfigListProps) {
  const [selectedItem, setSelectedItem] = useState<InventoryItem | null>(null);
  const [metadata, setMetadata] = useState<Record<number, ConsumptionMetadata | null>>({});
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchAllMetadata = async () => {
      try {
        setLoading(true);
        const metadataMap: Record<number, ConsumptionMetadata | null> = {};

        for (const item of inventoryItems) {
          try {
            const response = await fetch(`/api/inventory/${item.id}/consumption-metadata`);
            if (response.ok) {
              metadataMap[item.id] = await response.json() as ConsumptionMetadata;
            }
          } catch {
            metadataMap[item.id] = null;
          }
        }

        setMetadata(metadataMap);
      } finally {
        setLoading(false);
      }
    };

    if (inventoryItems.length > 0) {
      void fetchAllMetadata();
    }
  }, [inventoryItems]);

  if (loading) {
    return <div className="text-center text-muted-foreground">Lädt Konfigurationen...</div>;
  }

  return (
    <>
      <div className="space-y-3">
        {inventoryItems.length === 0 ? (
          <Card className="border-dashed">
            <CardContent className="p-8 text-center text-muted-foreground">
              Keine Lagerartikel vorhanden
            </CardContent>
          </Card>
        ) : (
          inventoryItems.map((item) => {
            const itemMetadata = metadata[item.id];
            const hasSeparateConfig = itemMetadata !== null;

            return (
              <Card key={item.id} className="hover:border-cyan-500/50 transition-colors">
                <CardContent className="p-4">
                  <div className="flex items-start justify-between">
                    <div className="flex-1">
                      <div className="flex items-center gap-2 mb-2">
                        <h4 className="font-semibold">{item.name}</h4>
                        {hasSeparateConfig && (
                          <Badge className="bg-cyan-100 text-cyan-800">Konfiguriert</Badge>
                        )}
                      </div>
                      <div className="space-y-1 text-sm text-muted-foreground">
                        <div>
                          Bestand: <strong>{item.totalStockAmount}</strong> {item.contentUnit}
                        </div>
                        {itemMetadata && (
                          <>
                            <div>
                              Lieferzeitraum: <strong>{itemMetadata.leadTimeDays} Tage</strong>
                            </div>
                            <div>
                              Sicherheitsfaktor:{" "}
                              <strong>
                                {Number(itemMetadata.safetyStockFactor).toFixed(1)}
                              </strong>
                              ×
                            </div>
                          </>
                        )}
                      </div>
                    </div>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setSelectedItem(item)}
                      className="gap-2"
                    >
                      {hasSeparateConfig ? "Bearbeiten" : <Plus className="h-4 w-4" />}
                    </Button>
                  </div>
                </CardContent>
              </Card>
            );
          })
        )}
      </div>

      {selectedItem && (
        <ConsumptionMetadataForm
          inventoryItem={selectedItem}
          onClose={() => setSelectedItem(null)}
          onSave={() => {
            onRefresh();
          }}
        />
      )}
    </>
  );
}

