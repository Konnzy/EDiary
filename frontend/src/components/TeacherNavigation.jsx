import { useContext } from "react";
import { School } from "lucide-react";
import { CurrentUserContext } from "@/lib/currentUser.jsx";
import { teacherNavigationItems } from "@/lib/teacherNavigation.js";
import { cn } from "@/lib/utils.js";

export function TeacherNavigation({ activeRoute, compact = false }) {
  const currentUser = useContext(CurrentUserContext);
  const isHomeroomTeacher = Boolean(currentUser?.profile?.professional?.homeroomClasses?.length);
  const groups = [teacherNavigationItems.slice(0, 1), teacherNavigationItems.slice(1, 3)];
  if (isHomeroomTeacher) groups.push([{ label: "Мій клас", icon: School }]);
  groups.push(teacherNavigationItems.slice(3));
  const visibleGroups = compact ? [[...groups[0], ...groups[1]], ...groups.slice(2)] : groups;

  const content = visibleGroups.map((group, index) => (
    <div key={index} className={cn(
      compact ? "flex min-w-0 flex-1 items-center gap-1" : "space-y-2",
      index > 0 && (compact ? "ml-1 border-l border-slate-200 pl-1" : "mt-3 border-t border-slate-200 pt-3"),
      compact && group.length === 1 && "flex-none",
    )}>
      {group.map((item) => {
        const Icon = item.icon;
        const active = item.route === activeRoute;
        const classes = cn(
          compact ? "inline-flex h-10 min-w-10 flex-1 items-center justify-center gap-1 rounded-md px-1" : "flex h-11 w-full items-center gap-3 rounded-md px-3",
          "text-[0.82rem] font-semibold transition-colors focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-slate-400",
          active ? "bg-slate-100 text-slate-900" : "text-slate-500 hover:bg-slate-50 hover:text-slate-800",
          !item.route && "cursor-default opacity-60 hover:bg-transparent hover:text-slate-500",
        );
        const label = <><Icon className={compact ? "h-4 w-4 shrink-0" : "h-5 w-5 shrink-0"} aria-hidden="true" />{compact ? <span className="hidden whitespace-nowrap min-[640px]:inline">{item.shortLabel || item.label}</span> : item.label}</>;
        return item.route
          ? <a key={item.label} href={`#${item.route}`} className={classes} aria-label={item.label} aria-current={active ? "page" : undefined}>{label}</a>
          : <button key={item.label} type="button" disabled className={classes} aria-label={item.label} title="Мій клас: розділ у розробці">{label}</button>;
      })}
    </div>
  ));

  if (compact) return <nav className="sticky top-16 z-20 overflow-x-auto border-b border-slate-200/80 bg-white px-3 py-2 lg:hidden" aria-label="Основна навігація"><div className="flex min-w-max items-center">{content}</div></nav>;
  return <aside className="hidden w-[210px] shrink-0 bg-white px-3 py-4 lg:block"><nav aria-label="Основна навігація">{content}</nav></aside>;
}
