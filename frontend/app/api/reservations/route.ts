import { NextResponse } from "next/server";
import { BACKEND_BASE_URL } from "@/lib/config";
import { backendFetchWithAuth } from "@/lib/server-auth";

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const date = searchParams.get("date");
  const path = date ? `/api/reservations?date=${encodeURIComponent(date)}` : "/api/reservations";

  const response = await backendFetchWithAuth(path, { method: "GET" });
  if (response.status === 401 || response.status === 403) {
    return NextResponse.json({ message: "unauthorized" }, { status: response.status });
  }

  const payload = await response.json().catch(() => ({}));
  return NextResponse.json(payload, { status: response.status });
}

export async function POST(request: Request) {
  const body = await request.json();
  const response = await fetch(`${BACKEND_BASE_URL}/api/reservations`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
    cache: "no-store",
  });

  const payload = await response.json().catch(() => ({}));
  return NextResponse.json(payload, { status: response.status });
}

