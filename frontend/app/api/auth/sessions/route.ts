import { NextResponse } from "next/server";
import { listSessionsAgainstBackend } from "@/lib/server-auth";

export async function GET() {
  const result = await listSessionsAgainstBackend();
  return NextResponse.json(result.sessions, { status: result.status });
}

