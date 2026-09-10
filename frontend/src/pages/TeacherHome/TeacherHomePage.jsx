import {
  BookOpenCheck,
  CalendarDays,
  GraduationCap,
  Home,
  LogOut,
  Mail,
  NotebookText,
  User,
  UserRound,
  Users,
} from "lucide-react";
import { AppFooter } from "@/components/AppFooter.jsx";
import { NotificationButton } from "@/components/NotificationButton.jsx";
import { Button } from "@/components/ui/button.jsx";
import { Card, CardContent, CardHeader } from "@/components/ui/card.jsx";
import { cn } from "@/lib/utils.js";

const teacher = {
  name: "Олександр Коваленко",
  role: "Вчитель",
};

const navigationItems = [
  { label: "Головна", icon: Home, active: true },
  { label: "Розклад", icon: CalendarDays },
  { label: "Журнал", icon: NotebookText },
  { label: "Учні", icon: Users },
  { label: "Повідомлення", shortLabel: "Повідомл.", icon: Mail },
  { label: "Профіль", icon: User },
];

const lessons = [
  {
    time: "08:30",
    number: "1 урок",
    subject: "Алгебра",
    details: "10-А клас • Кабінет 302",
  },
  {
    time: "09:25",
    number: "2 урок",
    subject: "Геометрія",
    details: "9-Б клас • Кабінет 302",
  },
  {
    time: "10:30",
    number: "3 урок",
    subject: "Алгебра",
    details: "11-В клас • Кабінет 302",
  },
];

const homework = [
  {
    group: "Алгебра • 10-А",
    title: "Параграф 14, впр. 245-250",
    status: "Активне",
    due: "До 24.05",
    submitted: "18/24 здано",
  },
  {
    group: "Геометрія • 9-Б",
    title: "Контрольна робота №4",
    status: "Перевірено",
    due: "До 23.05",
    submitted: "22/22 здано",
  },
  {
    group: "Геометрія • 9-Б",
    title: "Контрольна робота №4",
    status: "Перевірено",
    due: "До 23.05",
    submitted: "22/22 здано",
  },
  {
    group: "Геометрія • 9-Б",
    title: "Контрольна робота №4",
    status: "Перевірено",
    due: "До 23.05",
    submitted: "22/22 здано",
  },
];

const messages = [
  {
    author: "Марія Іванова (Мати)",
    text: "Добрий день, чи можна передати контрольну роботу",
  },
  {
    author: "Адміністратор",
    text: "Нагадування про педраду о 15:00",
  },
];

const hasUnreadNotifications = false;

const classes = [
  {
    title: "10-А клас",
    details: "24 учні • Математика",
  },
  {
    title: "9-Б клас",
    details: "22 учні • Математика",
  },
  {
    title: "11-В клас",
    details: "20 учнів • Математика",
  },
];

export function TeacherHomePage() {
  return (
    <div className="flex min-h-screen flex-col bg-slate-50 text-slate-800">
      <TopBar />
      <MobileNavigation />

      <div className="flex flex-1">
        <Sidebar />

        <div className="flex min-w-0 flex-1 flex-col lg:border-l lg:border-slate-200/80">
          <main className="min-w-0 flex-1 px-4 py-5 pb-0 sm:px-6 lg:px-8 lg:py-6 lg:pb-0">
            <div className="mx-auto w-full max-w-[1280px] 2xl:max-w-[1500px] min-[1920px]:max-w-[1680px] min-[2300px]:max-w-[1840px]">
              <header className="mb-5 sm:mb-7">
                <h1 className="text-[1.45rem] font-bold leading-tight text-slate-900 sm:text-[1.65rem]">
                  Головна
                </h1>
                <p className="mt-2 text-[0.9rem] text-slate-500">
                  Огляд вашої навчальної діяльності
                </p>
              </header>

              <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(320px,0.9fr)]">
                <TodayScheduleCard />
                <HomeworkCard />
                <MessagesCard />
                <ClassesCard />
              </div>
            </div>
          </main>
          <AppFooter />
        </div>
      </div>
    </div>
  );
}

