import { NextResponse } from "next/server";
import { backendFetchWithAuth } from "@/lib/server-auth";

export async function GET() {
  const response = await backendFetchWithAuth("/api/auth/sessions", { method: "GET" });
  const authenticated = response.ok;

  // A delayed status response must not clear cookies from a newer login.

  return NextResponse.json({ authenticated });
}

