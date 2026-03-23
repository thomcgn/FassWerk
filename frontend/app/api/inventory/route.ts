import { NextResponse } from "next/server";
import { backendFetchWithAuth } from "@/lib/server-auth";

export async function GET() {
  const response = await backendFetchWithAuth("/api/inventory");

  if (response.status === 401) {
    return NextResponse.json({ message: "unauthorized" }, { status: 401 });
  }

  const payload = await response.json().catch(() => ({}));
  return NextResponse.json(payload, { status: response.status });
}

