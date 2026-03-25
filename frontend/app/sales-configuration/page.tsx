import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { ACCESS_TOKEN_COOKIE, REFRESH_TOKEN_COOKIE } from "@/lib/auth-cookies";
import { SalesConfigurationPage } from "@/components/sales-configuration-page";

export default async function SalesConfigPage() {
  const cookieStore = await cookies();
  const hasAnyToken =
    cookieStore.has(ACCESS_TOKEN_COOKIE) || cookieStore.has(REFRESH_TOKEN_COOKIE);

  if (!hasAnyToken) {
    redirect("/login");
  }

  return (
    <main className="flex-1 p-8">
      <SalesConfigurationPage />
    </main>
  );
}

