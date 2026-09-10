import React from "react";
import { Check } from "lucide-react";
import { cn } from "@/lib/utils.js";

const Checkbox = React.forwardRef(
  ({ id, checked, onCheckedChange, className, ...props }, ref) => (
    <button
      ref={ref}
      id={id}
      type="button"
      role="checkbox"
      aria-checked={checked}
      onClick={() => onCheckedChange?.(!checked)}
      className={cn(
        "flex h-4 w-4 shrink-0 items-center justify-center rounded-[3px] border border-slate-400 bg-white transition-all duration-200 hover:border-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2",
        checked && "border-slate-800 bg-white text-slate-900",
        className,
      )}
      {...props}
    >
      {checked ? <Check className="h-3 w-3" aria-hidden="true" /> : null}
    </button>
  ),
);

Checkbox.displayName = "Checkbox";

export { Checkbox };
