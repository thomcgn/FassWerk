import { NextResponse } from "next/server";
import { backendFetchWithAuth } from "@/lib/server-auth";

export async function GET() {
  const response = await backendFetchWithAuth("/api/reports/reorder-list.pdf");

  if (!response.ok) {
    return NextResponse.json({ message: "unable to fetch report" }, { status: response.status });
  }

  const pdfBytes = await response.arrayBuffer();
  return new NextResponse(pdfBytes, {
    status: 200,
    headers: {
      "Content-Type": "application/pdf",
      "Content-Disposition": "inline; filename=reorder-list.pdf",
    },
  });
}

