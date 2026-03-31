import { NextResponse } from "next/server";
import { backendFetchWithAuth } from "@/lib/server-auth";

type Params = { params: Promise<{ id: string }> };

export async function PUT(_: Request, { params }: Params) {
  const { id } = await params;
  const body = await _.json().catch(() => ({}));
  const response = await backendFetchWithAuth(`/api/reorder/suppliers/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const payload = await response.json().catch(() => ({}));
  return NextResponse.json(payload, { status: response.status });
}


