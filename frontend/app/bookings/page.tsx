import { cookies } from "next/headers";
import { ACCESS_TOKEN_COOKIE, REFRESH_TOKEN_COOKIE } from "@/lib/auth-cookies";
import BookingsClient from "@/app/bookings/bookings-client";

export default async function BookingsPage() {
  const cookieStore = await cookies();
  const isAuthenticated =
    cookieStore.has(ACCESS_TOKEN_COOKIE) || cookieStore.has(REFRESH_TOKEN_COOKIE);

  return <BookingsClient isAuthenticated={isAuthenticated} />;
}

