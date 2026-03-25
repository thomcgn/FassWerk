import { NextResponse } from "next/server";
import { BACKEND_BASE_URL } from "@/lib/config";
import { backendFetchWithAuth } from "@/lib/server-auth";

export async function GET() {
  const response = await fetch(`${BACKEND_BASE_URL}/api/drink-categories`, {
    method: "GET",
    cache: "no-store",
  });

  const payload = await response.json().catch(() => ({}));
  return NextResponse.json(payload, { status: response.status });
}

export async function POST(request: Request) {
  const body = await request.json().catch(() => ({}));
  const response = await backendFetchWithAuth("/api/drink-categories", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

  const payload = await response.json().catch(() => ({}));
  return NextResponse.json(payload, { status: response.status });
}

