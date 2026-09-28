import { NextResponse } from "next/server";
import { backendFetchWithAuth } from "@/lib/server-auth";

export async function PUT(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  if (!/^\d+$/.test(id)) return NextResponse.json({ message: "Invalid id" }, { status: 400 });
  const response = await backendFetchWithAuth(`/api/reservations/${id}`, { method: "PUT", body: JSON.stringify(await request.json()), headers: { "Content-Type": "application/json" }, });
  return NextResponse.json(await response.json().catch(() => ({})), { status: response.status });
}
