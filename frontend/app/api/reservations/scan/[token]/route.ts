import { NextResponse } from "next/server";
import { backendFetchWithAuth } from "@/lib/server-auth";
export async function POST(_: Request, { params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  if (!/^[A-Za-z0-9_-]{1,128}$/.test(token)) return NextResponse.json({ message: "Invalid QR token" }, { status: 400 });
  const response = await backendFetchWithAuth(`/api/reservations/scan/${encodeURIComponent(token)}`, { method: "POST" });
  return NextResponse.json(await response.json().catch(() => ({})), { status: response.status });
}

// Previously printed QR links are navigation only; scanning still requires staff authentication.
export async function GET(request: Request, { params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  if (!/^[A-Za-z0-9_-]{1,128}$/.test(token)) return NextResponse.json({ message: "Invalid QR token" }, { status: 400 });
  return NextResponse.redirect(new URL(`/bookings/scan/${encodeURIComponent(token)}`, request.url));
}
