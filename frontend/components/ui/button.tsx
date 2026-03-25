import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const buttonVariants = cva(
  "inline-flex max-w-full min-w-0 items-center justify-center gap-2 overflow-hidden rounded-lg text-sm font-semibold transition-all disabled:pointer-events-none disabled:opacity-50 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-[color:var(--ring)] [&_span]:max-w-full [&_span]:truncate",
  {
    variants: {
      variant: {
        default:
          "bg-gradient-to-r from-cyan-500 to-blue-600 text-white shadow-lg shadow-cyan-500/30 hover:shadow-cyan-500/50",
        secondary:
          "border border-cyan-400/60 bg-cyan-500/20 text-cyan-100 hover:bg-cyan-500/30 hover:border-cyan-300/80",
        outline:
          "border border-[color:var(--color-border-strong)] bg-[color:var(--color-surface)]/65 text-[color:var(--color-foreground)] hover:bg-[color:var(--color-surface-muted)] hover:border-cyan-500/45",
        ghost: "text-[color:var(--color-muted-foreground)] hover:text-[color:var(--color-foreground)] hover:bg-white/10",
        destructive: "bg-red-600 text-white hover:bg-red-700 shadow-lg shadow-red-600/30",
      },
      size: {
        default: "h-10 px-4 py-2",
        sm: "h-9 px-3 py-1.5 rounded-md text-xs",
        lg: "h-12 px-6 py-3 text-base rounded-lg",
        icon: "h-10 w-10",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  },
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, ...props }, ref) => {
    return <button ref={ref} className={cn(buttonVariants({ variant, size, className }))} {...props} />;
  },
);
Button.displayName = "Button";
