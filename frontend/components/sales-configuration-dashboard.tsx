"use client";

import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import {
  Settings,
  Save,
  AlertCircle,
  CheckCircle,
  Clock,
  Shield,
  TrendingDown,
  RotateCw,
} from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import type { SalesConfiguration } from "@/types/api";

export function SalesConfigurationDashboard() {
  const [config, setConfig] = useState<SalesConfiguration | null>(null);
  const [editedConfig, setEditedConfig] = useState<SalesConfiguration | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [status, setStatus] = useState<{
    type: "success" | "error" | "info";
    message: string;
  } | null>(null);

  useEffect(() => {
    if (!status) return;
    if (status.type === "success") {
      toast.success(status.message);
      return;
    }
    if (status.type === "error") {
      toast.error(status.message);
      return;
    }
    toast(status.message);
  }, [status]);

  useEffect(() => {
    const normalizeConfig = (raw: SalesConfiguration): SalesConfiguration => ({
      ...raw,
      businessDayEndsAt: raw.businessDayEndsAt?.slice(0, 5) ?? "05:00",
    });

    const fetchConfig = async () => {
      try {
        setLoading(true);
        const response = await fetch("/api/inventory/configuration");
        if (!response.ok) throw new Error("Konfiguration konnte nicht geladen werden");
        const data = normalizeConfig(await response.json() as SalesConfiguration);
        setConfig(data);
        setEditedConfig(data);
      } catch (err) {
        setStatus({
          type: "error",
          message: err instanceof Error ? err.message : "Fehler beim Laden der Konfiguration",
        });
      } finally {
        setLoading(false);
      }
    };

    void fetchConfig();
  }, []);

  const handleSave = async () => {
    if (!editedConfig) return;

    try {
      setSaving(true);
      const response = await fetch("/api/inventory/configuration", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(editedConfig),
      });

      if (!response.ok) {
        const error = await response.json() as { message?: string };
        throw new Error(error.message || "Speichern fehlgeschlagen");
      }

      const data = await response.json() as SalesConfiguration;
      const normalized = {
        ...data,
        businessDayEndsAt: data.businessDayEndsAt?.slice(0, 5) ?? "05:00",
      };
      setConfig(normalized);
      setEditedConfig(normalized);
      setStatus({
        type: "success",
        message: "✓ Konfiguration erfolgreich gespeichert",
      });

      setTimeout(() => setStatus(null), 3000);
    } catch (err) {
      setStatus({
        type: "error",
        message: err instanceof Error ? err.message : "Fehler beim Speichern",
      });
    } finally {
      setSaving(false);
    }
  };

  const handleManualDayClose = async () => {
    try {
      setSaving(true);
      const response = await fetch("/api/inventory/configuration/manual-day-close", {
        method: "POST",
      });
      if (!response.ok) {
        const error = await response.json().catch(() => ({})) as { message?: string };
        throw new Error(error.message || "Manueller Tagesabschluss fehlgeschlagen");
      }
      const data = await response.json() as SalesConfiguration;
      const normalized = {
        ...data,
        businessDayEndsAt: data.businessDayEndsAt?.slice(0, 5) ?? "05:00",
      };
      setConfig(normalized);
      setEditedConfig(normalized);
      setStatus({ type: "success", message: "✓ Geschäftstag manuell abgeschlossen" });
      setTimeout(() => setStatus(null), 3000);
    } catch (err) {
      setStatus({
        type: "error",
        message: err instanceof Error ? err.message : "Fehler beim Tagesabschluss",
      });
    } finally {
      setSaving(false);
    }
  };

  const handleReset = () => {
    setEditedConfig(config);
    setStatus({
      type: "info",
      message: "Änderungen verworfen",
    });
    setTimeout(() => setStatus(null), 2000);
  };

  const hasChanges =
    JSON.stringify(config) !== JSON.stringify(editedConfig);

  if (loading) {
    return <div className="flex items-center justify-center p-8">Lädt Konfiguration...</div>;
  }

  if (!editedConfig) {
    return (
      <div className="rounded border border-red-500/40 bg-red-500/15 p-4 text-red-100">
        Konfiguration konnte nicht geladen werden
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Status Message */}
      {status && (
        <div
          className={`flex items-center gap-2 rounded-lg border p-4 ${
            status.type === "success"
              ? "border-emerald-500/40 bg-emerald-500/15 text-emerald-100"
              : status.type === "error"
                ? "border-red-500/40 bg-red-500/15 text-red-100"
                : "border-blue-500/40 bg-blue-500/15 text-blue-100"
          }`}
        >
          {status.type === "success" ? (
            <CheckCircle className="h-5 w-5" />
          ) : status.type === "error" ? (
            <AlertCircle className="h-5 w-5" />
          ) : (
            <AlertCircle className="h-5 w-5" />
          )}
          <span>{status.message}</span>
        </div>
      )}

      {/* Main Configuration Card */}
      <Card className="border-2 border-cyan-500/30 bg-gradient-to-br from-cyan-500/10 to-blue-500/10">
        <CardHeader>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <Settings className="h-6 w-6 text-cyan-400" />
              <div>
                <CardTitle>Nachbestellberechnung</CardTitle>
                <CardDescription>Konfigurieren Sie die Parameter für die Nachbestellberechnung</CardDescription>
              </div>
            </div>
            <Badge variant="muted" className="bg-cyan-400/10">
              Aktiv
            </Badge>
          </div>
        </CardHeader>

        <CardContent className="space-y-8">
          {/* Business Day Settings */}
          <div className="space-y-4 rounded-lg border border-violet-500/30 bg-violet-500/10 p-4">
            <div className="flex items-start gap-3">
              <Clock className="mt-1 h-5 w-5 text-violet-300" />
              <div className="flex-1 space-y-3">
                <Label htmlFor="businessTimezone" className="text-base font-semibold">
                  Geschäftstag & Zeitzone
                </Label>
                <p className="text-sm text-muted-foreground">
                  Für Nachtgeschäft: Definiert, wann ein neuer Geschäftstag beginnt.
                </p>

                <div className="grid gap-3 sm:grid-cols-2">
                  <div className="space-y-2">
                    <Label htmlFor="businessTimezone">Zeitzone</Label>
                    <Input
                      id="businessTimezone"
                      value={editedConfig.businessTimezone}
                      onChange={(e) =>
                        setEditedConfig({
                          ...editedConfig,
                          businessTimezone: e.target.value,
                        })
                      }
                      placeholder="Europe/Berlin"
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="businessDayEndsAt">Tag endet um</Label>
                    <Input
                      id="businessDayEndsAt"
                      type="time"
                      value={editedConfig.businessDayEndsAt}
                      onChange={(e) =>
                        setEditedConfig({
                          ...editedConfig,
                          businessDayEndsAt: e.target.value,
                        })
                      }
                    />
                  </div>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="manualBusinessDate">Manueller Geschäftstag (optional)</Label>
                  <Input
                    id="manualBusinessDate"
                    type="date"
                    value={editedConfig.manualBusinessDate ?? ""}
                    onChange={(e) =>
                      setEditedConfig({
                        ...editedConfig,
                        manualBusinessDate: e.target.value || null,
                      })
                    }
                  />
                </div>

                <div className="rounded bg-violet-500/15 p-3 text-sm text-foreground">
                  <p><strong>Aktiver Geschäftstag:</strong> {editedConfig.effectiveBusinessDate}</p>
                  <p>
                    Vor {editedConfig.businessDayEndsAt} Uhr zählt Umsatz noch zum Vortag.
                  </p>
                </div>

                <Button
                  type="button"
                  variant="secondary"
                  className="w-full"
                  onClick={handleManualDayClose}
                  disabled={saving}
                >
                  Tagesabschluss manuell (+1 Tag)
                </Button>
              </div>
            </div>
          </div>

          {/* Weeks Lookback */}
          <div className="space-y-4 rounded-lg border border-cyan-500/30 bg-cyan-500/10 p-4">
            <div className="flex items-start gap-3">
              <TrendingDown className="h-5 w-5 text-cyan-400 mt-1" />
              <div className="flex-1">
                <Label htmlFor="weeksLookback" className="text-base font-semibold">
                  Lookback-Periode (Wochen)
                </Label>
                <p className="mt-1 text-sm text-muted-foreground">
                  Wie viele Wochen sollen für die Durchschnittsberechnung herangezogen werden?
                </p>
                <div className="mt-3 flex items-center gap-2">
                  <Input
                    id="weeksLookback"
                    type="number"
                    min="1"
                    max="52"
                    value={editedConfig.weeksLookback}
                    onChange={(e) =>
                      setEditedConfig({
                        ...editedConfig,
                        weeksLookback: parseInt(e.target.value, 10),
                      })
                    }
                    className="w-24"
                  />
                  <span className="text-sm text-muted-foreground">Wochen</span>
                </div>
                <div className="mt-3 rounded bg-cyan-500/15 p-3 text-sm text-foreground">
                  <strong>Aktuell:</strong> Der Durchschnitt basiert auf den letzten{" "}
                  <strong>{editedConfig.weeksLookback} Wochen</strong>
                  {editedConfig.weeksLookback === 4 && " (Standardempfehlung)"}
                  {editedConfig.weeksLookback > 4 &&
                    " - Längeres Fenster, glättet Spitzen"}
                  {editedConfig.weeksLookback < 4 &&
                    " - Kürzeres Fenster, reagiert schneller auf Änderungen"}
                </div>
              </div>
            </div>
          </div>

          {/* Safety Stock Factor */}
          <div className="space-y-4 rounded-lg border border-amber-500/30 bg-amber-500/10 p-4">
            <div className="flex items-start gap-3">
              <Shield className="h-5 w-5 text-amber-400 mt-1" />
              <div className="flex-1">
                <Label htmlFor="safetyFactor" className="text-base font-semibold">
                  Sicherheitsbestands-Faktor
                </Label>
                <p className="mt-1 text-sm text-muted-foreground">
                  Multiplikator für den Sicherheitsbestand (Puffer für Notfälle)
                </p>
                <div className="mt-3 flex items-center gap-2">
                  <Input
                    id="safetyFactor"
                    type="number"
                    step="0.1"
                    min="0.5"
                    max="5"
                    value={editedConfig.defaultSafetyFactor}
                    onChange={(e) =>
                      setEditedConfig({
                        ...editedConfig,
                        defaultSafetyFactor: parseFloat(e.target.value),
                      })
                    }
                    className="w-24"
                  />
                  <span className="text-sm text-muted-foreground">×</span>
                </div>
                <div className="mt-3 space-y-2 rounded bg-amber-500/15 p-3 text-sm text-foreground">
                  <div>
                    <strong>Aktuell:</strong> {editedConfig.defaultSafetyFactor.toFixed(1)}×
                    {editedConfig.defaultSafetyFactor === 1.5 && " (Standardempfehlung)"}
                  </div>
                  <div>
                    <strong>Beispiel:</strong> Bei 1000 ml/Woche Verbrauch = Sicherheitsbestand{" "}
                    {(1000 * parseFloat(editedConfig.defaultSafetyFactor.toString())).toFixed(0)} ml
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Lead Time */}
          <div className="space-y-4 rounded-lg border border-orange-500/30 bg-orange-500/10 p-4">
            <div className="flex items-start gap-3">
              <Clock className="h-5 w-5 text-orange-400 mt-1" />
              <div className="flex-1">
                <Label htmlFor="leadTime" className="text-base font-semibold">
                  Standard-Lieferzeitraum (Tage)
                </Label>
                <p className="mt-1 text-sm text-muted-foreground">
                  Typische Lieferdauer vom Lieferanten (Tage bis zur Zustellung)
                </p>
                <div className="mt-3 flex items-center gap-2">
                  <Input
                    id="leadTime"
                    type="number"
                    min="1"
                    max="30"
                    value={editedConfig.defaultLeadTimeDays}
                    onChange={(e) =>
                      setEditedConfig({
                        ...editedConfig,
                        defaultLeadTimeDays: parseInt(e.target.value, 10),
                      })
                    }
                    className="w-24"
                  />
                  <span className="text-sm text-muted-foreground">Tage</span>
                </div>
                <div className="mt-3 space-y-2 rounded bg-orange-500/15 p-3 text-sm text-foreground">
                  <div>
                    <strong>Aktuell:</strong> {editedConfig.defaultLeadTimeDays} Tage
                    {editedConfig.defaultLeadTimeDays === 3 && " (Standardempfehlung)"}
                  </div>
                  <div>
                    <strong>Berechnung:</strong> {editedConfig.defaultLeadTimeDays} Tage = ~
                    {(editedConfig.defaultLeadTimeDays / 7).toFixed(2)} Wochen
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Calculation Example */}
          <div className="space-y-3 rounded-lg border border-cyan-500/30 bg-cyan-500/10 p-4">
            <h4 className="font-semibold text-cyan-200">
              📊 Berechnungsbeispiel (mit aktuellen Parametern)
            </h4>
            <div className="space-y-2 text-sm text-foreground">
              <div>
                Angenommen: Getränk mit 2.000 ml/Woche Durchschnittsumsatz, aktueller Bestand
                500 ml
              </div>
              <div className="space-y-1 rounded bg-background/70 p-3 font-mono text-xs text-foreground">
                <div>
                  Lead-Time Verbrauch = 2.000 × ({editedConfig.defaultLeadTimeDays} ÷ 7) ={" "}
                  {(2000 * (editedConfig.defaultLeadTimeDays / 7)).toFixed(0)} ml
                </div>
                <div>
                  Sicherheitsbestand = 2.000 × {editedConfig.defaultSafetyFactor} ={" "}
                  {(2000 * editedConfig.defaultSafetyFactor).toFixed(0)} ml
                </div>
                <div className="border-t border-cyan-500/30 pt-1 mt-1">
                  <strong>Empfohlene Menge =</strong>{" "}
                  {(
                    2000 * (editedConfig.defaultLeadTimeDays / 7) +
                    2000 * editedConfig.defaultSafetyFactor -
                    500
                  ).toFixed(0)}{" "}
                  ml
                </div>
              </div>
            </div>
          </div>
        </CardContent>

        {/* Actions */}
        <div className="border-t border-cyan-500/20 px-6 py-4 flex gap-3 justify-end">
          <Button
            variant="outline"
            onClick={handleReset}
            disabled={!hasChanges || saving}
            className="gap-2"
          >
            <RotateCw className="h-4 w-4" />
            Zurücksetzen
          </Button>
          <Button
            onClick={handleSave}
            disabled={!hasChanges || saving}
            className="gap-2 bg-cyan-500 hover:bg-cyan-600"
          >
            <Save className="h-4 w-4" />
            {saving ? "Speichert..." : "Speichern"}
          </Button>
        </div>
      </Card>

      {/* Info Card */}
      <Card className="border-blue-500/30 bg-blue-500/10">
        <CardHeader>
          <CardTitle className="text-base flex items-center gap-2">
            <AlertCircle className="h-5 w-5 text-blue-400" />
            Hinweise
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-2 text-sm text-muted-foreground">
          <p>
            ✓ Diese Parameter werden für alle Artikel als Standard verwendet, können aber pro
            Artikel überschrieben werden
          </p>
          <p>
            ✓ Änderungen werden sofort wirksam, die nächste automatische Berechnung findet um
            05:30 Europe/Berlin statt
          </p>
          <p>
            ✓ Mit &quot;Tag endet um&quot; steuern Sie, ob Umsätze nachts noch zum Vortag zählen
          </p>
          <p>
            ✓ &quot;Tagesabschluss manuell&quot; setzt den Geschäftstag aktiv auf den nächsten Tag
          </p>
          <p>
            ✓ Für spezifische Artikel: Nutzen Sie &quot;Konsumptions-Metadaten&quot; um individuelle
            Parameter festzulegen
          </p>
          <p>
            ✓ Höhere Lookback-Periode = glattere Kurven, aber langsamer auf Änderungen reagierend
          </p>
        </CardContent>
      </Card>
    </div>
  );
}

