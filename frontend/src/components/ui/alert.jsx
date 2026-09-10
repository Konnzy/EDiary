import React from "react";
import { cn } from "@/lib/utils.js";

const Alert = React.forwardRef(({ className, children, ...props }, ref) => (
  <div
    ref={ref}
    role="alert"
    className={cn(
      "flex items-center gap-3 rounded-md border border-red-200 bg-red-50 px-3.5 py-3 text-[0.82rem] text-red-600 shadow-none",
      className,
    )}
    {...props}
  >
    <span
      className="flex h-4 w-4 shrink-0 items-center justify-center rounded-full bg-red-500 text-[10px] font-bold leading-none text-white"
      aria-hidden="true"
    >
      !
    </span>
    <div>{children}</div>
  </div>
));

Alert.displayName = "Alert";

export { Alert };
