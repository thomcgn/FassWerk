import { NextResponse } from "next/server";
import { backendFetchWithAuth } from "@/lib/server-auth";

export async function GET() {
  const response = await backendFetchWithAuth("/api/reorder/suppliers", { method: "GET" });
  const payload = await response.json().catch(() => []);
  return NextResponse.json(payload, { status: response.status });
}

export async function POST(request: Request) {
  const body = await request.json().catch(() => ({}));
  const response = await backendFetchWithAuth("/api/reorder/suppliers", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const payload = await response.json().catch(() => ({}));
  return NextResponse.json(payload, { status: response.status });
}


