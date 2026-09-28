"use client";
import { Button } from "@/components/ui/button";
export function PendingMutationNotice({ label, busy, error, onRetry }: {
  label?: string; busy: boolean; error?: string | null; onRetry: () => void;
}) {
  if (error) return <p role="alert">{error}</p>;
  if (busy) return <p role="status">Aktion wird geprüft …</p>;
  if (!label) return null;
  return <div role="alert" className="rounded-lg border border-amber-500/50 bg-amber-500/10 p-4 space-y-2">
    <p>Offene Aktion: {label}. Die Antwort fehlt. Bitte wiederholen, bevor du weiterbuchst.</p>
    <Button onClick={onRetry}>Offene Aktion wiederholen</Button>
  </div>;
}
