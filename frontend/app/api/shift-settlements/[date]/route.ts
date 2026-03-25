import { NextResponse } from "next/server";
import { backendFetchWithAuth } from "@/lib/server-auth";

type Params = { params: Promise<{ date: string }> };

export async function GET(_: Request, { params }: Params) {
  const { date } = await params;
  const response = await backendFetchWithAuth(`/api/shift-settlements/${date}`, {
    method: "GET",
  });

  if (response.status === 401) {
    return NextResponse.json({ message: "unauthorized" }, { status: 401 });
  }

  const payload = await response.json().catch(() => ({}));
  return NextResponse.json(payload, { status: response.status });
}

export async function PUT(request: Request, { params }: Params) {
  const { date } = await params;
  const body = await request.json().catch(() => ({}));
  const response = await backendFetchWithAuth(`/api/shift-settlements/${date}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

  if (response.status === 401) {
    return NextResponse.json({ message: "unauthorized" }, { status: 401 });
  }

  const payload = await response.json().catch(() => ({}));
  return NextResponse.json(payload, { status: response.status });
}

