import { NextResponse } from "next/server";
import { backendFetchWithAuth } from "@/lib/server-auth";

export async function GET() {
  const response = await backendFetchWithAuth("/api/reports/revenue-overview");
  if (response.status === 401 || response.status === 403) {
    return NextResponse.json({ message: "unauthorized" }, { status: response.status });
  }

  const payload = await response.json().catch(() => ({}));
  return NextResponse.json(payload, { status: response.status });
}

