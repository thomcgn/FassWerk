import { NextResponse } from "next/server";
import { backendFetchWithAuth } from "@/lib/server-auth";

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const supplier = searchParams.get("supplier")?.trim();
  const targetPath = supplier
    ? `/api/reports/reorder-list.pdf?supplier=${encodeURIComponent(supplier)}`
    : "/api/reports/reorder-list.pdf";

  const response = await backendFetchWithAuth(targetPath);

  if (!response.ok) {
    return NextResponse.json({ message: "unable to fetch report" }, { status: response.status });
  }

  const pdfBytes = await response.arrayBuffer();
  const filename = supplier ? `reorder-list-${supplier}.pdf` : "reorder-list.pdf";
  return new NextResponse(pdfBytes, {
    status: 200,
    headers: {
      "Content-Type": "application/pdf",
      "Content-Disposition": `inline; filename=${filename}`,
    },
  });
}

