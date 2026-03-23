import { NextResponse } from "next/server";
import { BACKEND_BASE_URL } from "@/lib/config";

export async function GET() {
  const response = await fetch(`${BACKEND_BASE_URL}/api/drink-variants`, {
    method: "GET",
    cache: "no-store",
  });
  const payload = await response.json().catch(() => ({}));
  return NextResponse.json(payload, { status: response.status });
}

