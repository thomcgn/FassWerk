import { NextResponse } from "next/server";
import { refreshSession } from "@/lib/server-auth";

export async function POST() {
  const refreshed = await refreshSession();
  if (!refreshed) {
    return NextResponse.json({ message: "refresh failed" }, { status: 401 });
  }
  return NextResponse.json({ ok: true });
}

