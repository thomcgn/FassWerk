import { NextResponse } from "next/server";
import { backendFetchWithAuth } from "@/lib/server-auth";

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const query = new URLSearchParams();
  if (searchParams.has("onlyActive")) {
    query.set("onlyActive", searchParams.get("onlyActive") ?? "true");
  }

  const response = await backendFetchWithAuth(`/api/reorder/suppliers?${query.toString()}`, {
    method: "GET",
  });
  const payload = await response.json().catch(() => []);
  return NextResponse.json(payload, { status: response.status });
}


