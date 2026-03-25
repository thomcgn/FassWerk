import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const badgeVariants = cva("inline-flex items-center rounded-lg px-3 py-1.5 text-xs font-semibold transition-all", {
  variants: {
    variant: {
      default: "bg-cyan-500/35 text-cyan-100 border border-cyan-500/60",
      success: "bg-green-500/35 text-green-100 border border-green-500/60",
      warning: "bg-amber-500/35 text-amber-100 border border-amber-500/60",
      destructive: "bg-red-500/35 text-red-100 border border-red-500/60",
      muted: "border border-[color:var(--color-border-strong)] bg-[color:var(--color-surface-muted)]/80 text-[color:var(--color-foreground)]",
    },
  },
  defaultVariants: {
    variant: "default",
  },
});

export interface BadgeProps
  extends React.HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof badgeVariants> {}

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ variant }), className)} {...props} />;
}