function TopBar() {
  return (
    <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-slate-200/80 bg-white px-4 sm:px-5 lg:px-6">
      <a
        href="#teacher"
        className="inline-flex min-w-0 items-center gap-2 text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        aria-label="Електронний щоденник - головна"
      >
        <GraduationCap className="h-7 w-7 shrink-0 text-slate-900 sm:h-8 sm:w-8" strokeWidth={2.4} aria-hidden="true" />
        <span className="max-w-[150px] truncate text-[0.92rem] font-bold min-[420px]:max-w-none sm:text-[1.05rem]">
          Електронний щоденник
        </span>
      </a>

      <div className="flex items-center gap-1.5 sm:gap-4">
        <NotificationButton hasUnread={hasUnreadNotifications} />

        <div className="hidden items-center gap-3 rounded-sm bg-slate-50 px-3 py-1.5 sm:flex">
          <div className="text-right leading-tight">
            <div className="text-[0.78rem] font-bold text-slate-800">{teacher.name}</div>
            <div className="text-[0.7rem] text-slate-500">{teacher.role}</div>
          </div>
          <UserRound className="h-8 w-8 text-slate-900" strokeWidth={1.8} aria-hidden="true" />
        </div>

        <button
          type="button"
          onClick={() => {
            window.location.hash = "login";
          }}
          className="inline-flex h-9 items-center gap-2 rounded-md px-2 text-[0.82rem] font-semibold text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 sm:h-10 sm:px-3"
        >
          <LogOut className="h-4 w-4" aria-hidden="true" />
          <span className="hidden sm:inline">Вийти</span>
        </button>
      </div>
    </header>
  );
}

function MobileNavigation() {
  return (
    <nav
      className="sticky top-16 z-20 border-b border-slate-200/80 bg-white px-3 py-2 lg:hidden"
      aria-label="Основна навігація"
    >
      <div className="grid grid-cols-6 gap-1">
        {navigationItems.map((item) => {
          const Icon = item.icon;

          return (
            <button
              key={item.label}
              type="button"
              className={cn(
                "inline-flex h-10 min-w-0 items-center justify-center gap-1 rounded-md px-1 text-[0.68rem] font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 min-[560px]:gap-1.5 min-[560px]:text-[0.76rem]",
                item.active
                  ? "bg-slate-100 text-slate-900"
                  : "text-slate-500 hover:bg-slate-50 hover:text-slate-800",
              )}
              aria-label={item.label}
              aria-current={item.active ? "page" : undefined}
            >
              <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
              <span className="hidden truncate min-[560px]:inline">{item.shortLabel ?? item.label}</span>
            </button>
          );
        })}
      </div>
    </nav>
  );
}

function Sidebar() {
  return (
    <aside className="hidden w-[210px] shrink-0 bg-white px-3 py-4 lg:block">
      <nav className="space-y-2" aria-label="Основна навігація">
        {navigationItems.map((item) => {
          const Icon = item.icon;

          return (
            <button
              key={item.label}
              type="button"
              className={cn(
                "flex h-11 w-full items-center gap-3 rounded-md px-3 text-left text-[0.9rem] font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2",
                item.active
                  ? "bg-slate-100 text-slate-900"
                  : "text-slate-500 hover:bg-slate-50 hover:text-slate-800",
              )}
              aria-current={item.active ? "page" : undefined}
            >
              <Icon className="h-5 w-5 shrink-0" aria-hidden="true" />
              {item.label}
            </button>
          );
        })}
      </nav>
    </aside>
  );
}

function SectionTitle({ children }) {
  return <h2 className="text-[0.98rem] font-bold leading-tight text-slate-900">{children}</h2>;
}

