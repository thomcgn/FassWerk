"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import {
  CalendarDays,
  CheckCircle2,
  Loader2,
  Mail,
  Phone,
  Plus,
  TicketX,
  Users,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { CreateReservationRequest, Reservation } from "@/types/api";

type LoadState = "loading" | "ready" | "error";
type Props = { isAuthenticated: boolean };

type ApiErrorPayload = {
  message?: string;
  error?: string;
};

const nowDate = new Date().toISOString().slice(0, 10);

function toLocalDateTime(date: string, time: string): string {
  const parsed = new Date(`${date}T${time}`);
  if (Number.isNaN(parsed.getTime())) {
    return `${date} ${time}`;
  }
  return parsed.toLocaleString("de-DE", { hour: "2-digit", minute: "2-digit", day: "2-digit", month: "2-digit" });
}

function statusVariant(status: Reservation["status"]): "success" | "warning" | "destructive" | "muted" {
  if (status === "CHECKED_IN") return "success";
  if (status === "PENDING" || status === "CONFIRMED") return "warning";
  if (status === "CANCELLED" || status === "NO_SHOW") return "destructive";
  return "muted";
}

function buildCalendarDays(dateFilter: string): string[] {
  const base = new Date(`${dateFilter}T12:00:00`);
  if (Number.isNaN(base.getTime())) {
    return [nowDate];
  }

  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date(base);
    date.setDate(base.getDate() - 3 + index);
    return date.toISOString().slice(0, 10);
  });
}

async function readErrorMessage(response: Response, fallback: string): Promise<string> {
  const payload = (await response.json().catch(() => ({}))) as ApiErrorPayload;
  return payload.message || payload.error || fallback;
}

