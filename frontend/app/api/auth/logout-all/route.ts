import { NextResponse } from "next/server";
import { logoutAllSessionsAgainstBackend } from "@/lib/server-auth";

export async function POST() {
  const success = await logoutAllSessionsAgainstBackend();
  return new NextResponse(null, { status: success ? 204 : 401 });
}

