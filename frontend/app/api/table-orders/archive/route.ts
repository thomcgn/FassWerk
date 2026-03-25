import { NextResponse } from "next/server";
import { backendFetchWithAuth } from "@/lib/server-auth";

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const date = searchParams.get("date");
  const query = searchParams.get("query");
  const payment = searchParams.get("payment");

  const backendQuery = new URLSearchParams();
  if (date) backendQuery.set("date", date);
  if (query) backendQuery.set("query", query);
  if (payment) backendQuery.set("payment", payment);

  const response = await backendFetchWithAuth(`/api/table-orders/archive?${backendQuery.toString()}`, {
    method: "GET",
  });

  const payload = await response.json().catch(() => []);
  return NextResponse.json(payload, { status: response.status });
}

