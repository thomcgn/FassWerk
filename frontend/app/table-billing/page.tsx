import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { ACCESS_TOKEN_COOKIE, REFRESH_TOKEN_COOKIE } from "@/lib/auth-cookies";
import TableBillingClient from "@/app/table-billing/table-billing-client";

export default async function TableBillingPage() {
  const cookieStore = await cookies();
  const hasAnyToken =
    cookieStore.has(ACCESS_TOKEN_COOKIE) || cookieStore.has(REFRESH_TOKEN_COOKIE);

  if (!hasAnyToken) {
    redirect("/login");
  }

  return <TableBillingClient />;
}

