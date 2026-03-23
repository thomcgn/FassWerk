import { NextResponse } from "next/server";
import { logoutAgainstBackend } from "@/lib/server-auth";

export async function POST() {
  await logoutAgainstBackend();
  return new NextResponse(null, { status: 204 });
}

