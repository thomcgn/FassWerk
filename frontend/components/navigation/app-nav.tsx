"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  Beer,
  CalendarDays,
  ChartNoAxesColumn,
  Home,
  LogOut,
  QrCode,
  Receipt,
  ShieldCheck,
  Sparkles,
  Users,
  Settings,
} from "lucide-react";
import { cn } from "@/lib/utils";

const publicNavItems = [
  { href: "/", label: "Drinks", icon: Home },
  { href: "/bookings", label: "Reservieren", icon: CalendarDays },
];

const staffNavItems = [
  { href: "/inventory", label: "Lagerverwaltung", icon: ChartNoAxesColumn },
  { href: "/bookings", label: "Reservierungen", icon: CalendarDays },
  { href: "/bar-admin", label: "Bar Admin", icon: Beer },
  { href: "/sales-configuration", label: "Nachbestellung", icon: Settings },
  { href: "/table-billing", label: "Tische", icon: Receipt },
  { href: "/shift-settlement", label: "Löhne", icon: Users },
  { href: "/sessions", label: "Sessions", icon: Users },
  { href: "/ops/auth-metrics", label: "Ops", icon: ShieldCheck },
];

function isActivePath(pathname: string, href: string) {
  return pathname === href || (href !== "/" && pathname.startsWith(href));
}

export function AppNav() {
  const pathname = usePathname();
  const router = useRouter();
  const [authenticated, setAuthenticated] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);

  useEffect(() => {
    let active = true;

    async function loadAuthStatus() {
      const response = await fetch("/api/auth/status", { cache: "no-store" });
      if (!active) return;
      if (!response.ok) {
        setAuthenticated(false);
        return;
      }
      const payload = (await response.json()) as { authenticated?: boolean };
      setAuthenticated(Boolean(payload.authenticated));
    }

    void loadAuthStatus();
    return () => {
      active = false;
    };
  }, [pathname]);

  const navItems = useMemo(() => (authenticated ? staffNavItems : publicNavItems), [authenticated]);

  async function logout() {
    setLoggingOut(true);
    try {
      await fetch("/api/auth/logout", { method: "POST" });
      setAuthenticated(false);
      router.replace("/");
      router.refresh();
    } finally {
      setLoggingOut(false);
    }
  }

  return (
    <>
      <header className="sticky top-0 z-40 border-b border-[color:var(--color-border-strong)] bg-[color:var(--color-surface)]/50 backdrop-blur-2xl">
        <div className="mx-auto flex w-full max-w-7xl items-center justify-between gap-4 px-3 py-3.5 sm:px-4 md:px-6">
          <Link href="/" className="inline-flex items-center gap-3 group">
            <div className="inline-flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-cyan-500 to-blue-600 text-white shadow-lg shadow-cyan-500/30 group-hover:shadow-cyan-500/50 transition-all duration-300">
              {authenticated ? <QrCode className="h-5 w-5" /> : <Sparkles className="h-5 w-5" />}
            </div>
            <span>
              <span className="block text-sm font-bold tracking-tight text-[color:var(--color-foreground)] group-hover:text-cyan-400 transition-colors">FassWerk</span>
              <span className="block text-xs text-[color:var(--color-muted-foreground)]">
                {authenticated ? "Staff Dashboard" : "Drinks · Booking · Check-in"}
              </span>
            </span>
          </Link>

          <div className="hidden min-w-0 flex-1 items-center justify-end gap-2 md:flex">
            <nav className="flex min-w-0 flex-nowrap items-center justify-end gap-1 overflow-x-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
              {navItems.map((item) => {
                const Icon = item.icon;
                const isActive = isActivePath(pathname, item.href);
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    className={cn(
                      "inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-lg border px-2.5 py-1.5 text-xs font-semibold transition-all duration-300",
                      isActive
                        ? "border-cyan-500/60 bg-gradient-to-r from-cyan-500 to-blue-600 text-white shadow-lg shadow-cyan-500/30"
                        : "border-[color:var(--color-border-strong)] bg-[color:var(--color-surface-muted)]/55 text-[color:var(--color-muted-foreground)] hover:border-cyan-300/75 hover:bg-cyan-500/25 hover:text-white hover:shadow-md hover:shadow-cyan-500/30",
                    )}
                  >
                    <Icon className="h-4.5 w-4.5" />
                    <span>{item.label}</span>
                  </Link>
                );
              })}
            </nav>

            {authenticated ? (
              <button
                type="button"
                onClick={() => void logout()}
                disabled={loggingOut}
                className="inline-flex items-center gap-2 rounded-lg border border-[color:var(--color-border-strong)] bg-[color:var(--color-surface-muted)]/60 px-4 py-2.5 text-sm font-semibold text-[color:var(--color-foreground)] hover:bg-red-500/15 hover:border-red-500/50 transition-all duration-300 disabled:opacity-60"
              >
                <LogOut className="h-4 w-4" />
                <span>{loggingOut ? "Abmelden..." : "Logout"}</span>
              </button>
            ) : (
              <Link
                href="/login"
                className="inline-flex items-center gap-2 rounded-lg bg-gradient-to-r from-cyan-500 to-blue-600 px-4 py-2.5 text-sm font-semibold text-white shadow-lg shadow-cyan-500/30 hover:shadow-cyan-500/50 transition-all duration-300"
              >
                <ShieldCheck className="h-4 w-4" />
                Staff Login
              </Link>
            )}
          </div>
        </div>
      </header>

      <nav className="fixed inset-x-0 bottom-0 z-50 border-t border-[color:var(--color-border-strong)] bg-[color:var(--color-surface)]/80 px-2 pb-safe-bottom pt-2 backdrop-blur-2xl md:hidden">
        <div className={cn("mx-auto grid max-w-xl gap-1.5", authenticated ? "grid-cols-4" : "grid-cols-2")}>
          {(authenticated ? staffNavItems : publicNavItems).map((item) => {
            const Icon = item.icon;
            const isActive = isActivePath(pathname, item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "inline-flex min-w-0 flex-col items-center justify-center gap-1.5 rounded-2xl px-2 py-3 text-[10px] font-semibold transition-all duration-300",
                  isActive
                    ? "bg-gradient-to-br from-cyan-500 to-blue-600 text-white shadow-lg shadow-cyan-500/40"
                    : "text-[color:var(--color-muted-foreground)] hover:bg-cyan-500/25 hover:text-white",
                )}
              >
                <Icon className="h-5 w-5" />
                <span className="w-full truncate text-center">{item.label}</span>
              </Link>
            );
          })}
        </div>
        {authenticated ? (
          <button
            type="button"
            onClick={() => void logout()}
            disabled={loggingOut}
            className="mx-auto mt-2 flex w-full items-center justify-center gap-2 rounded-xl px-3 py-2 text-xs font-semibold text-[color:var(--color-muted-foreground)] transition-all duration-300 hover:bg-red-500/10 hover:text-red-300"
          >
            <LogOut className="h-3.5 w-3.5" />
            {loggingOut ? "Abmelden..." : "Logout"}
          </button>
        ) : null}
      </nav>
    </>
  );
}
