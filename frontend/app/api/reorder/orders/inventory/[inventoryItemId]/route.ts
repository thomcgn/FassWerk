import { NextResponse } from "next/server";
import { backendFetchWithAuth } from "@/lib/server-auth";

type Params = { params: Promise<{ inventoryItemId: string }> };

export async function GET(_: Request, { params }: Params) {
  const { inventoryItemId } = await params;
  const response = await backendFetchWithAuth(`/api/reorder/orders/inventory/${inventoryItemId}`, {
    method: "GET",
  });
  const payload = await response.json().catch(() => []);
  return NextResponse.json(payload, { status: response.status });
}