export default function BookingsClient({ isAuthenticated }: Props) {
  const router = useRouter();
  const [state, setState] = useState<LoadState>(isAuthenticated ? "loading" : "ready");
  const [dateFilter, setDateFilter] = useState(nowDate);
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [success, setSuccess] = useState<string | null>(null);
  const [form, setForm] = useState<CreateReservationRequest>({
    guestName: "",
    contactEmail: "",
    contactPhone: "",
    reservationDate: nowDate,
    reservationTime: "19:00",
    guestCount: 2,
  });

  const loadReservations = useCallback(async (effectiveDate: string) => {
    if (!isAuthenticated) {
      return;
    }

    try {
      const response = await fetch(`/api/reservations?date=${encodeURIComponent(effectiveDate)}`, {
        cache: "no-store",
      });
      if (response.status === 401 || response.status === 403) {
        router.replace("/login");
        return;
      }
      if (!response.ok) {
        setError("Reservierungen konnten nicht geladen werden.");
        setState("error");
        return;
      }
      const payload = (await response.json()) as Reservation[];
      setReservations(payload);
      setState("ready");
    } catch {
      setError("Unerwarteter Fehler beim Laden.");
      setState("error");
    }
  }, [isAuthenticated, router]);

  useEffect(() => {
    if (!isAuthenticated) {
      return;
    }

    const timer = window.setTimeout(() => {
      void loadReservations(dateFilter);
    }, 0);
    return () => window.clearTimeout(timer);
  }, [dateFilter, isAuthenticated, loadReservations]);

  const pendingCount = useMemo(
    () => reservations.filter((reservation) => reservation.status === "PENDING").length,
    [reservations],
  );
  const calendarDays = useMemo(() => buildCalendarDays(dateFilter), [dateFilter]);

  async function createReservation() {
    setSaving(true);
    setError(null);
    setSuccess(null);

    const payload: CreateReservationRequest = {
      ...form,
      guestName: form.guestName.trim(),
      contactEmail: form.contactEmail?.trim() || undefined,
      contactPhone: form.contactPhone?.trim() || undefined,
      guestCount: Number(form.guestCount),
    };

    const response = await fetch("/api/reservations", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });

    if (!response.ok) {
      setError(await readErrorMessage(response, "Reservierung konnte nicht angelegt werden."));
      setSaving(false);
      return;
    }

    setForm((current) => ({ ...current, guestName: "", contactEmail: "", contactPhone: "" }));
    setSuccess("Danke! Ihre Buchungsanfrage wurde gespeichert.");

    if (isAuthenticated) {
      setDateFilter(payload.reservationDate);
      await loadReservations(payload.reservationDate);
    }

    setSaving(false);
  }

  async function runAction(id: number, action: "check-in" | "cancel") {
    const response = await fetch(`/api/reservations/${id}/${action}`, { method: "POST" });
    if (!response.ok) {
      setError(await readErrorMessage(response, `Aktion ${action} fehlgeschlagen.`));
      return;
    }

    await loadReservations(dateFilter);
  }

  if (state === "loading") {
    return <section className="p-4 md:p-6">Lade Buchungskalender...</section>;
  }

  if (state === "error") {
    return (
      <section className="mx-auto w-full max-w-3xl p-4 md:p-6">
        <Card className="border-red-200 bg-red-50">
          <CardHeader>
            <CardTitle className="text-red-700">Fehler</CardTitle>
            <CardDescription className="text-red-700">{error}</CardDescription>
          </CardHeader>
          <CardContent>
            <Button onClick={() => void loadReservations(dateFilter)} variant="outline">
              Neu laden
            </Button>
          </CardContent>
        </Card>
      </section>
    );
  }

  return (
    <section className="mx-auto w-full max-w-6xl p-3 sm:p-4 md:p-6">
      <div className="mb-4 flex flex-col gap-3 rounded-xl border border-zinc-200 bg-white p-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-zinc-900 sm:text-3xl">
            {isAuthenticated ? "Buchungskalender" : "Buchungsanfrage"}
          </h1>
          <p className="text-sm text-zinc-600">
            {isAuthenticated
              ? "Interne Kalenderansicht fuer Team, optimiert fuer iPhone 12 mini und iPad 10.5."
              : "Reservierung ohne Login anfragen. Unser Team bestaetigt im Anschluss."}
          </p>
        </div>
      </div>

      {isAuthenticated ? (
        <div className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Card>
            <CardContent className="flex items-center justify-between p-4">
              <div>
                <p className="text-xs text-zinc-500">Datum</p>
                <p className="text-sm font-semibold">{dateFilter}</p>
              </div>
              <CalendarDays className="h-5 w-5 text-zinc-500" />
            </CardContent>
          </Card>
          <Card>
            <CardContent className="flex items-center justify-between p-4">
              <div>
                <p className="text-xs text-zinc-500">Gesamt</p>
                <p className="text-sm font-semibold">{reservations.length}</p>
              </div>
              <Users className="h-5 w-5 text-zinc-500" />
            </CardContent>
          </Card>
          <Card>
            <CardContent className="flex items-center justify-between p-4">
              <div>
                <p className="text-xs text-zinc-500">Offen</p>
                <p className="text-sm font-semibold">{pendingCount}</p>
              </div>
              <TicketX className="h-5 w-5 text-amber-600" />
            </CardContent>
          </Card>
        </div>
      ) : null}

      <div className="grid gap-4 lg:grid-cols-[340px_1fr]">
        <Card>
          <CardHeader>
            <CardTitle>{isAuthenticated ? "Neue Reservierung" : "Anfrage senden"}</CardTitle>
            <CardDescription>
              {isAuthenticated ? "Schnellformular fuer Service-Team." : "Wir melden uns bei Rueckfragen per Telefon oder E-Mail."}
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="space-y-1">
              <Label htmlFor="guestName">Name</Label>
              <Input
                id="guestName"
                value={form.guestName}
                onChange={(event) => setForm((s) => ({ ...s, guestName: event.target.value }))}
                placeholder="Max Mustermann"
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="contactEmail">E-Mail (optional)</Label>
              <Input
                id="contactEmail"
                type="email"
                value={form.contactEmail}
                onChange={(event) => setForm((s) => ({ ...s, contactEmail: event.target.value }))}
                placeholder="gast@example.com"
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="contactPhone">Telefon (optional)</Label>
              <Input
                id="contactPhone"
                value={form.contactPhone}
                onChange={(event) => setForm((s) => ({ ...s, contactPhone: event.target.value }))}
                placeholder="+49 ..."
              />
            </div>
            <div className="grid grid-cols-2 gap-2">
              <div className="space-y-1">
                <Label htmlFor="reservationDate">Datum</Label>
                <Input
                  id="reservationDate"
                  type="date"
                  value={form.reservationDate}
                  onChange={(event) => setForm((s) => ({ ...s, reservationDate: event.target.value }))}
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="reservationTime">Uhrzeit</Label>
                <Input
                  id="reservationTime"
                  type="time"
                  value={form.reservationTime}
                  onChange={(event) => setForm((s) => ({ ...s, reservationTime: event.target.value }))}
                />
              </div>
            </div>
            <div className="space-y-1">
              <Label htmlFor="guestCount">Gaeste</Label>
              <Input
                id="guestCount"
                min={1}
                type="number"
                value={String(form.guestCount)}
                onChange={(event) => setForm((s) => ({ ...s, guestCount: Number(event.target.value || 1) }))}
              />
            </div>
            <Button disabled={saving || !form.guestName.trim()} onClick={() => void createReservation()} className="w-full">
              {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
              {isAuthenticated ? "Anlegen" : "Anfrage absenden"}
            </Button>
            {success ? <p className="text-xs text-emerald-700">{success}</p> : null}
            {error ? <p className="text-xs text-red-600">{error}</p> : null}
          </CardContent>
        </Card>

        {isAuthenticated ? (
          <Card>
            <CardHeader className="gap-3">
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <CardTitle>Buchungskalender</CardTitle>
                <Input
                  type="date"
                  value={dateFilter}
                  onChange={(event) => setDateFilter(event.target.value)}
                  className="w-full sm:w-[190px]"
                />
              </div>
              <CardDescription>Check-in und Storno direkt auf der Tagesansicht.</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="mb-3 grid grid-cols-3 gap-2 sm:grid-cols-7">
                {calendarDays.map((day) => {
                  const isSelected = day === dateFilter;
                  return (
                    <button
                      key={day}
                      type="button"
                      onClick={() => setDateFilter(day)}
                      className={[
                        "rounded-md border px-2 py-2 text-xs font-medium",
                        isSelected
                          ? "border-zinc-900 bg-zinc-900 text-white"
                          : "border-zinc-200 bg-zinc-50 text-zinc-700",
                      ].join(" ")}
                    >
                      {new Date(`${day}T12:00:00`).toLocaleDateString("de-DE", { weekday: "short", day: "2-digit" })}
                    </button>
                  );
                })}
              </div>

              <div className="space-y-2">
                {reservations.length === 0 ? (
                  <p className="rounded-md border border-zinc-200 p-3 text-sm text-zinc-600">
                    Keine Reservierungen fuer dieses Datum.
                  </p>
                ) : (
                  reservations.map((reservation) => (
                    <div key={reservation.id} className="rounded-lg border border-zinc-200 p-3">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div>
                          <p className="text-sm font-semibold text-zinc-900">{reservation.guestName}</p>
                          <p className="text-xs text-zinc-600">
                            {toLocalDateTime(reservation.reservationDate, reservation.reservationTime)} · {reservation.guestCount} Pers.
                          </p>
                        </div>
                        <Badge variant={statusVariant(reservation.status)}>{reservation.status}</Badge>
                      </div>
                      <div className="mt-3 flex flex-wrap items-center gap-3 text-xs text-zinc-600">
                        <span className="inline-flex items-center gap-1"><Phone className="h-3.5 w-3.5" />{reservation.contactPhone || "-"}</span>
                        <span className="inline-flex items-center gap-1"><Mail className="h-3.5 w-3.5" />{reservation.contactEmail || "-"}</span>
                      </div>
                      <div className="mt-3 flex flex-wrap gap-2">
                        <Button
                          size="sm"
                          variant="secondary"
                          disabled={reservation.status !== "PENDING" && reservation.status !== "CONFIRMED"}
                          onClick={() => void runAction(reservation.id, "check-in")}
                        >
                          <CheckCircle2 className="h-4 w-4" />Check-in
                        </Button>
                        <Button
                          size="sm"
                          variant="destructive"
                          disabled={reservation.status === "CANCELLED" || reservation.status === "NO_SHOW"}
                          onClick={() => void runAction(reservation.id, "cancel")}
                        >
                          <TicketX className="h-4 w-4" />Stornieren
                        </Button>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </CardContent>
          </Card>
        ) : (
          <Card>
            <CardHeader>
              <CardTitle>Was passiert als naechstes?</CardTitle>
              <CardDescription>Ihre Anfrage landet sofort im internen Buchungskalender.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-2 text-sm text-zinc-700">
              <p>1. Anfrage abschicken</p>
              <p>2. Team prueft Verfuegbarkeit</p>
              <p>3. Rueckmeldung per Telefon oder E-Mail</p>
              <p className="pt-2 text-xs text-zinc-500">Fuer die interne Kalenderansicht bitte einloggen.</p>
              <Button variant="outline" onClick={() => router.push("/login")}>Zum Staff-Login</Button>
            </CardContent>
          </Card>
        )}
      </div>
    </section>
  );
}

