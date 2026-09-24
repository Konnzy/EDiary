import React from "react";
import { cva } from "class-variance-authority";
import { cn } from "@/lib/utils.js";

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-xl text-sm font-medium transition-all duration-200 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-60",
  {
    variants: {
      variant: {
        default:
          "bg-slate-800 text-primary-foreground shadow-[0_14px_30px_-22px_rgba(15,23,42,0.8)] hover:-translate-y-0.5 hover:bg-slate-700 hover:shadow-[0_18px_36px_-22px_rgba(15,23,42,0.85)] active:translate-y-0 active:scale-[0.99]",
        ghost:
          "hover:bg-secondary/75 hover:text-secondary-foreground active:scale-[0.98]",
      },
      size: {
        default: "h-11 px-4 py-2",
        icon: "h-10 w-10",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  },
);

const Button = React.forwardRef(
  ({ className, variant, size, ...props }, ref) => {
    return (
      <button
        className={cn(buttonVariants({ variant, size, className }))}
        ref={ref}
        {...props}
      />
    );
  },
);

Button.displayName = "Button";

export { Button, buttonVariants };
