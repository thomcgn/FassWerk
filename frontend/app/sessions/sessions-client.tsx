"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import type { SessionResponse } from "@/types/api";

type State = "loading" | "ready" | "error";

function formatIso(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString("de-DE");
}

export default function SessionsClient() {
  const router = useRouter();
  const [state, setState] = useState<State>("loading");
  const [sessions, setSessions] = useState<SessionResponse[]>([]);
  const [error, setError] = useState<string | null>(null);

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
    const timer = window.setTimeout(() => {
      void loadSessions();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [loadSessions]);

  async function revokeSession(sessionId: number) {
    const response = await fetch(`/api/auth/sessions/${sessionId}`, { method: "DELETE" });
    if (response.status === 204) {
      setSessions((previous) => previous.filter((session) => session.id !== sessionId));
      return;
    }

    setError("Session konnte nicht beendet werden.");
  }

  async function logoutAll() {
    const response = await fetch("/api/auth/logout-all", { method: "POST" });
    if (response.status === 204) {
      router.replace("/login");
      router.refresh();
      return;
    }

    setError("Logout aller Sessions fehlgeschlagen.");
  }

  if (state === "loading") {
    return <main className="p-6">Lade Sessions...</main>;
  }

  if (state === "error") {
    return (
      <main className="mx-auto w-full max-w-3xl p-6">
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-red-700">
          <p className="font-medium">{error}</p>
          <button
            onClick={() => {
              setState("loading");
              setError(null);
              void loadSessions();
            }}
            className="mt-3 rounded-md border border-red-400 px-3 py-2 text-sm"
          >
            Erneut versuchen
          </button>
        </div>
      </main>
    );
  }

  return (
    <main className="mx-auto w-full max-w-5xl p-6">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-3xl font-semibold">Aktive Sessions</h1>
        <div className="flex items-center gap-2">
          <a href="/inventory" className="rounded-md border border-zinc-400 px-3 py-2">
            Zurueck zu Inventory
          </a>
          <button onClick={logoutAll} className="rounded-md bg-zinc-900 px-3 py-2 text-white">
            Logout All
          </button>
        </div>
      </div>

      {error ? (
        <p className="mb-3 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-700">
          {error}
        </p>
      ) : null}

      <div className="overflow-x-auto rounded-xl border border-zinc-300">
        <table className="min-w-full border-collapse text-left text-sm">
          <thead>
            <tr className="bg-zinc-100">
              <th className="px-3 py-2">Aktiv</th>
              <th className="px-3 py-2">Session ID</th>
              <th className="px-3 py-2">Token ID</th>
              <th className="px-3 py-2">Erstellt</th>
              <th className="px-3 py-2">Zuletzt genutzt</th>
              <th className="px-3 py-2">Client</th>
              <th className="px-3 py-2">Gueltig bis</th>
              <th className="px-3 py-2">Aktion</th>
            </tr>
          </thead>
          <tbody>
            {sessions.length === 0 ? (
              <tr>
                <td className="px-3 py-4 text-zinc-600" colSpan={8}>
                  Keine aktiven Sessions vorhanden.
                </td>
              </tr>
            ) : (
              sessions.map((session) => (
                <tr key={session.id} className="border-t border-zinc-200">
                  <td className="px-3 py-2">
                    <span
                      className={`rounded-full px-2 py-1 text-xs font-semibold ${
                        session.current
                          ? "bg-emerald-100 text-emerald-800"
                          : "bg-zinc-200 text-zinc-700"
                      }`}
                    >
                      {session.current ? "CURRENT" : "OTHER"}
                    </span>
                  </td>
                  <td className="px-3 py-2 font-medium">{session.id}</td>
                  <td className="px-3 py-2 font-mono text-xs">{session.tokenId.slice(0, 12)}...</td>
                  <td className="px-3 py-2">{formatIso(session.createdAt)}</td>
                  <td className="px-3 py-2">{session.lastUsedAt ? formatIso(session.lastUsedAt) : "-"}</td>
                  <td className="px-3 py-2">
                    <div className="max-w-xs">
                      <p className="truncate text-xs text-zinc-700">{session.userAgent ?? "unknown"}</p>
                      <p className="text-xs text-zinc-500">IP: {session.ipAddress ?? "-"}</p>
                    </div>
                  </td>
                  <td className="px-3 py-2">{formatIso(session.expiresAt)}</td>
                  <td className="px-3 py-2">
                    <button
                      disabled={session.current}
                      onClick={() => void revokeSession(session.id)}
                      className="rounded-md border border-red-400 px-2 py-1 text-xs text-red-700 disabled:opacity-40"
                    >
                      Beenden
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </main>
  );
}

