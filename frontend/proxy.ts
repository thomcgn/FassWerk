import { NextRequest, NextResponse } from "next/server";
import { isAllowedApiRequest } from "@/lib/csrf";

export function proxy(request: NextRequest) {
  // NextURL normalizes loopback hosts; retain the browser's actual HTTP Host, not forwarded headers.
  const host = request.headers.get("host");
  const expectedOrigin = process.env.APP_ORIGIN
    || (host ? `${request.nextUrl.protocol}//${host}` : request.nextUrl.origin);
  if (!isAllowedApiRequest(
    request.method,
    request.headers.get("origin"),
    request.headers.get("sec-fetch-site"),
    expectedOrigin,
  )) {
    return NextResponse.json(
      { message: "Cross-origin request denied" },
      { status: 403, headers: { "Cache-Control": "no-store" } },
    );
  }
  return NextResponse.next();
}

export const config = { matcher: "/api/:path*" };
