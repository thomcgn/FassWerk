import { NextResponse } from "next/server";
import { backendFetchWithAuth } from "@/lib/server-auth";

export async function POST(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  if (!/^\d+$/.test(id)) return NextResponse.json({ message: "Invalid id" }, { status: 400 });
  const response = await backendFetchWithAuth(`/api/reservations/${id}/complete`, { method: "POST",  });
  return NextResponse.json(await response.json().catch(() => ({})), { status: response.status });
}
