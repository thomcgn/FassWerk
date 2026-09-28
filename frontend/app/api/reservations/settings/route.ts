import { NextResponse } from "next/server";
import { BACKEND_BASE_URL } from "@/lib/config";
export async function GET() {
  const response = await fetch(`${BACKEND_BASE_URL}/api/reservations/settings`, { cache: "no-store" });
  return NextResponse.json(await response.json().catch(() => ({})), { status: response.status });
}
