import { NextResponse } from "next/server";
import { backendFetchWithAuth } from "@/lib/server-auth";

type Params = { params: Promise<{ id: string; itemId: string }> };

export async function DELETE(request: Request, { params }: Params) {
  const { id, itemId } = await params;
  const response = await backendFetchWithAuth(`/api/table-orders/${id}/items/${itemId}`, {
    method: "DELETE",
    headers: { "Idempotency-Key": request.headers.get("Idempotency-Key") ?? "" },
  });

  const payload = await response.json().catch(() => ({}));
  return NextResponse.json(payload, { status: response.status });
}

