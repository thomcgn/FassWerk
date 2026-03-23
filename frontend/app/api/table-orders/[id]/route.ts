import { NextResponse } from "next/server";
import { backendFetchWithAuth } from "@/lib/server-auth";

type Params = { params: Promise<{ id: string }> };

export async function GET(_: Request, { params }: Params) {
  const { id } = await params;
  const response = await backendFetchWithAuth(`/api/table-orders/${id}`, { method: "GET" });
  const payload = await response.json().catch(() => ({}));
  return NextResponse.json(payload, { status: response.status });
}

