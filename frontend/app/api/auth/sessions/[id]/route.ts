import { NextResponse } from "next/server";
import { revokeSessionAgainstBackend } from "@/lib/server-auth";

type Params = { id: string };

export async function DELETE(
  _request: Request,
  context: { params: Promise<Params> },
) {
  const { id } = await context.params;
  const sessionId = Number(id);
  if (Number.isNaN(sessionId) || sessionId <= 0) {
    return NextResponse.json({ message: "invalid session id" }, { status: 400 });
  }

  const revoked = await revokeSessionAgainstBackend(sessionId);
  return new NextResponse(null, { status: revoked ? 204 : 404 });
}

