"use client";

import { useCallback, useEffect, useState } from "react";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { Mail, Phone } from "lucide-react";
import QRCode from "qrcode";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FullCalendarBookings } from "@/components/full-calendar-bookings";
import { useToastFeedback } from "@/lib/use-toast-feedback";
import type { CreateReservationRequest, Reservation } from "@/types/api";

type LoadState = "loading" | "ready" | "error";
type Props = { isAuthenticated: boolean };

type ApiErrorPayload = { message?: string; error?: string };
const nowDate = new Date().toISOString().slice(0, 10);

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
  const [createdReservation, setCreatedReservation] = useState<Reservation | null>(null);
  const [qrCodeDataUrl, setQrCodeDataUrl] = useState<string | null>(null);
  const [cancellationModal, setCancellationModal] = useState<{ id: number; reason: string } | null>(null);
  const [form, setForm] = useState<CreateReservationRequest>({
    guestName: "",
    contactEmail: "",
    contactPhone: "",
    reservationDate: nowDate,
    reservationTime: "18:00",
    guestCount: 20,
  });

  useToastFeedback(error, "error");
  useToastFeedback(success, "success");

  const loadReservations = useCallback(
    async (effectiveDate: string) => {
      if (!isAuthenticated) return;
      try {
        const response = await fetch(`/api/reservations?date=${encodeURIComponent(effectiveDate)}`, { cache: "no-store" });
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
        setReservations(payload.sort((a, b) => `${a.reservationDate}${a.reservationTime}`.localeCompare(`${b.reservationDate}${b.reservationTime}`)));
        setState("ready");
      } catch {
        setError("Unerwarteter Fehler beim Laden.");
        setState("error");
      }
    },
    [isAuthenticated, router],
  );

  useEffect(() => {
    if (!isAuthenticated) return;
    const timer = window.setTimeout(() => void loadReservations(dateFilter), 0);
    return () => window.clearTimeout(timer);
  }, [dateFilter, isAuthenticated, loadReservations]);

  useEffect(() => {
    let active = true;
    async function renderQr() {
      if (!createdReservation?.qrScanUrl) {
        setQrCodeDataUrl(null);
        return;
      }
      try {
        const dataUrl = await QRCode.toDataURL(createdReservation.qrScanUrl, {
          margin: 1,
          width: 220,
          color: { dark: "#f5ecd7", light: "#12271f" },
        });
        if (active) setQrCodeDataUrl(dataUrl);
      } catch {
        if (active) setQrCodeDataUrl(null);
      }
    }
    void renderQr();
    return () => {
      active = false;
    };
  }, [createdReservation]);

  async function createReservation() {
    setSaving(true);
    setError(null);
    setSuccess(null);
    setCreatedReservation(null);
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
    const created = (await response.json()) as Reservation;
    setForm((current) => ({ ...current, guestName: "", contactEmail: "", contactPhone: "" }));
    setCreatedReservation(created);
    setSuccess("Reservierung gespeichert. Dein QR-Code ist sofort verfügbar.");
    if (isAuthenticated) {
      setDateFilter(created.reservationDate);
      await loadReservations(created.reservationDate);
    }
    setSaving(false);
  }

  async function runAction(id: number, action: "confirm" | "check-in" | "cancel") {
    if (action === "cancel") {
      setCancellationModal({ id, reason: "" });
      return;
    }
    const response = await fetch(`/api/reservations/${id}/${action}`, { method: "POST" });
    if (!response.ok) {
      setError(await readErrorMessage(response, `Aktion ${action} fehlgeschlagen.`));
      return;
    }
    setSuccess(action === "confirm" ? "Reservierung bestätigt." : "Reservierung eingecheckt.");
    await loadReservations(dateFilter);
  }

  async function confirmCancellation() {
    if (!cancellationModal) return;
    const response = await fetch(`/api/reservations/${cancellationModal.id}/cancel`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ reason: cancellationModal.reason.trim() }),
    });
    if (!response.ok) {
      setError(await readErrorMessage(response, "Stornierung fehlgeschlagen."));
      return;
    }
    setSuccess("Reservierung abgelehnt.");
    setCancellationModal(null);
    await loadReservations(dateFilter);
  }

  if (state === "loading") {
    return (
      <section className="p-4 text-sm text-[color:var(--color-muted-foreground)] flex items-center gap-2">
        <div className="h-5 w-5 rounded-full border-2 border-cyan-500 border-r-transparent animate-spin"></div>
        Lade Buchungskalender...
      </section>
    );
  }

  if (state === "error") {
    return (
      <section className="mx-auto w-full max-w-3xl p-4 md:p-6">
        <Card className="border-red-500/40 bg-red-500/10">
          <CardHeader>
            <CardTitle className="text-red-300">Fehler</CardTitle>
            <CardDescription className="text-red-300/70">{error}</CardDescription>
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
    <section className="dashboard-grid animate-in fade-in duration-500 space-y-6">
      {/* Header & Form */}
      <div className={isAuthenticated ? "grid gap-5 lg:grid-cols-[0.92fr_1.08fr]" : "grid gap-5 lg:grid-cols-[1.05fr_0.95fr]"}>
        <Card className={isAuthenticated ? "border-cyan-500/30 bg-gradient-to-br from-cyan-500/10 to-transparent" : "overflow-hidden border-cyan-500/30 bg-gradient-to-br from-cyan-500/10 to-transparent"}>
          <CardContent className="p-6">
            <h1 className="text-3xl font-bold tracking-tight">
              {isAuthenticated ? "Reservierungen Dashboard" : "Reserve your table."}
            </h1>
            <p className="mt-2 text-sm leading-6 text-[color:var(--color-muted-foreground)]">
              {isAuthenticated
                ? "FullCalendar-Übersicht mit nächsten Reservierungen und schnellen Aktionen."
                : (
                  <>
                    <span className="block font-semibold text-[color:var(--color-foreground)]">Öffnungszeiten</span>
                    <span className="block">Mo-Mi: 18:00 - 01:00 Uhr</span>
                    <span className="block">Do: 18:00 - 02:00 Uhr</span>
                    <span className="block">Fr-Sa: 18:00 - 03:00 Uhr</span>
                    <span className="block">So: geschlossen</span>
                    <span className="mt-2 block">Hinweis: Bestätigte Reservierungen sind nur bis Buchungszeit +15 Minuten gültig, danach verfällt der Slot.</span>
                  </>
                )}
            </p>
          </CardContent>
        </Card>

        <Card className="border-cyan-500/30 bg-gradient-to-br from-cyan-500/5 to-transparent">
          <CardHeader>
            <CardTitle>{isAuthenticated ? "Neue Reservierung" : "Buchungsanfrage"}</CardTitle>
            <CardDescription>{isAuthenticated ? "Schnelleingabe fürs Team." : "Große Felder, klare Reihenfolge."}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="guestName">Name</Label>
              <Input
                id="guestName"
                value={form.guestName}
                onChange={(event) => setForm((s) => ({ ...s, guestName: event.target.value }))}
                placeholder="z. B. Lara Weber"
              />
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="contactEmail">E-Mail</Label>
                <div className="relative">
                  <Mail className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-[color:var(--color-muted-foreground)]" />
                  <Input
                    id="contactEmail"
                    type="email"
                    value={form.contactEmail}
                    onChange={(event) => setForm((s) => ({ ...s, contactEmail: event.target.value }))}
                    placeholder="gast@example.com"
                    className="pl-10"
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label htmlFor="contactPhone">Telefon</Label>
                <div className="relative">
                  <Phone className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-[color:var(--color-muted-foreground)]" />
                  <Input
                    id="contactPhone"
                    value={form.contactPhone}
                    onChange={(event) => setForm((s) => ({ ...s, contactPhone: event.target.value }))}
                    placeholder="+49 ..."
                    className="pl-10"
                  />
                </div>
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label htmlFor="reservationDate">Datum</Label>
                <Input
                  id="reservationDate"
                  type="date"
                  value={form.reservationDate}
                  onChange={(event) => setForm((s) => ({ ...s, reservationDate: event.target.value }))}
                  min={nowDate}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="reservationTime">Uhrzeit</Label>
                <Input
                  id="reservationTime"
                  type="time"
                  value={form.reservationTime}
                  onChange={(event) => setForm((s) => ({ ...s, reservationTime: event.target.value }))}
                />
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="guestCount">Personen</Label>
              <Input
                id="guestCount"
                type="number"
                min={1}
                max={20}
                value={String(form.guestCount)}
                onChange={(event) => setForm((s) => ({ ...s, guestCount: Number(event.target.value) }))}
              />
            </div>
            {success ? <p className="rounded-2xl bg-emerald-50 px-4 py-3 text-sm text-emerald-700">{success}</p> : null}
            {error ? <p className="rounded-2xl bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</p> : null}
            <Button onClick={() => void createReservation()} className="w-full" size="lg" disabled={saving || !form.guestName.trim()}>
              {saving ? "Sende Anfrage..." : isAuthenticated ? "Reservierung anlegen" : "Tisch anfragen"}
            </Button>

            {!isAuthenticated && createdReservation ? (
              <div className="rounded-2xl border border-[color:var(--color-border-strong)] bg-[color:var(--color-surface-muted)] p-4">
                <p className="text-sm font-semibold text-[color:var(--color-foreground)]">Dein QR-Code</p>
                <p className="mt-1 text-xs text-[color:var(--color-muted-foreground)]">Reservierung #{createdReservation.id} bitte am Eingang vorzeigen.</p>
                {qrCodeDataUrl ? (
                  <Image
                    src={qrCodeDataUrl}
                    alt="Reservierungs-QR-Code"
                    width={180}
                    height={180}
                    unoptimized
                    className="mt-3 rounded-xl border border-[color:var(--color-border-soft)]"
                  />
                ) : null}
                <a
                  href={createdReservation.qrScanUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="mt-3 inline-block text-xs text-amber-100 underline underline-offset-4"
                >
                  QR-Link öffnen
                </a>
              </div>
            ) : null}
          </CardContent>
        </Card>
      </div>

      {/* FullCalendar für authentifizierte User */}
      {isAuthenticated ? (
        <FullCalendarBookings
          reservations={reservations}
          setDateFilter={setDateFilter}
          onAction={runAction}
          cancellationModal={cancellationModal}
          setCancellationModal={setCancellationModal}
          onConfirmCancellation={confirmCancellation}
          loading={false}
        />
      ) : null}
    </section>
  );
}

