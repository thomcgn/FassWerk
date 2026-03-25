import * as React from "react";
import { cn } from "@/lib/utils";

export const Input = React.forwardRef<HTMLInputElement, React.ComponentProps<"input">>(
  ({ className, type, ...props }, ref) => {
    return (
      <input
        type={type}
        className={cn(
          "flex h-11 w-full rounded-lg border border-[color:var(--color-border-strong)] bg-[color:var(--color-surface)]/82 backdrop-blur-sm px-4 py-2 text-sm text-[color:var(--color-foreground)] transition-all outline-none placeholder:text-[color:var(--color-muted-foreground)] focus-visible:border-cyan-500/60 focus-visible:ring-4 focus-visible:ring-cyan-500/25 focus-visible:bg-[color:var(--color-surface)]/95 disabled:cursor-not-allowed disabled:opacity-50",
          className,
        )}
        ref={ref}
        {...props}
      />
    );
  },
);
Input.displayName = "Input";
