"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { LockKeyhole, QrCode, ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError(null);

    try {
      const response = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      });

      if (!response.ok) {
        const body = (await response.json().catch(() => ({}))) as { message?: string };
        setError(body.message ?? "Login fehlgeschlagen");
        return;
      }

      router.replace("/inventory");
      router.refresh();
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="mx-auto grid min-h-[calc(100vh-10rem)] w-full max-w-6xl items-center gap-8 px-4 py-8 lg:grid-cols-[1fr_1fr]">
      <div className="hidden lg:block space-y-8">
        <div>
          <div className="inline-flex h-14 w-14 items-center justify-center rounded-2xl bg-linear-to-br from-cyan-500 to-blue-600 text-white shadow-lg shadow-cyan-500/30 mb-6">
            <ShieldCheck className="h-7 w-7" />
          </div>
          <h1 className="mt-4 text-4xl sm:text-5xl font-bold tracking-tight bg-gradient-to-r from-white via-cyan-100 to-blue-200 bg-clip-text text-transparent">
            Professional ops dashboard for your team.
          </h1>
          <p className="mt-4 max-w-xl text-base leading-7 text-[color:var(--color-muted-foreground)]">
            Modernes Interface mit klaren Informationshierarchien, Touch-optimierten Komponenten und sicheren Workflows.
            Alles auf eine ruhige, fokussierte Art.
          </p>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          {[
            { icon: QrCode, title: "Check-in", text: "QR-Scans, Reservierungen und Statuswechsel in Sekunden." },
            { icon: LockKeyhole, title: "Secure Access", text: "Login, Sessions und operative Metriken in einem sicheren Admin-Layout." },
          ].map((item) => {
            const Icon = item.icon;
            return (
              <div key={item.title} className="rounded-2xl border border-cyan-500/20 bg-cyan-500/5 backdrop-blur-sm p-5 hover:border-cyan-500/40 hover:bg-cyan-500/10 transition-all duration-300">
                <div className="inline-flex h-12 w-12 items-center justify-center rounded-xl bg-cyan-500/20 text-cyan-400">
                  <Icon className="h-6 w-6" />
                </div>
                <h2 className="mt-4 text-lg font-bold text-white">{item.title}</h2>
                <p className="mt-2 text-sm leading-6 text-[color:var(--color-muted-foreground)]">{item.text}</p>
              </div>
            );
          })}
        </div>
      </div>

      <Card className="mx-auto w-full max-w-md border-cyan-500/30 bg-gradient-to-br from-cyan-500/5 via-transparent to-blue-600/5">
        <CardHeader className="space-y-3">
          <CardTitle className="text-2xl font-bold">Staff Login</CardTitle>
          <CardDescription className="text-[color:var(--color-muted-foreground)]">Zugriff auf Dashboard, Service und Auswertungen</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-5">
            <div className="space-y-2.5">
              <Label htmlFor="email" className="text-sm font-semibold">E-Mail</Label>
              <Input
                id="email"
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                required
                className="bg-white/5 border-cyan-500/20 focus:border-cyan-500/50 rounded-lg"
                placeholder="admin@fasswerk.local"
              />
            </div>
            <div className="space-y-2.5">
              <Label htmlFor="password" className="text-sm font-semibold">Passwort</Label>
              <Input
                id="password"
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                required
                className="bg-white/5 border-cyan-500/20 focus:border-cyan-500/50 rounded-lg"
              />
            </div>
            {error ? (
              <div className="rounded-lg bg-red-500/15 border border-red-500/40 px-4 py-3 text-sm text-red-300">
                {error}
              </div>
            ) : null}
            <Button type="submit" className="w-full" size="lg" disabled={loading}>
              {loading ? "Anmeldung läuft..." : "Zum Dashboard"}
            </Button>
          </form>
        </CardContent>
      </Card>
    </main>
  );
}
