import * as React from "react";
import { cn } from "@/lib/utils";

export const Select = React.forwardRef<HTMLSelectElement, React.ComponentProps<"select">>(
  ({ className, children, ...props }, ref) => {
    return (
      <select
        ref={ref}
        className={cn(
          "flex h-11 w-full rounded-lg border border-[color:var(--color-border-strong)] bg-[color:var(--color-surface)]/82 backdrop-blur-sm px-4 py-2 text-sm text-[color:var(--color-foreground)] transition-all outline-none focus-visible:border-cyan-500/60 focus-visible:ring-4 focus-visible:ring-cyan-500/25 disabled:cursor-not-allowed disabled:opacity-50",
          className,
        )}
        {...props}
      >
        {children}
      </select>
    );
  },
);
Select.displayName = "Select";
