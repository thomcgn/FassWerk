import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { ACCESS_TOKEN_COOKIE, REFRESH_TOKEN_COOKIE } from "@/lib/auth-cookies";
import InventoryClient from "@/app/inventory/inventory-client";

export default async function InventoryPage() {
  const cookieStore = await cookies();
  const hasAnyToken =
    cookieStore.has(ACCESS_TOKEN_COOKIE) || cookieStore.has(REFRESH_TOKEN_COOKIE);

  if (!hasAnyToken) {
    redirect("/login");
  }

  return <InventoryClient />;
}

