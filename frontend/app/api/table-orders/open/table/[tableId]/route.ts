import { NextResponse } from "next/server";
import { backendFetchWithAuth } from "@/lib/server-auth";

type Params = { params: Promise<{ tableId: string }> };

export async function GET(_: Request, { params }: Params) {
  const { tableId } = await params;
  const response = await backendFetchWithAuth(`/api/table-orders/open/table/${tableId}`, {
    method: "GET",
  });
  const payload = await response.json().catch(() => ({}));
  return NextResponse.json(payload, { status: response.status });
}

