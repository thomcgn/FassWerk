"use client";


import { useMemo, useState } from "react";
import FullCalendar from "@fullcalendar/react";
import dayGridPlugin from "@fullcalendar/daygrid";
import timeGridPlugin from "@fullcalendar/timegrid";
import interactionPlugin from "@fullcalendar/interaction";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { CheckCircle2, X } from "lucide-react";
import { Label } from "@/components/ui/label";
import type { Reservation } from "@/types/api";

type ReservationStatus = "PENDING" | "CONFIRMED" | "CHECKED_IN" | "REJECTED" | "CANCELLED" | "NO_SHOW";

interface FullCalendarBookingsProps {
  reservations: Reservation[];
  setDateFilter: (date: string) => void;
  onAction: (id: number, action: "confirm" | "check-in" | "cancel") => void;
  cancellationModal: { id: number; reason: string } | null;
  setCancellationModal: (modal: { id: number; reason: string } | null) => void;
  onConfirmCancellation: () => Promise<void>;
  loading: boolean;
}

function statusVariant(status: ReservationStatus): "success" | "warning" | "destructive" | "muted" {
  if (status === "CHECKED_IN") return "success";
  if (status === "PENDING" || status === "CONFIRMED") return "warning";
  if (status === "REJECTED" || status === "CANCELLED" || status === "NO_SHOW") return "destructive";
  return "muted";
}

function toLocalDateTime(date: string, time: string): string {
  const parsed = new Date(`${date}T${time}`);
  if (Number.isNaN(parsed.getTime())) return `${date} ${time}`;
  return parsed.toLocaleString("de-DE", { hour: "2-digit", minute: "2-digit", day: "2-digit", month: "2-digit" });
}

function groupReservationsByStatus(reservations: Reservation[]): Record<ReservationStatus, Reservation[]> {
  const grouped: Record<ReservationStatus, Reservation[]> = {
    CONFIRMED: [],
    PENDING: [],
    CHECKED_IN: [],
    REJECTED: [],
    CANCELLED: [],
    NO_SHOW: [],
  };

  reservations.forEach((res) => {
    const status = res.status as ReservationStatus;
    if (grouped[status]) {
      grouped[status].push(res);
    }
  });

  return grouped;
}

