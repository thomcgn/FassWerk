import { NextResponse } from "next/server";
import { backendFetchWithAuth } from "@/lib/server-auth";

export async function POST(request: Request) {
  const response = await backendFetchWithAuth("/api/inventory/configuration/manual-day-close", {
    method: "POST",
    headers: { "Content-Type": "application/json", "Idempotency-Key": request.headers.get("Idempotency-Key") ?? "" },
    body: await request.text(),
  });

  if (response.status === 401) {
    return NextResponse.json({ message: "unauthorized" }, { status: 401 });
  }

  const payload = await response.json().catch(() => ({}));
  return NextResponse.json(payload, { status: response.status });
}

