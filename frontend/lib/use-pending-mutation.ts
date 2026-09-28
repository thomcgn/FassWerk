"use client";
import { useEffect, useRef, useState } from "react";
import { PendingMutation, type MutationCommand } from "./pending-mutation";

export function usePendingMutation(storageKey: string) {
  const runner = useRef<PendingMutation | null>(null);
  const [pending, setPending] = useState<MutationCommand | null>(null);
  const [busy, setBusy] = useState(false);
  const [initializationError, setInitializationError] = useState<string | null>(null);
  useEffect(() => {
    const timer = window.setTimeout(() => {
      try {
        runner.current = new PendingMutation(window.sessionStorage, storageKey);
        setPending(runner.current.pending?.command ?? null);
      } catch (error) {
        setInitializationError(error instanceof Error ? error.message : "Offene Aktion konnte nicht geladen werden.");
      }
    }, 0);
    return () => window.clearTimeout(timer);
  }, [storageKey]);
  async function run<T>(command: MutationCommand | undefined, consume: (response: Response, command: MutationCommand) => Promise<T>) {
    const current = runner.current;
    if (!current) throw new Error(initializationError ?? "Buchungsaktionen werden noch geladen.");
    setBusy(true);
    try { return await current.run(command, consume); }
    finally { setBusy(current.busy); setPending(current.pending?.command ?? null); }
  }
  return { run, pending, busy, initializationError, blocked: busy || pending !== null || initializationError !== null };
}
