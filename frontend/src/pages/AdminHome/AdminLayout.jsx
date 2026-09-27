import { useContext, useEffect, useState } from "react";
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
import { AdminRequestNotifications } from "@/components/AdminRequestNotifications.jsx";
import { Alert } from "@/components/ui/alert.jsx";
import { adminAuthApi, adminUsersApi } from "@/lib/api.js";
import { CurrentUserContext } from "@/lib/currentUser.jsx";
import { isGuardedActionBlocked } from "@/lib/navigationGuard.js";
import { cn } from "@/lib/utils.js";
import { AdminSelfDialog } from "@/pages/AdminHome/AdminSelfDialog.jsx";

const navigationItems = [
  { label: "Головна", icon: Home, route: "admin/home" },
  { label: "Заявки", icon: Mail, route: "admin/requests" },
  { label: "Користувачі", icon: Users, route: "admin/users" },
  { label: "Класи", icon: Layers3, route: "admin/classes" },
  { label: "Предмети", icon: BookOpen, route: "admin/subjects" },
  { label: "Розклад", icon: CalendarDays, route: "admin/schedule" },
];
const navigationGroups = [navigationItems.slice(0, 1), navigationItems.slice(1, 3), navigationItems.slice(3)];

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
  const currentUser = useContext(CurrentUserContext);
  const [account, setAccount] = useState(null);
  const [accountError, setAccountError] = useState("");
  const [selfDialogOpen, setSelfDialogOpen] = useState(false);

  useEffect(() => {
    let active = true;
    adminUsersApi.findMe().then((result) => {
      if (active) {
        setAccount(result);
        setAccountError("");
      }
    }).catch((error) => {
      if (active) setAccountError(error.message || "Не вдалося завантажити дані адміністратора.");
    });
    return () => { active = false; };
  }, []);

  const openSelfDialog = async () => {
    setSelfDialogOpen(true);
    if (!account) {
      try {
        setAccount(await adminUsersApi.findMe());
        setAccountError("");
      } catch (error) {
        setAccountError(error.message || "Не вдалося завантажити дані адміністратора.");
      }
    }
  };

  const saveSelf = (updated, loginChanged) => {
    setAccount(updated);
    setSelfDialogOpen(false);
    if (loginChanged) window.location.hash = "admin";
  };

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
        className="inline-flex min-w-0 items-center gap-2 text-slate-900 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        aria-label="Електронний щоденник - адмін-панель"
      >
        <GraduationCap className="h-7 w-7 shrink-0 text-slate-900 sm:h-8 sm:w-8" strokeWidth={2.4} aria-hidden="true" />
        <span className="max-w-[150px] truncate text-[0.92rem] font-bold min-[420px]:max-w-none sm:text-[1.05rem]">
          Електронний щоденник
        </span>
      </a>

      <div className="flex items-center gap-1.5 sm:gap-4">
        <AdminRequestNotifications />

        <button
          type="button"
          onClick={openSelfDialog}
          aria-label="Редагувати дані адміністратора"
          className="inline-flex items-center gap-3 rounded-md bg-slate-50 px-2 py-1.5 transition-colors hover:bg-slate-100 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-slate-500 focus-visible:ring-offset-2 sm:px-3"
        >
          <span className="hidden text-right leading-tight sm:block">
            <span className="block max-w-[190px] truncate text-[0.78rem] font-bold text-slate-800">
              {account ? `${account.lastName} ${account.firstName}` : currentUser?.session?.login || "Адміністратор"}
            </span>
            <span className="block text-[0.7rem] text-slate-500">Адміністратор</span>
          </span>
          <UserRound className="h-8 w-8 text-slate-900" strokeWidth={1.8} aria-hidden="true" />
        </button>

        <button
          type="button"
          onClick={handleLogout}
          className="inline-flex h-9 items-center gap-2 rounded-md px-2 text-[0.82rem] font-semibold text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-900 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 sm:h-10 sm:px-3"
        >
          <LogOut className="h-4 w-4" aria-hidden="true" />
          <span className="hidden sm:inline">Вийти</span>
        </button>
      </div>
      {selfDialogOpen && account ? (
        <AdminSelfDialog account={account} onClose={() => setSelfDialogOpen(false)} onSaved={saveSelf} />
      ) : null}
      {selfDialogOpen && !account ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4"
          role="presentation"
          onMouseDown={(event) => event.target === event.currentTarget && setSelfDialogOpen(false)}
        >
          <section role="dialog" aria-modal="true" aria-label="Дані адміністратора" className="w-full max-w-[420px] rounded-lg bg-white p-5 shadow-2xl">
            <p className="font-bold">Дані адміністратора</p>
            {accountError ? <div className="mt-4"><Alert>{accountError}</Alert></div> : (
              <p role="status" className="mt-4 text-sm text-slate-500">Завантаження даних…</p>
            )}
            <div className="mt-5 flex justify-end gap-2">
              <button type="button" onClick={() => setSelfDialogOpen(false)} className="rounded-md border border-slate-300 px-3 py-2 text-sm">Закрити</button>
              {accountError ? <button type="button" onClick={openSelfDialog} className="rounded-md bg-slate-900 px-3 py-2 text-sm text-white">Повторити</button> : null}
            </div>
          </section>
        </div>
      ) : null}
    </header>
  );
}

function NavigationItem({ item, activeItem, compact = false }) {
  const Icon = item.icon;
  const className = cn(
    compact
      ? "inline-flex h-10 w-full min-w-0 items-center justify-center rounded-md px-1"
      : "flex h-11 w-full items-center gap-3 rounded-md px-3 text-left",
    "text-[0.82rem] font-semibold transition-colors focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2",
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
      <div className="grid grid-cols-6 items-center gap-1">
        {navigationGroups.map((group, index) => (
          <div key={index} className={cn("grid min-w-0 gap-1", index === 0 ? "col-span-1 grid-cols-1" : index === 1 ? "col-span-2 grid-cols-2 border-l border-slate-200 pl-1" : "col-span-3 grid-cols-3 border-l border-slate-200 pl-1")}>
            {group.map((item) => <NavigationItem key={item.label} item={item} activeItem={activeItem} compact />)}
          </div>
        ))}
      </div>
    </nav>
  );
}

function Sidebar({ activeItem }) {
  return (
    <aside className="hidden w-[220px] shrink-0 bg-white px-3 py-4 lg:block">
      <nav aria-label="Основна навігація адміністратора">
        {navigationGroups.map((group, index) => (
          <div key={index} className={cn("space-y-2", index > 0 && "mt-3 border-t border-slate-200 pt-3")}>
            {group.map((item) => <NavigationItem key={item.label} item={item} activeItem={activeItem} />)}
          </div>
        ))}
      </nav>
    </aside>
  );
}
