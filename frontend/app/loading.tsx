export default function Loading() {
  return (
    <main className="mx-auto w-full max-w-6xl p-4 sm:p-6" aria-busy="true" aria-live="polite">
      <div className="rounded-2xl border border-cyan-500/20 bg-gradient-to-br from-cyan-500/10 to-transparent p-6">
        <div className="flex items-center gap-3 text-sm text-[color:var(--color-muted-foreground)]">
          <span className="h-5 w-5 animate-spin rounded-full border-2 border-cyan-400 border-r-transparent" aria-hidden="true" />
          Ansicht wird geladen...
        </div>
      </div>
    </main>
  );
}
