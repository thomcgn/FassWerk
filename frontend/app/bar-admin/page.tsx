import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { ACCESS_TOKEN_COOKIE, REFRESH_TOKEN_COOKIE } from "@/lib/auth-cookies";
import BarAdminClient from "@/app/bar-admin/bar-admin-client";

export default async function BarAdminPage() {
  const cookieStore = await cookies();
  const hasAnyToken =
    cookieStore.has(ACCESS_TOKEN_COOKIE) || cookieStore.has(REFRESH_TOKEN_COOKIE);

  if (!hasAnyToken) {
    redirect("/login");
  }

  return <BarAdminClient />;
}