function TodayScheduleCard() {
  return (
    <Card className="rounded-lg px-4 py-4 shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)] sm:px-5 xl:col-span-2">
      <CardHeader className="mb-4 flex-row items-center justify-between space-y-0">
        <SectionTitle>Сьогоднішній розклад</SectionTitle>
        <span className="text-[0.74rem] font-medium text-slate-400">Понеділок, 22 Травня</span>
      </CardHeader>

      <CardContent className="space-y-3">
        {lessons.map((lesson) => (
          <article
            key={`${lesson.time}-${lesson.subject}`}
            className="grid gap-3 rounded-md border border-slate-200/80 bg-slate-50/75 px-4 py-3 sm:grid-cols-[90px_minmax(0,1fr)_auto] sm:items-center"
          >
            <div className="border-slate-200/90 sm:border-r sm:pr-4">
              <time className="text-[0.72rem] font-semibold leading-none text-slate-400">{lesson.time}</time>
              <div className="mt-1 text-[0.82rem] font-bold text-slate-900">{lesson.number}</div>
            </div>

            <div className="min-w-0">
              <h3 className="truncate text-[0.95rem] font-bold leading-5 text-slate-900">{lesson.subject}</h3>
              <p className="mt-0.5 text-[0.74rem] leading-4 text-slate-500">{lesson.details}</p>
            </div>

            <Button
              type="button"
              variant="ghost"
              className="h-9 justify-self-start rounded-md px-3 text-[0.78rem] font-semibold text-slate-500 hover:bg-slate-100 sm:justify-self-end"
            >
              Журнал
            </Button>
          </article>
        ))}
      </CardContent>
    </Card>
  );
}

function HomeworkCard() {
  return (
    <Card className="rounded-lg px-4 py-4 shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)] sm:px-5 xl:col-start-3 xl:row-span-2 xl:row-start-1">
      <CardHeader className="mb-4 space-y-0">
        <SectionTitle>Домашні завдання</SectionTitle>
      </CardHeader>

      <CardContent className="space-y-4">
        {homework.map((item) => (
          <article
            key={`${item.group}-${item.submitted}`}
            className="rounded-md border border-slate-200/80 bg-slate-50/75 px-4 py-4"
          >
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <div className="text-[0.74rem] font-bold text-slate-500">{item.group}</div>
                <h3 className="mt-2 text-[0.86rem] font-bold leading-5 text-slate-800">{item.title}</h3>
              </div>
              <span className="shrink-0 text-[0.7rem] font-semibold text-slate-400">{item.due}</span>
            </div>

            <div className="mt-3 flex items-center justify-between gap-3">
              <span className="rounded bg-slate-800 px-2 py-1 text-[0.66rem] font-bold text-white">
                {item.status}
              </span>
              <span className="text-[0.72rem] font-semibold text-slate-400">{item.submitted}</span>
            </div>
          </article>
        ))}
      </CardContent>
    </Card>
  );
}

function MessagesCard() {
  return (
    <Card className="rounded-lg px-4 py-4 shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)] sm:px-5">
      <CardHeader className="mb-4 space-y-0">
        <SectionTitle>Останні повідомлення</SectionTitle>
      </CardHeader>

      <CardContent className="space-y-4">
        {messages.map((message) => (
          <article key={message.author} className="flex gap-3">
            <UserRound className="mt-0.5 h-8 w-8 shrink-0 text-slate-800" strokeWidth={1.7} aria-hidden="true" />
            <div className="min-w-0">
              <h3 className="text-[0.84rem] font-bold text-slate-800">{message.author}</h3>
              <p className="mt-0.5 text-[0.76rem] leading-5 text-slate-500">{message.text}</p>
            </div>
          </article>
        ))}
      </CardContent>
    </Card>
  );
}

function ClassesCard() {
  return (
    <Card className="rounded-lg px-4 py-4 shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)] sm:px-5">
      <CardHeader className="mb-4 space-y-0">
        <SectionTitle>Мої класи</SectionTitle>
      </CardHeader>

      <CardContent className="space-y-4">
        {classes.map((item) => (
          <article key={item.title} className="flex items-center justify-between gap-4">
            <div className="min-w-0">
              <h3 className="truncate text-[0.95rem] font-bold text-slate-900">{item.title}</h3>
              <p className="mt-0.5 text-[0.74rem] text-slate-500">{item.details}</p>
            </div>
            <Button
              type="button"
              variant="ghost"
              className="h-8 shrink-0 rounded-md bg-slate-100 px-3 text-[0.72rem] font-bold text-slate-600 hover:bg-slate-200"
            >
              Відкрити
            </Button>
          </article>
        ))}
      </CardContent>
    </Card>
  );
}
