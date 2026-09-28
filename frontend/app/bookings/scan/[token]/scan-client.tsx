"use client";

import { useState } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

type Scan = { reservationId: number; guestName: string; reservationDate: string; reservationTime: string; status: string; checkInAllowed: boolean };

export default function ScanClient({ token }: { token: string }) {
  const [scan, setScan] = useState<Scan | null>(null);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  async function run(checkIn: boolean) {
    setBusy(true);
    setMessage("");
    try {
      const url = checkIn && scan
        ? `/api/reservations/${scan.reservationId}/check-in`
        : `/api/reservations/scan/${encodeURIComponent(token)}`;
      const response = await fetch(url, { method: "POST" });
      if (response.status === 401 || response.status === 403) {
        setScan(null);
        setMessage("Bitte als Teammitglied anmelden und diesen QR-Link erneut oeffnen.");
        return;
      }
      const body = await response.json();
      if (!response.ok) {
        setScan(null);
        setMessage(body.message || "Reservierung konnte nicht geladen werden.");
        return;
      }
      if (checkIn) {
        setScan(null);
        setMessage("Check-in erfolgreich.");
      } else {
        setScan(body as Scan);
      }
    } catch {
      setMessage("Verbindung fehlgeschlagen. Bitte erneut versuchen.");
    } finally { setBusy(false); }
  }
  return <section className="mx-auto max-w-xl p-6">
    <Card><CardHeader><CardTitle>Reservierung scannen</CardTitle></CardHeader><CardContent className="space-y-4">
      <p>Der QR-Code allein berechtigt nicht zum Check-in. Die Pruefung erfolgt durch ein angemeldetes Teammitglied.</p>
      <Button disabled={busy} onClick={() => void run(false)}>Reservierung pruefen</Button>
      {scan && <div>
        <p>{scan.guestName} · {scan.reservationDate} {scan.reservationTime} · {scan.status}</p>
        {scan.checkInAllowed ? <Button disabled={busy} onClick={() => void run(true)}>Check-in bestaetigen</Button>
          : <p>Ein Check-in ist derzeit nicht erlaubt.</p>}
      </div>}
      {message && <p role="status">{message}</p>}
      <Link href="/login">Zur Anmeldung</Link>
    </CardContent></Card>
  </section>;
}