export function FullCalendarBookings({
  reservations,
  setDateFilter,
  onAction,
  cancellationModal,
  setCancellationModal,
  onConfirmCancellation,
  loading,
}: FullCalendarBookingsProps) {
  const [viewFilter, setViewFilter] = useState<"OPEN" | "REJECTED" | "ALL">("ALL");

  const calendarEvents = useMemo(
    () =>
      reservations.map((res) => ({
        id: String(res.id),
        title: `${res.guestName} (${res.guestCount})`,
        date: res.reservationDate,
        backgroundColor: 
          res.status === "CHECKED_IN" ? "#10b981" :
          res.status === "PENDING" || res.status === "CONFIRMED" ? "#f59e0b" :
          res.status === "REJECTED" || res.status === "CANCELLED" ? "#ef4444" : "#6b7280",
        borderColor: 
          res.status === "CHECKED_IN" ? "#059669" :
          res.status === "PENDING" || res.status === "CONFIRMED" ? "#d97706" :
          res.status === "REJECTED" || res.status === "CANCELLED" ? "#dc2626" : "#374151",
        extendedProps: { reservation: res },
      })),
    [reservations],
  );

  // Offene Reservierungen für Aktionen
  const actionableReservations = useMemo(
    () => reservations.filter((res) => res.status === "PENDING" || res.status === "CONFIRMED"),
    [reservations],
  );

  // Abgelehnte Reservierungen separat als read-only Liste
  const rejectedReservations = useMemo(
    () => reservations.filter((res) => res.status === "REJECTED"),
    [reservations],
  );

  const grouped = useMemo(() => groupReservationsByStatus(actionableReservations), [actionableReservations]);
  const hasOpenEntries = grouped.CONFIRMED.length > 0 || grouped.PENDING.length > 0;
  const hasRejectedEntries = rejectedReservations.length > 0;
  const openCount = actionableReservations.length;
  const rejectedCount = rejectedReservations.length;
  const allCount = openCount + rejectedCount;

  return (
    <div className="grid gap-5 lg:grid-cols-[1fr_350px]">
      {/* Calendar */}
      <Card className="overflow-hidden">
        <CardHeader>
          <CardTitle>Reservierungs-Kalender</CardTitle>
          <CardDescription>Klicken Sie auf ein Datum, um Reservierungen zu sehen.</CardDescription>
        </CardHeader>
        <CardContent className="p-0">
          <div className="fc-light [&_.fc]:bg-transparent [&_.fc-button-primary]:bg-cyan-500 [&_.fc-button-primary:hover]:bg-cyan-600 [&_.fc-button-primary:active]:bg-cyan-700 [&_.fc-button-primary.fc-button-active]:bg-cyan-600">
            <FullCalendar
              plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]}
              initialView="dayGridMonth"
              headerToolbar={{
                left: "prev,next today",
                center: "title",
                right: "dayGridMonth",
              }}
              events={calendarEvents}
              dateClick={(info) => setDateFilter(info.dateStr)}
              height="auto"
            />
          </div>
        </CardContent>
      </Card>

      {/* Reservation List */}
      <Card className="flex flex-col overflow-hidden">
        <CardHeader>
          <CardTitle className="text-lg">Offene Reservierungen</CardTitle>
          <CardDescription className="text-xs">ab heute</CardDescription>
          <div className="mt-2 flex gap-2">
            <Button
              type="button"
              size="sm"
              variant={viewFilter === "OPEN" ? "default" : "outline"}
              onClick={() => setViewFilter("OPEN")}
            >
              Offen ({openCount})
            </Button>
            <Button
              type="button"
              size="sm"
              variant={viewFilter === "REJECTED" ? "default" : "outline"}
              onClick={() => setViewFilter("REJECTED")}
            >
              Abgelehnt ({rejectedCount})
            </Button>
            <Button
              type="button"
              size="sm"
              variant={viewFilter === "ALL" ? "default" : "outline"}
              onClick={() => setViewFilter("ALL")}
            >
              Alle ({allCount})
            </Button>
          </div>
        </CardHeader>
        <CardContent className="flex-1 overflow-y-auto space-y-2 pr-2">
          {loading ? (
            <p className="text-sm text-[color:var(--color-muted-foreground)]">Lade...</p>
          ) : actionableReservations.length === 0 && rejectedReservations.length === 0 ? (
            <p className="text-sm text-[color:var(--color-muted-foreground)]">Keine offenen Reservierungen</p>
          ) : (
            <div className="space-y-2">
              {(viewFilter === "OPEN" && !hasOpenEntries) ? (
                <p className="text-sm text-[color:var(--color-muted-foreground)]">Keine offenen Reservierungen.</p>
              ) : null}
              {(viewFilter === "REJECTED" && !hasRejectedEntries) ? (
                <p className="text-sm text-[color:var(--color-muted-foreground)]">Keine abgelehnten Reservierungen.</p>
              ) : null}

              {/* CONFIRMED */}
              {(viewFilter === "OPEN" || viewFilter === "ALL") && grouped.CONFIRMED.length > 0 && (
                <>
                  <p className="text-xs font-semibold text-[color:var(--color-muted-foreground)] uppercase tracking-wider mt-4">Bestätigt</p>
                  {grouped.CONFIRMED.map((res) => (
                    <ResCard key={res.id} reservation={res} onAction={onAction} onCancelClick={() => setCancellationModal({ id: res.id, reason: "" })} />
                  ))}
                </>
              )}

              {/* PENDING */}
              {(viewFilter === "OPEN" || viewFilter === "ALL") && grouped.PENDING.length > 0 && (
                <>
                  <p className="text-xs font-semibold text-[color:var(--color-muted-foreground)] uppercase tracking-wider mt-4">Ausstehend</p>
                  {grouped.PENDING.map((res) => (
                    <ResCard key={res.id} reservation={res} onAction={onAction} onCancelClick={() => setCancellationModal({ id: res.id, reason: "" })} />
                  ))}
                </>
              )}

              {/* REJECTED (read-only) */}
              {(viewFilter === "REJECTED" || viewFilter === "ALL") && rejectedReservations.length > 0 && (
                <>
                  <p className="text-xs font-semibold text-[color:var(--color-muted-foreground)] uppercase tracking-wider mt-4">Abgelehnt</p>
                  {rejectedReservations.map((res) => (
                    <ResCard key={res.id} reservation={res} readOnly onAction={onAction} onCancelClick={() => undefined} />
                  ))}
                </>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Cancellation Modal */}
      {cancellationModal ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <Card className="w-full max-w-sm">
            <CardHeader className="flex flex-row items-center justify-between gap-2">
              <CardTitle>Reservierung ablehnen</CardTitle>
              <button onClick={() => setCancellationModal(null)} className="text-[color:var(--color-muted-foreground)] hover:text-[color:var(--color-foreground)]">
                <X className="h-5 w-5" />
              </button>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-2">
                <Label>Begründung</Label>
                <textarea
                  value={cancellationModal.reason}
                  onChange={(e) => setCancellationModal({ ...cancellationModal, reason: e.target.value })}
                  placeholder="z. B. Kulanzstornierung, auf Wunsch des Gastes..."
                  className="flex min-h-24 w-full rounded-lg border border-[color:var(--color-border-strong)] bg-[color:var(--color-surface)]/75 backdrop-blur-sm px-4 py-2 text-sm text-[color:var(--color-foreground)] transition-all outline-none placeholder:text-[color:var(--color-muted-foreground)] focus-visible:border-cyan-500/60 focus-visible:ring-4 focus-visible:ring-cyan-500/25"
                />
              </div>
              <div className="flex gap-2">
                <Button className="flex-1" variant="destructive" onClick={() => void onConfirmCancellation()} disabled={!cancellationModal.reason.trim()}>
                  Ablehnen
                </Button>
                <Button className="flex-1" variant="outline" onClick={() => setCancellationModal(null)}>
                  Abbrechen
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      ) : null}
    </div>
  );
}

