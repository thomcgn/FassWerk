"use client";

import { useEffect, useState } from "react";
import { Calendar, Clock, Package, AlertCircle, CheckCircle, Plus, Truck } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import type { InventoryItem } from "@/types/api";

interface Supplier {
  id: number;
  name: string;
  contactEmail: string;
  contactPhone: string;
  website: string;
  notes: string;
  active: boolean;
}

interface ReorderOrder {
  id: number;
  inventoryItemId: number;
  inventoryItemName: string;
  supplierId: number;
  supplierName: string;
  orderedQuantity: string;
  orderedUnit: string;
  scheduledDeliveryDate: string;
  scheduledDeliveryTime: string | null;
  status: string;
  notes: string;
  receivedQuantity: string | null;
  receivedAt: string | null;
  createdBy: string;
  createdAt: string;
}

interface CreateReorderState {
  supplierId: number;
  orderedQuantity: string;
  scheduledDeliveryDate: string;
  scheduledDeliveryTime: string;
  notes: string;
}

export function ReorderDashboard({ inventoryItem }: { inventoryItem: InventoryItem }) {
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [reorders, setReorders] = useState<ReorderOrder[]>([]);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [formState, setFormState] = useState<CreateReorderState>({
    supplierId: 0,
    orderedQuantity: "",
    scheduledDeliveryDate: new Date().toISOString().split("T")[0],
    scheduledDeliveryTime: "09:00",
    notes: "",
  });

  useEffect(() => {
    const loadData = async () => {
      try {
        setLoading(true);
        const [suppliersRes, reordersRes] = await Promise.all([
          fetch("/api/reorder/suppliers?onlyActive=true", { cache: "no-store" }),
          fetch(`/api/reorder/orders/inventory/${inventoryItem.id}`, { cache: "no-store" }),
        ]);

        if (!suppliersRes.ok || !reordersRes.ok) {
          throw new Error("Daten konnten nicht geladen werden");
        }

        const [supp, reord] = await Promise.all([
          suppliersRes.json(),
          reordersRes.json(),
        ]);

        setSuppliers(supp);
        setReorders(reord);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Fehler beim Laden");
      } finally {
        setLoading(false);
      }
    };

    void loadData();
  }, [inventoryItem.id]);

  const handleCreateReorder = async () => {
    if (!formState.supplierId || !formState.orderedQuantity || !formState.scheduledDeliveryDate) {
      setError("Bitte alle erforderlichen Felder ausfüllen");
      return;
    }

    try {
      setIsCreating(true);
      setError(null);

      const response = await fetch("/api/reorder/orders", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          inventoryItemId: inventoryItem.id,
          supplierId: formState.supplierId,
          orderedQuantity: Number(formState.orderedQuantity),
          orderedUnit: inventoryItem.contentUnit,
          scheduledDeliveryDate: formState.scheduledDeliveryDate,
          scheduledDeliveryTime: formState.scheduledDeliveryTime,
          notes: formState.notes,
        }),
      });

      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        throw new Error(data.message || "Nachbestellung konnte nicht erstellt werden");
      }

      const newReorder = await response.json();
      setReorders([newReorder, ...reorders]);
      setShowCreateForm(false);
      setFormState({
        supplierId: 0,
        orderedQuantity: "",
        scheduledDeliveryDate: new Date().toISOString().split("T")[0],
        scheduledDeliveryTime: "09:00",
        notes: "",
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Fehler beim Erstellen");
    } finally {
      setIsCreating(false);
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case "PENDING":
        return "bg-yellow-500/20 text-yellow-200 border-yellow-500/30";
      case "CONFIRMED":
        return "bg-blue-500/20 text-blue-200 border-blue-500/30";
      case "SHIPPED":
        return "bg-cyan-500/20 text-cyan-200 border-cyan-500/30";
      case "RECEIVED":
        return "bg-green-500/20 text-green-200 border-green-500/30";
      case "CANCELLED":
        return "bg-red-500/20 text-red-200 border-red-500/30";
      default:
        return "bg-gray-500/20 text-gray-200 border-gray-500/30";
    }
  };

  if (loading) {
    return <div className="text-sm text-muted-foreground">Lädt Nachbestellungen...</div>;
  }

  return (
    <Card className="border-amber-500/30 bg-amber-500/5">
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Truck className="h-5 w-5 text-amber-400" />
            <div>
              <CardTitle>Nachbestellungen für {inventoryItem.name}</CardTitle>
              <CardDescription>Liefertag, Uhrzeit und Lieferant tracken</CardDescription>
            </div>
          </div>
          <Button
            size="sm"
            onClick={() => setShowCreateForm(!showCreateForm)}
            className="bg-amber-600 hover:bg-amber-700"
          >
            <Plus className="h-4 w-4" />
            Neue Nachbestellung
          </Button>
        </div>
      </CardHeader>

      <CardContent className="space-y-4">
        {error && (
          <div className="flex items-center gap-2 rounded-lg bg-red-500/15 border border-red-500/30 p-3 text-sm text-red-100">
            <AlertCircle className="h-4 w-4" />
            {error}
          </div>
        )}

        {showCreateForm && (
          <div className="rounded-lg border border-amber-500/30 bg-amber-500/10 p-4 space-y-3">
            <h4 className="font-semibold text-sm">Neue Nachbestellung</h4>

            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-1">
                <Label htmlFor="supplier" className="text-xs">
                  Lieferant
                </Label>
                <select
                  id="supplier"
                  value={formState.supplierId}
                  onChange={(e) =>
                    setFormState({ ...formState, supplierId: Number(e.target.value) })
                  }
                  className="w-full rounded-lg border border-amber-500/40 bg-amber-500/5 px-3 py-2 text-sm"
                >
                  <option value={0}>-- Lieferant wählen --</option>
                  {suppliers.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name}
                    </option>
                  ))}
                </select>
              </div>

              <div className="space-y-1">
                <Label htmlFor="quantity" className="text-xs">
                  Menge ({inventoryItem.contentUnit})
                </Label>
                <Input
                  id="quantity"
                  type="number"
                  step="0.01"
                  value={formState.orderedQuantity}
                  onChange={(e) =>
                    setFormState({ ...formState, orderedQuantity: e.target.value })
                  }
                  className="h-9"
                  placeholder="z.B. 100"
                />
              </div>

              <div className="space-y-1">
                <Label htmlFor="deliveryDate" className="text-xs flex items-center gap-1">
                  <Calendar className="h-3 w-3" /> Liefertag
                </Label>
                <Input
                  id="deliveryDate"
                  type="date"
                  value={formState.scheduledDeliveryDate}
                  onChange={(e) =>
                    setFormState({ ...formState, scheduledDeliveryDate: e.target.value })
                  }
                  className="h-9"
                />
              </div>

              <div className="space-y-1">
                <Label htmlFor="deliveryTime" className="text-xs flex items-center gap-1">
                  <Clock className="h-3 w-3" /> Lieferuhrzeit
                </Label>
                <Input
                  id="deliveryTime"
                  type="time"
                  value={formState.scheduledDeliveryTime}
                  onChange={(e) =>
                    setFormState({ ...formState, scheduledDeliveryTime: e.target.value })
                  }
                  className="h-9"
                />
              </div>
            </div>

            <div className="space-y-1">
              <Label htmlFor="notes" className="text-xs">
                Bemerkungen
              </Label>
              <Input
                id="notes"
                value={formState.notes}
                onChange={(e) => setFormState({ ...formState, notes: e.target.value })}
                placeholder="z.B. Lieferadresse, Besonderheiten..."
                className="h-9"
              />
            </div>

            <div className="flex gap-2 justify-end pt-2">
              <Button
                size="sm"
                variant="outline"
                onClick={() => setShowCreateForm(false)}
              >
                Abbrechen
              </Button>
              <Button
                size="sm"
                onClick={() => void handleCreateReorder()}
                disabled={isCreating}
                className="bg-amber-600 hover:bg-amber-700"
              >
                {isCreating ? "Speichern..." : "Nachbestellung erstellen"}
              </Button>
            </div>
          </div>
        )}

        {reorders.length === 0 ? (
          <div className="text-center text-sm text-muted-foreground py-6">
            Noch keine Nachbestellungen geplant.
          </div>
        ) : (
          <div className="space-y-2 max-h-96 overflow-y-auto">
            {reorders.map((order) => (
              <div key={order.id} className="rounded-lg border border-amber-500/20 bg-amber-500/5 p-3 text-sm">
                <div className="flex items-start justify-between gap-2 mb-2">
                  <div className="flex-1">
                    <p className="font-semibold">
                      {order.supplierName} · {order.orderedQuantity} {order.orderedUnit}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      📅 {new Date(order.scheduledDeliveryDate).toLocaleDateString("de-DE")}
                      {order.scheduledDeliveryTime && ` · 🕒 ${order.scheduledDeliveryTime}`}
                    </p>
                    {order.notes && (
                      <p className="text-xs text-muted-foreground mt-1">Notiz: {order.notes}</p>
                    )}
                  </div>
                  <Badge
                    variant="muted"
                    className={`shrink-0 ${getStatusColor(order.status)}`}
                  >
                    {order.status}
                  </Badge>
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}


