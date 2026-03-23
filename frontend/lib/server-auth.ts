import { cookies } from "next/headers";
import { BACKEND_BASE_URL } from "@/lib/config";
import {
  ACCESS_TOKEN_COOKIE,
  COOKIE_MAX_AGE_ACCESS_SECONDS,
  COOKIE_MAX_AGE_REFRESH_SECONDS,
  REFRESH_TOKEN_COOKIE,
} from "@/lib/auth-cookies";
import type { LoginResponse, SessionResponse } from "@/types/api";

const isProd = process.env.NODE_ENV === "production";

async function applyTokenCookies(payload: LoginResponse) {
  const cookieStore = await cookies();

  cookieStore.set(ACCESS_TOKEN_COOKIE, payload.accessToken, {
    httpOnly: true,
    sameSite: "lax",
    secure: isProd,
    path: "/",
    maxAge: payload.accessExpiresInSeconds || COOKIE_MAX_AGE_ACCESS_SECONDS,
  });

  cookieStore.set(REFRESH_TOKEN_COOKIE, payload.refreshToken, {
    httpOnly: true,
    sameSite: "lax",
    secure: isProd,
    path: "/",
    maxAge: payload.refreshExpiresInSeconds || COOKIE_MAX_AGE_REFRESH_SECONDS,
  });
}

export async function clearTokenCookies() {
  const cookieStore = await cookies();
  cookieStore.set(ACCESS_TOKEN_COOKIE, "", {
    httpOnly: true,
    sameSite: "lax",
    secure: isProd,
    path: "/",
    maxAge: 0,
  });
  cookieStore.set(REFRESH_TOKEN_COOKIE, "", {
    httpOnly: true,
    sameSite: "lax",
    secure: isProd,
    path: "/",
    maxAge: 0,
  });
}

export async function loginAgainstBackend(
  email: string,
  password: string,
): Promise<{ ok: true; payload: LoginResponse } | { ok: false; status: number; body: unknown }> {
  const response = await fetch(`${BACKEND_BASE_URL}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
    cache: "no-store",
  });

  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    return { ok: false, status: response.status, body };
  }

  const payload = body as LoginResponse;
  await applyTokenCookies(payload);
  return { ok: true, payload };
}

export async function refreshSession(): Promise<boolean> {
  const cookieStore = await cookies();
  const refreshToken = cookieStore.get(REFRESH_TOKEN_COOKIE)?.value;
  if (!refreshToken) {
    return false;
  }

  const response = await fetch(`${BACKEND_BASE_URL}/api/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
    cache: "no-store",
  });

  if (!response.ok) {
    await clearTokenCookies();
    return false;
  }

  const payload = (await response.json()) as LoginResponse;
  await applyTokenCookies(payload);
  return true;
}

export async function backendFetchWithAuth(
  path: string,
  init?: RequestInit,
): Promise<Response> {
  const cookieStore = await cookies();
  let accessToken = cookieStore.get(ACCESS_TOKEN_COOKIE)?.value;

  if (!accessToken && !(await refreshSession())) {
    return new Response(null, { status: 401 });
  }

  if (!accessToken) {
    accessToken = (await cookies()).get(ACCESS_TOKEN_COOKIE)?.value;
  }

  let response = await fetch(`${BACKEND_BASE_URL}${path}`, {
    ...init,
    headers: {
      ...(init?.headers ?? {}),
      Authorization: `Bearer ${accessToken}`,
    },
    cache: "no-store",
  });

  if (response.status !== 401) {
    return response;
  }

  const refreshed = await refreshSession();
  if (!refreshed) {
    return response;
  }

  const retryAccessToken = (await cookies()).get(ACCESS_TOKEN_COOKIE)?.value;

  response = await fetch(`${BACKEND_BASE_URL}${path}`, {
    ...init,
    headers: {
      ...(init?.headers ?? {}),
      Authorization: `Bearer ${retryAccessToken}`,
    },
    cache: "no-store",
  });
  return response;
}

export async function logoutAgainstBackend(): Promise<void> {
  const cookieStore = await cookies();
  const accessToken = cookieStore.get(ACCESS_TOKEN_COOKIE)?.value;
  const refreshToken = cookieStore.get(REFRESH_TOKEN_COOKIE)?.value;

  if (refreshToken) {
    const headers: Record<string, string> = { "Content-Type": "application/json" };
    if (accessToken) {
      headers.Authorization = `Bearer ${accessToken}`;
    }

    await fetch(`${BACKEND_BASE_URL}/api/auth/logout`, {
      method: "POST",
      headers,
      body: JSON.stringify({ refreshToken }),
      cache: "no-store",
    });
  }

  await clearTokenCookies();
}

export async function listSessionsAgainstBackend(): Promise<{
  status: number;
  sessions: SessionResponse[];
}> {
  const currentRefreshToken = (await cookies()).get(REFRESH_TOKEN_COOKIE)?.value;
  const response = await backendFetchWithAuth("/api/auth/sessions", {
    method: "GET",
    headers: {
      "X-Current-Refresh-Token": currentRefreshToken ?? "",
    },
  });
  if (!response.ok) {
    return { status: response.status, sessions: [] };
  }
  const payload = (await response.json()) as SessionResponse[];
  return { status: 200, sessions: payload };
}

export async function revokeSessionAgainstBackend(sessionId: number): Promise<boolean> {
  const response = await backendFetchWithAuth(`/api/auth/sessions/${sessionId}`, {
    method: "DELETE",
  });
  return response.status === 204;
}

export async function logoutAllSessionsAgainstBackend(): Promise<boolean> {
  const response = await backendFetchWithAuth("/api/auth/logout-all", {
    method: "POST",
  });

  if (response.status === 204) {
    await clearTokenCookies();
    return true;
  }

  return false;
}



