"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { LaptopMinimal, LogOut, ShieldCheck, Smartphone } from "lucide-react";
import toast from "react-hot-toast";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useToastFeedback } from "@/lib/use-toast-feedback";
import type { SessionResponse } from "@/types/api";

type State = "loading" | "ready" | "error";

function formatIso(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("de-DE");
}

export default function SessionsClient() {
  const router = useRouter();
  const [state, setState] = useState<State>("loading");
  const [sessions, setSessions] = useState<SessionResponse[]>([]);
  const [error, setError] = useState<string | null>(null);

  useToastFeedback(error, "error");

  const loadSessions = useCallback(async () => {
    try {
      const response = await fetch("/api/auth/sessions", { cache: "no-store" });
      if (response.status === 401 || response.status === 403) {
        router.replace("/login");
        return;
      }
      if (!response.ok) {
        setState("error");
        setError("Sessions konnten nicht geladen werden.");
        return;
      }
      const payload = (await response.json()) as SessionResponse[];
      setSessions(payload);
      setState("ready");
    } catch {
      setState("error");
      setError("Unerwarteter Fehler beim Laden der Sessions.");
    }
  }, [router]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadSessions(), 0);
    return () => window.clearTimeout(timer);
  }, [loadSessions]);

  async function revokeSession(sessionId: number) {
    const response = await fetch(`/api/auth/sessions/${sessionId}`, { method: "DELETE" });
    if (response.status === 204) {
      setSessions((previous) => previous.filter((session) => session.id !== sessionId));
      toast.success("Session wurde beendet.");
      return;
    }
    setError("Session konnte nicht beendet werden.");
  }

  async function logoutAll() {
    const response = await fetch("/api/auth/logout-all", { method: "POST" });
    if (response.status === 204) {
      toast.success("Alle Sessions wurden abgemeldet.");
      router.replace("/login");
      router.refresh();
      return;
    }
    setError("Logout aller Sessions fehlgeschlagen.");
  }

  if (state === "loading") {
    return (
      <main className="p-6 text-sm text-[color:var(--color-muted-foreground)] flex items-center gap-2">
        <div className="h-5 w-5 rounded-full border-2 border-cyan-500 border-r-transparent animate-spin"></div>
        Lade Sessions...
      </main>
    );
  }

  if (state === "error") {
    return (
      <main className="mx-auto w-full max-w-3xl p-6">
        <Card className="border-red-500/40 bg-red-500/10">
          <CardHeader>
            <CardTitle className="text-red-300">Fehler</CardTitle>
            <CardDescription className="text-red-300/70">{error}</CardDescription>
          </CardHeader>
          <CardContent>
            <Button variant="outline" onClick={() => void loadSessions()}>Erneut versuchen</Button>
          </CardContent>
        </Card>
      </main>
    );
  }

  return (
    <main className="dashboard-grid animate-in fade-in duration-500">
      <section className="grid gap-4 lg:grid-cols-[1fr_auto] lg:items-end">
        <Card className="border-cyan-500/30 bg-gradient-to-br from-cyan-500/10 to-transparent">
          <CardContent className="p-6">
            <p className="text-xs uppercase tracking-[0.2em] font-semibold text-cyan-400">Security</p>
            <h1 className="mt-3 text-3xl font-bold tracking-tight">Aktive Sessions überwachen.</h1>
            <p className="mt-3 max-w-2xl text-sm leading-6 text-[color:var(--color-muted-foreground)]">
              Kontrolliere aktive Geräte, Tokens und Sicherheitsverläufe im Überblick.
            </p>
          </CardContent>
        </Card>
        <Button onClick={logoutAll} size="lg" className="w-full lg:w-auto">
          <LogOut className="h-4 w-4" />
          Logout all
        </Button>
      </section>

      {error ? (
        <div className="rounded-lg bg-red-500/20 border border-red-500/40 px-4 py-3 text-sm text-red-300">
          {error}
        </div>
      ) : null}

      <div className="space-y-3 md:hidden">
        {sessions.map((session) => (
          <Card key={session.id} className="border-cyan-500/20 hover:border-cyan-500/40 transition-all duration-300">
            <CardContent className="p-5">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-base font-semibold">Session #{session.id}</p>
                  <p className="text-sm text-[color:var(--color-muted-foreground)]">{session.current ? "Aktuelles Gerät" : "Weiteres Gerät"}</p>
                </div>
                <Badge variant={session.current ? "success" : "muted"}>{session.current ? "Current" : "Other"}</Badge>
              </div>
              <div className="mt-4 space-y-2 text-sm text-[color:var(--color-muted-foreground)]">
                <p className="inline-flex items-center gap-2"><ShieldCheck className="h-4 w-4" />Token {session.tokenId.slice(0, 12)}...</p>
                <p className="inline-flex items-center gap-2"><LaptopMinimal className="h-4 w-4" />{session.userAgent ?? "unknown"}</p>
                <p className="inline-flex items-center gap-2"><Smartphone className="h-4 w-4" />IP {session.ipAddress ?? "-"}</p>
                <p>Erstellt: {formatIso(session.createdAt)}</p>
                <p>Gültig bis: {formatIso(session.expiresAt)}</p>
              </div>
              <Button className="mt-4 w-full" variant="outline" disabled={session.current} onClick={() => void revokeSession(session.id)}>
                Session beenden
              </Button>
            </CardContent>
          </Card>
        ))}
      </div>

      <Card className="hidden md:block border-cyan-500/20">
        <CardHeader>
          <CardTitle>Session Übersicht</CardTitle>
          <CardDescription>Alle aktiven Geräte und Zugriffe auf einem Blick.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="overflow-hidden rounded-lg border border-cyan-500/20">
            <table className="min-w-full border-collapse text-left text-sm">
              <thead className="bg-cyan-500/10 text-cyan-300 border-b border-cyan-500/20">
                <tr>
                  <th className="px-4 py-3 font-semibold">Status</th>
                  <th className="px-4 py-3 font-semibold">Session</th>
                  <th className="px-4 py-3 font-semibold">Erstellt</th>
                  <th className="px-4 py-3 font-semibold">Zuletzt genutzt</th>
                  <th className="px-4 py-3 font-semibold">Client</th>
                  <th className="px-4 py-3 font-semibold">Ablauf</th>
                  <th className="px-4 py-3 font-semibold">Aktion</th>
                </tr>
              </thead>
              <tbody>
                {sessions.map((session) => (
                  <tr key={session.id} className="border-t border-cyan-500/10 hover:bg-cyan-500/5 transition-colors duration-200">
                    <td className="px-4 py-4"><Badge variant={session.current ? "success" : "muted"}>{session.current ? "Current" : "Other"}</Badge></td>
                    <td className="px-4 py-4 font-semibold">#{session.id}</td>
                    <td className="px-4 py-4 text-[color:var(--color-muted-foreground)]">{formatIso(session.createdAt)}</td>
                    <td className="px-4 py-4 text-[color:var(--color-muted-foreground)]">{session.lastUsedAt ? formatIso(session.lastUsedAt) : "-"}</td>
                    <td className="px-4 py-4">
                      <div className="max-w-xs">
                        <p className="truncate text-[color:var(--color-foreground)]">{session.userAgent ?? "unknown"}</p>
                        <p className="text-xs text-[color:var(--color-muted-foreground)]">IP: {session.ipAddress ?? "-"}</p>
                      </div>
                    </td>
                    <td className="px-4 py-4 text-[color:var(--color-muted-foreground)]">{formatIso(session.expiresAt)}</td>
                    <td className="px-4 py-4">
                      <Button variant="outline" size="sm" disabled={session.current} onClick={() => void revokeSession(session.id)}>
                        Beenden
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </main>
  );
}
