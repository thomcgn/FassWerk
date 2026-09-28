import { NextResponse } from "next/server";
import { backendFetchWithAuth } from "@/lib/server-auth";

type Params = { params: Promise<{ id: string }> };

export async function POST(request: Request, { params }: Params) {
  const { id } = await params;
  const response = await backendFetchWithAuth(`/api/table-orders/${id}/mark-unpaid`, {
    method: "POST",
    headers: { "Idempotency-Key": request.headers.get("Idempotency-Key") ?? "" },
  });

  const payload = await response.json().catch(() => ({}));
  return NextResponse.json(payload, { status: response.status });
}

