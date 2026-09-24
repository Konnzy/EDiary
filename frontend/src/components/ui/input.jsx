import React from "react";
import { cn } from "@/lib/utils.js";

const Input = React.forwardRef(({ className, type, ...props }, ref) => (
  <input
    type={type}
    className={cn(
      "flex h-10 w-full rounded-md border border-slate-300/85 bg-slate-50/40 px-3 py-2 text-[0.82rem] text-slate-800 shadow-[inset_0_1px_1px_rgba(15,23,42,0.03)] transition-all duration-200 placeholder:text-slate-500 hover:border-slate-400/70 focus-visible:border-primary/45 focus-visible:bg-white focus-visible:outline-hidden focus-visible:ring-0 focus-visible:shadow-glow disabled:cursor-not-allowed disabled:opacity-50",
      className,
    )}
    ref={ref}
    {...props}
  />
));

Input.displayName = "Input";

export { Input };
