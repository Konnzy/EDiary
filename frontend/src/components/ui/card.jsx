import React from "react";
import { cn } from "@/lib/utils.js";

const Card = React.forwardRef(({ className, ...props }, ref) => (
  <section
    ref={ref}
    className={cn(
      "rounded-xl border border-slate-200/90 bg-white text-card-foreground shadow-[0_10px_40px_-32px_rgba(15,23,42,0.55)] ring-1 ring-slate-900/[0.015]",
      className,
    )}
    {...props}
  />
));

Card.displayName = "Card";

const CardHeader = ({ className, ...props }) => (
  <div className={cn("flex flex-col space-y-2", className)} {...props} />
);

const CardContent = ({ className, ...props }) => (
  <div className={cn(className)} {...props} />
);

export { Card, CardContent, CardHeader };
