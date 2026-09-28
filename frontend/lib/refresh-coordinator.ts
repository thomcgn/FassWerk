// Process-local coalescing: never relax backend replay detection to handle browser concurrency.
export function createRefreshCoordinator<T>(
  holdMs = 3000,
  maxEntries = 256,
  now: () => number = Date.now,
) {
  const entries = new Map<string, { promise: Promise<T>; expiresAt: number }>();
  return (key: string, action: () => Promise<T>): Promise<T> => {
    for (const [id, entry] of entries) {
      if (entry.expiresAt <= now()) entries.delete(id);
    }
    const existing = entries.get(key);
    if (existing) return existing.promise;
    if (entries.size >= maxEntries) {
      return Promise.reject(new Error("Refresh capacity exceeded"));
    }
    const entry: { promise: Promise<T>; expiresAt: number } = {
      promise: Promise.resolve().then(action), expiresAt: Infinity,
    };
    entry.promise = entry.promise.then(
      (value) => {
        entry.expiresAt = now() + holdMs;
        const timer = setTimeout(() => {
          if (entries.get(key) === entry) entries.delete(key);
        }, holdMs);
        timer.unref();
        return value;
      },
      (error: unknown) => {
        entries.delete(key);
        throw error;
      },
    );
    entries.set(key, entry);
    return entry.promise;
  };
}
