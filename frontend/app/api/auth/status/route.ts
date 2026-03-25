import { NextResponse } from "next/server";
import { backendFetchWithAuth, clearTokenCookies } from "@/lib/server-auth";

export async function GET() {
  const response = await backendFetchWithAuth("/api/auth/sessions", { method: "GET" });
  const authenticated = response.ok;

  if (!authenticated) {
    await clearTokenCookies();
  }

  return NextResponse.json({ authenticated });
}

