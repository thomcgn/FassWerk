import { NextResponse } from "next/server";
import { backendFetchWithAuth } from "@/lib/server-auth";

export async function POST(request: Request) {
  const body = await request.json();
  const response = await backendFetchWithAuth("/api/table-orders/direct", {
    method: "POST",
    headers: { "Content-Type": "application/json", "Idempotency-Key": request.headers.get("Idempotency-Key") ?? "" },
    body: JSON.stringify(body),
  });

  const payload = await response.json().catch(() => ({}));
  return NextResponse.json(payload, { status: response.status });
}

