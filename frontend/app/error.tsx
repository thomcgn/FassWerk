"use client";

import { useEffect } from "react";
import { AlertTriangle, RotateCcw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error("Unhandled route error", error);
  }, [error]);

  return (
    <main className="mx-auto flex min-h-[55vh] w-full max-w-2xl items-center p-4 sm:p-6">
      <Card className="w-full border-red-500/35 bg-gradient-to-br from-red-500/10 to-transparent">
        <CardHeader>
          <div className="mb-2 flex h-11 w-11 items-center justify-center rounded-xl border border-red-500/30 bg-red-500/15 text-red-300">
            <AlertTriangle className="h-5 w-5" />
          </div>
          <CardTitle>Diese Ansicht konnte nicht geladen werden</CardTitle>
          <CardDescription>Deine Eingaben wurden nicht absichtlich verworfen. Versuche die Ansicht erneut zu laden.</CardDescription>
        </CardHeader>
        <CardContent>
          <Button onClick={reset} className="gap-2"><RotateCcw className="h-4 w-4" />Erneut versuchen</Button>
        </CardContent>
      </Card>
    </main>
  );
}
