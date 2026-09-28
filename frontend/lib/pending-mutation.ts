export type MutationCommand = { url: string; method: "POST" | "DELETE"; body?: string; label: string };
type Pending = { command: MutationCommand; key: string };
type Storage = Pick<globalThis.Storage, "getItem" | "setItem" | "removeItem">;

// One unresolved command per workflow. Persist before sending, including its exact payload.
export class PendingMutation {
  pending: Pending | null = null;
  busy = false;
  private uncertain = false;
  constructor(privateStorage: Storage, storageKey: string) {
    this.storage = privateStorage;
    this.storageKey = storageKey;
    const raw = privateStorage.getItem(storageKey);
    if (raw) {
      const saved: unknown = JSON.parse(raw);
      if (!isPending(saved)) throw new Error("Offene Aktion konnte nicht gelesen werden. Bitte den gespeicherten Auftrag prüfen lassen.");
      this.pending = saved;
      this.uncertain = true;
    }
  }
  private storage: Storage;
  private storageKey: string;

  async run<T>(command: MutationCommand | undefined, consume: (response: Response, command: MutationCommand) => Promise<T>,
    send: typeof fetch = fetch): Promise<{ value: T; command: MutationCommand } | null> {
    if (this.busy) return null;
    if (this.pending && command && JSON.stringify(command) !== JSON.stringify(this.pending.command)) {
      throw new Error("Bitte zuerst die offene Aktion wiederholen, bevor du eine weitere Buchung startest.");
    }
    if (!this.pending) {
      if (!command) return null;
      this.pending = { command, key: crypto.randomUUID() };
      this.uncertain = false;
    }
    const pending = this.pending;
    this.busy = true;
    try {
      // If storage is unavailable, no request is sent.
      this.storage.setItem(this.storageKey, JSON.stringify(pending));
      const response = await send(pending.command.url, {
        method: pending.command.method,
        headers: { "Content-Type": "application/json", "Idempotency-Key": pending.key },
        body: pending.command.body,
        signal: AbortSignal.timeout(20_000),
      });
      if (!response.ok) {
        const error = await response.json().catch(() => null) as { message?: string } | null;
        // Only a definite rejection of the FIRST attempt allows a new command.
        // A later 401/409 may follow an earlier commit whose response was lost.
        if (!this.uncertain && [400, 401, 403, 404, 409, 422].includes(response.status)) this.clear();
        throw new Error(error?.message ?? `Aktion fehlgeschlagen (HTTP ${response.status}).`);
      }
      const value = await consume(response, pending.command);
      this.clear();
      return { value, command: pending.command };
    } catch (error) {
      if (this.pending) {
        this.uncertain = true;
        throw new Error("Ausgang der Aktion unklar. Bitte die offene Aktion wiederholen; sie wird nicht doppelt gebucht.", { cause: error });
      }
      throw error;
    } finally { this.busy = false; }
  }
  private clear() {
    // Keep the command in memory if durable removal fails.
    const stored = this.storage.getItem(this.storageKey);
    // A late response from an unmounted page must not erase a newer command.
    if (stored && (JSON.parse(stored) as Pending).key === this.pending?.key) {
      this.storage.removeItem(this.storageKey);
    }
    this.pending = null;
    this.uncertain = false;
  }
}
function isPending(value: unknown): value is Pending {
  if (!value || typeof value !== "object") return false;
  const p = value as Partial<Pending>;
  const c = p.command;
  return typeof p.key === "string" && p.key.length > 0 && p.key.length <= 80 && !!c
    && typeof c.url === "string" && c.url.startsWith("/api/") && !c.url.includes("\\")
    && ["POST", "DELETE"].includes(c.method) && typeof c.label === "string"
    && (c.body === undefined || typeof c.body === "string");
}
