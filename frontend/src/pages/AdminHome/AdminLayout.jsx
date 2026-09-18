import {
  BookOpen,
  CalendarDays,
  GraduationCap,
  Home,
  Layers3,
  LogOut,
  Mail,
  UserRound,
  Users,
} from "lucide-react";
import { AppFooter } from "@/components/AppFooter.jsx";
import { NotificationButton } from "@/components/NotificationButton.jsx";
import { adminAuthApi } from "@/lib/api.js";
import { isGuardedActionBlocked } from "@/lib/navigationGuard.js";
import { cn } from "@/lib/utils.js";

const admin = {
  name: "Олена Ткаченко",
  role: "Адміністратор",
};

const navigationItems = [
  { label: "Головна", icon: Home, route: "admin/home" },
  { label: "Заявки", icon: Mail, route: "admin/requests" },
  { label: "Користувачі", icon: Users, route: "admin/users" },
  { label: "Класи", icon: Layers3, route: "admin/classes" },
  { label: "Предмети", icon: BookOpen, route: "admin/subjects" },
  { label: "Розклад", icon: CalendarDays, route: "admin/schedule" },
];

export function AdminLayout({ activeItem, title, subtitle, breadcrumb, actions, children }) {
  return (
    <div className="flex min-h-screen flex-col bg-slate-50 text-slate-800">
      <TopBar />
      <MobileNavigation activeItem={activeItem} />

      <div className="flex flex-1">
        <Sidebar activeItem={activeItem} />

        <div className="flex min-w-0 flex-1 flex-col lg:border-l lg:border-slate-200/80">
          <main className="min-w-0 flex-1 px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
            <div className="mx-auto w-full max-w-[1280px] 2xl:max-w-[1440px]">
              <header className="mb-7 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                <div>
                  {breadcrumb ? (
                    <nav className="mb-3 flex items-center gap-2 text-[0.78rem] text-slate-500" aria-label="Навігаційний шлях">
                      {breadcrumb}
                    </nav>
                  ) : null}
                  <h1 className="text-[1.55rem] font-bold leading-tight text-slate-950 sm:text-[1.85rem]">
                    {title}
                  </h1>
                  {subtitle ? (
                    <p className="mt-2 max-w-2xl text-[0.92rem] leading-6 text-slate-500">{subtitle}</p>
                  ) : null}
                </div>
                {actions ? <div className="shrink-0">{actions}</div> : null}
              </header>
              {children}
            </div>
          </main>
          <AppFooter />
        </div>
      </div>
    </div>
  );
}

function TopBar() {
  const performLogout = async () => {
    try {
      await adminAuthApi.logout();
    } catch {
      // The server session may already be expired.
    } finally {
      window.location.hash = "admin";
    }
  };
  const handleLogout = () => {
    if (!isGuardedActionBlocked(performLogout)) void performLogout();
  };

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-slate-200/80 bg-white px-4 sm:px-5 lg:px-6">
      <a
        href="#admin/home"
        className="inline-flex min-w-0 items-center gap-2 text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        aria-label="Електронний щоденник - адмін-панель"
      >
        <GraduationCap className="h-7 w-7 shrink-0 text-slate-900 sm:h-8 sm:w-8" strokeWidth={2.4} aria-hidden="true" />
        <span className="max-w-[150px] truncate text-[0.92rem] font-bold min-[420px]:max-w-none sm:text-[1.05rem]">
          Електронний щоденник
        </span>
      </a>

      <div className="flex items-center gap-1.5 sm:gap-4">
        <NotificationButton />

        <div className="hidden items-center gap-3 rounded-sm bg-slate-50 px-3 py-1.5 sm:flex">
          <div className="text-right leading-tight">
            <div className="text-[0.78rem] font-bold text-slate-800">{admin.name}</div>
            <div className="text-[0.7rem] text-slate-500">{admin.role}</div>
          </div>
          <UserRound className="h-8 w-8 text-slate-900" strokeWidth={1.8} aria-hidden="true" />
        </div>

        <button
          type="button"
          onClick={handleLogout}
          className="inline-flex h-9 items-center gap-2 rounded-md px-2 text-[0.82rem] font-semibold text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 sm:h-10 sm:px-3"
        >
          <LogOut className="h-4 w-4" aria-hidden="true" />
          <span className="hidden sm:inline">Вийти</span>
        </button>
      </div>
    </header>
  );
}

function NavigationItem({ item, activeItem, compact = false }) {
  const Icon = item.icon;
  const className = cn(
    compact
      ? "inline-flex h-10 min-w-0 items-center justify-center rounded-md px-1"
      : "flex h-11 w-full items-center gap-3 rounded-md px-3 text-left",
    "text-[0.82rem] font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2",
    activeItem === item.label
      ? "bg-slate-100 text-slate-900"
      : "text-slate-500 hover:bg-slate-50 hover:text-slate-800",
  );
  const content = (
    <>
      <Icon className={cn("shrink-0", compact ? "h-4 w-4" : "h-5 w-5")} aria-hidden="true" />
      {!compact ? item.label : null}
    </>
  );

  if (!item.route) {
    return <button type="button" className={className} disabled title={`${item.label}: розділ у розробці`}>{content}</button>;
  }
  return (
    <a
      href={`#${item.route}`}
      className={className}
      aria-label={compact ? item.label : undefined}
      aria-current={activeItem === item.label ? "page" : undefined}
      title={compact ? item.label : undefined}
    >
      {content}
    </a>
  );
}

function MobileNavigation({ activeItem }) {
  return (
    <nav className="sticky top-16 z-20 border-b border-slate-200/80 bg-white px-3 py-2 lg:hidden" aria-label="Основна навігація адміністратора">
      <div className="grid grid-cols-6 gap-1">
        {navigationItems.map((item) => (
          <NavigationItem key={item.label} item={item} activeItem={activeItem} compact />
        ))}
      </div>
    </nav>
  );
}

function Sidebar({ activeItem }) {
  return (
    <aside className="hidden w-[220px] shrink-0 bg-white px-3 py-4 lg:block">
      <nav className="space-y-2" aria-label="Основна навігація адміністратора">
        {navigationItems.map((item) => (
          <NavigationItem key={item.label} item={item} activeItem={activeItem} />
        ))}
      </nav>
    </aside>
  );
}
