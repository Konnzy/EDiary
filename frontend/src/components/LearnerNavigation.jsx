import { BookOpen, CalendarDays, GraduationCap, Home, Mail, User } from "lucide-react";
import { cn } from "@/lib/utils.js";

export function LearnerNavigation({ role, activeRoute, compact = false }) {
  const root = role.toLowerCase();
  const groups = [
    [{ label: "Головна", icon: Home, route: root }],
    [{ label: "Розклад", icon: CalendarDays, route: `${root}/schedule` }, { label: "Предмети", icon: BookOpen, route: `${root}/subjects` }],
    [{ label: "Вчителі", icon: GraduationCap, route: `${root}/teachers` }, { label: "Повідомлення", shortLabel: "Повідомл.", icon: Mail, route: `${root}/messages` }, { label: "Профіль", icon: User, route: `${root}/profile` }],
  ];
  const visibleGroups = compact ? [[...groups[0], ...groups[1]], ...groups.slice(2)] : groups;
  const content = visibleGroups.map((items, index) => <div key={index} className={cn(compact ? "flex min-w-0 flex-1 items-center gap-1" : "space-y-2", index > 0 && (compact ? "ml-1 border-l border-slate-200 pl-1" : "mt-3 border-t border-slate-200 pt-3"))}>
    {items.map((item) => { const Icon = item.icon; return <a key={item.route} href={`#${item.route}`} aria-label={item.label} aria-current={item.route === activeRoute ? "page" : undefined} className={cn(compact ? "inline-flex h-10 min-w-10 flex-1 items-center justify-center gap-1 rounded-md px-1" : "flex h-11 w-full items-center gap-3 rounded-md px-3", "text-[0.82rem] font-semibold transition-colors focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-slate-400", item.route === activeRoute ? "bg-slate-100 text-slate-900" : "text-slate-500 hover:bg-slate-50 hover:text-slate-800")}><Icon className={compact ? "h-4 w-4 shrink-0" : "h-5 w-5 shrink-0"} aria-hidden="true" />{compact ? <span className="hidden whitespace-nowrap min-[640px]:inline">{item.shortLabel || item.label}</span> : item.label}</a>; })}
  </div>);
  if (compact) return <nav className="sticky top-16 z-20 overflow-x-auto border-b border-slate-200/80 bg-white px-3 py-2 lg:hidden" aria-label="Основна навігація"><div className="flex min-w-max items-center">{content}</div></nav>;
  return <aside className="hidden w-[210px] shrink-0 bg-white px-3 py-4 lg:block"><nav aria-label="Основна навігація">{content}</nav></aside>;
}