function ResCard({
  reservation,
  onAction,
  onCancelClick,
  readOnly,
}: {
  reservation: Reservation;
  onAction: (id: number, action: "confirm" | "check-in" | "cancel") => void;
  onCancelClick: () => void;
  readOnly?: boolean;
}) {
  const variant = statusVariant(reservation.status as ReservationStatus);
  const isPending = reservation.status === "PENDING";
  const canCheckIn = reservation.status === "CONFIRMED";
  const isReadOnly = readOnly ?? false;

  return (
    <div className="rounded-lg border border-cyan-500/20 bg-cyan-500/5 p-2.5 text-xs">
      <div className="flex items-start justify-between gap-2 mb-2">
        <div className="flex-1">
          <p className="font-semibold text-[color:var(--color-foreground)]">{reservation.guestName}</p>
          <p className="text-[color:var(--color-muted-foreground)]">{toLocalDateTime(reservation.reservationDate, reservation.reservationTime)} · {reservation.guestCount}P</p>
        </div>
        <Badge variant={variant} className="shrink-0">{reservation.status}</Badge>
      </div>
      {!isReadOnly ? (
        <div className="flex gap-1.5">
          {isPending && (
            <Button size="sm" variant="default" onClick={() => void onAction(reservation.id, "confirm")} className="flex-1 h-7 text-xs">
              <CheckCircle2 className="h-3 w-3" />
              Bestätigen
            </Button>
          )}
          {canCheckIn && (
            <Button size="sm" variant="default" onClick={() => void onAction(reservation.id, "check-in")} className="flex-1 h-7 text-xs">
              <CheckCircle2 className="h-3 w-3" />
              Check-in
            </Button>
          )}
          <Button size="sm" variant="destructive" onClick={onCancelClick} className="flex-1 h-7 text-xs">
            Ablehnen
          </Button>
        </div>
      ) : null}
    </div>
  );
}

