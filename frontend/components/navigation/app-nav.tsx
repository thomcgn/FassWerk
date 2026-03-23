"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { CalendarDays, GlassWater, Home, LogIn, Plus, Receipt, ShieldCheck, Users } from "lucide-react";
import { cn } from "@/lib/utils";

const navItems = [
  { href: "/", label: "Home", icon: Home },
  { href: "/bookings", label: "Buchungen", icon: CalendarDays },
  { href: "/table-billing", label: "Tischbon", icon: Receipt },
  { href: "/inventory", label: "Inventory", icon: GlassWater },
  { href: "/sessions", label: "Sessions", icon: Users },
  { href: "/ops/auth-metrics", label: "Ops", icon: ShieldCheck },
  { href: "/login", label: "Login", icon: LogIn },
];

const mobileNavItems = [
  { href: "/", label: "Home", icon: Home },
  { href: "/bookings", label: "Buchung", icon: CalendarDays },
  { href: "/table-billing", label: "Bon", icon: Receipt },
  { href: "/inventory", label: "Inventar", icon: GlassWater },
  { href: "/sessions", label: "Sessions", icon: Users },
  { href: "/login", label: "Login", icon: LogIn },
];

function isActivePath(pathname: string, href: string) {
  return pathname === href || (href !== "/" && pathname.startsWith(href));
}

export function AppNav() {
  const pathname = usePathname();

  return (
    <>
      <header className="sticky top-0 z-40 hidden border-b border-indigo-200/70 bg-white/80 backdrop-blur md:block">
        <div className="mx-auto flex w-full max-w-7xl items-center gap-2 px-4 py-2 md:px-6">
          <Link
            href="/"
            className="inline-flex rounded-md bg-gradient-to-r from-indigo-600 to-violet-600 px-3 py-2 text-sm font-semibold text-white shadow-md shadow-indigo-500/30"
          >
            FassWerk
          </Link>
          <nav className="flex flex-wrap gap-2">
            {navItems.map((item) => {
              const Icon = item.icon;
              const isActive = isActivePath(pathname, item.href);
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={cn(
                    "inline-flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                    isActive
                      ? "bg-gradient-to-r from-indigo-600 to-violet-600 text-white shadow-sm shadow-indigo-500/30"
                      : "bg-indigo-50 text-slate-700 hover:bg-indigo-100",
                  )}
                >
                  <Icon className="h-4 w-4" />
                  <span>{item.label}</span>
                </Link>
              );
            })}
          </nav>
        </div>
      </header>

      <nav className="fixed inset-x-0 bottom-0 z-50 border-t border-indigo-200/70 bg-white/85 px-2 pb-safe-bottom pt-2 backdrop-blur md:hidden">
        <div className="pointer-events-none absolute left-1/2 top-0 -translate-x-1/2 -translate-y-1/2">
          <Link
            href="/bookings"
            aria-label="Neue Buchung"
            className="pointer-events-auto inline-flex h-14 w-14 items-center justify-center rounded-full border-4 border-white bg-gradient-to-r from-indigo-600 to-violet-600 text-white shadow-lg shadow-indigo-500/35"
          >
            <Plus className="h-6 w-6" />
          </Link>
        </div>

        <div className="mx-auto grid max-w-xl grid-cols-6 gap-1">
          {mobileNavItems.map((item, index) => {
            const Icon = item.icon;
            const isActive = isActivePath(pathname, item.href);

            const linkNode = (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "inline-flex flex-col items-center justify-center gap-1 rounded-md px-1 py-2 text-[11px] font-medium",
                  isActive ? "bg-indigo-600 text-white" : "text-slate-600 hover:bg-indigo-50",
                )}
              >
                <Icon className="h-4 w-4" />
                <span>{item.label}</span>
              </Link>
            );

            if (index === 3) {
              return (
                <div key="fab-slot" className="contents">
                  <div aria-hidden="true" />
                  {linkNode}
                </div>
              );
            }

            return linkNode;
          })}
        </div>
      </nav>
    </>
  );
}

