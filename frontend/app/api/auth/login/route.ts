import { NextResponse } from "next/server";
import { loginAgainstBackend } from "@/lib/server-auth";

export async function POST(request: Request) {
  const body = (await request.json().catch(() => null)) as
    | { email?: string; password?: string }
    | null;

  if (!body?.email || !body.password) {
    return NextResponse.json(
      { message: "email and password are required" },
      { status: 400 },
    );
  }

  const result = await loginAgainstBackend(body.email, body.password);
  if (!result.ok) {
    return NextResponse.json(result.body, { status: result.status });
  }

  return NextResponse.json({
    role: result.payload.role,
    displayName: result.payload.displayName,
  });
}

