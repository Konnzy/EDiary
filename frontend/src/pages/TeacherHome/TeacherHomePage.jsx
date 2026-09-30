import { useEffect, useState } from "react";
import {
  BookOpenCheck,
  CalendarDays,
  GraduationCap,
  LogOut,
  Mail,
} from "lucide-react";
import { AppFooter } from "@/components/AppFooter.jsx";
import { NotificationButton } from "@/components/NotificationButton.jsx";
import { CurrentUserBadge } from "@/lib/currentUser.jsx";
import { Button } from "@/components/ui/button.jsx";
import { Card, CardContent, CardHeader } from "@/components/ui/card.jsx";
import { authApi, teacherDashboardApi } from "@/lib/api.js";
import { TeacherNavigation } from "@/components/TeacherNavigation.jsx";
import { formatToday, useTodaySchedule } from "@/hooks/useTodaySchedule.js";

function journalLink(item, date) {
  if (!date) return "#teacher/journal";
  const params = new URLSearchParams({
    month: date.slice(0, 7),
    date,
  });
  if (item.classId) params.set("classId", String(item.classId));
  if (item.subjectId) params.set("subjectId", String(item.subjectId));
  if (item.className) params.set("className", item.className);
  if (item.subjectName || item.subject) params.set("subjectName", item.subjectName || item.subject);
  if (item.lessonNumber) params.set("lessonNumber", String(item.lessonNumber));
  if (item.lessonId) params.set("lessonId", String(item.lessonId));
  if (item.id && !item.lessonId) params.set("scheduleId", String(item.id));
  return `#teacher/journal?${params}`;
}

function todayIsoDate() {
  const today = new Date();
  return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;
}

function formatDate(value) {
  return new Intl.DateTimeFormat("uk-UA", { day: "2-digit", month: "2-digit", year: "numeric" })
    .format(new Date(`${value}T12:00:00`));
}

function formatUpdatedAt(value) {
  return new Intl.DateTimeFormat("uk-UA", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" })
    .format(new Date(value));
}

export function TeacherHomePage() {
  const todaySchedule = useTodaySchedule("TEACHER");
  const [dashboard, setDashboard] = useState({ data: null, loading: true, error: "" });

  useEffect(() => {
    let active = true;
    teacherDashboardApi.find()
      .then((data) => {
        if (!Array.isArray(data?.recentChanges) || !Array.isArray(data?.lessonsWithoutTopic)) {
          throw new Error("Не вдалося завантажити дані журналу.");
        }
        if (active) setDashboard({ data, loading: false, error: "" });
      })
      .catch((error) => { if (active) setDashboard({ data: null, loading: false, error: error.message || "Не вдалося завантажити журнал." }); });
    return () => { active = false; };
  }, []);

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
                <TodayScheduleCard schedule={todaySchedule} />
                <RecentChangesCard dashboard={dashboard} />
                <MessagesCard />
                <LessonsToCompleteCard dashboard={dashboard} />
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
  const handleLogout = async () => {
    try {
      await authApi.logout();
    } catch {
      // The session may already be expired.
    }
    window.location.hash = "login";
  };

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-slate-200/80 bg-white px-4 sm:px-5 lg:px-6">
      <a
        href="#teacher"
        className="inline-flex min-w-0 items-center gap-2 text-slate-900 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        aria-label="Електронний щоденник - головна"
      >
        <GraduationCap className="h-7 w-7 shrink-0 text-slate-900 sm:h-8 sm:w-8" strokeWidth={2.4} aria-hidden="true" />
        <span className="max-w-[150px] truncate text-[0.92rem] font-bold min-[420px]:max-w-none sm:text-[1.05rem]">
          Електронний щоденник
        </span>
      </a>

      <div className="flex items-center gap-1.5 sm:gap-4">
        <NotificationButton />

        <CurrentUserBadge />

        <button
          type="button"
          onClick={handleLogout}
          className="inline-flex h-9 items-center gap-2 rounded-md px-2 text-[0.82rem] font-semibold text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-900 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 sm:h-10 sm:px-3"
        >
          <LogOut className="h-4 w-4" aria-hidden="true" />
          <span className="hidden sm:inline">Вийти</span>
        </button>
      </div>
    </header>
  );
}

function MobileNavigation() {
  return <TeacherNavigation activeRoute="teacher" compact />;
}

function Sidebar() {
  return <TeacherNavigation activeRoute="teacher" />;
}

function SectionTitle({ children }) {
  return <h2 className="text-[0.98rem] font-bold leading-tight text-slate-900">{children}</h2>;
}

function TodayScheduleCard({ schedule }) {
  return (
    <Card className="rounded-lg px-4 py-4 shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)] sm:px-5 xl:col-span-2">
      <CardHeader className="mb-4 flex-row items-center justify-between space-y-0">
        <SectionTitle>Сьогоднішній розклад</SectionTitle>
        <span className="text-[0.74rem] font-medium text-slate-400">{formatToday()}</span>
      </CardHeader>

      <CardContent className="space-y-3">
        {schedule.loading ? <div className="flex min-h-24 items-center justify-center text-slate-400"><CalendarDays className="h-5 w-5 animate-pulse" aria-label="Завантаження розкладу" /></div> : null}
        {!schedule.loading && schedule.error ? <p className="py-8 text-center text-[0.78rem] text-red-500">{schedule.error}</p> : null}
        {!schedule.loading && !schedule.error && !schedule.lessons.length ? <p className="py-8 text-center text-[0.78rem] text-slate-400">Сьогодні уроків немає</p> : null}
        {!schedule.loading && !schedule.error ? schedule.lessons.map((lesson) => (
          <article
            key={lesson.id}
            className="grid gap-3 rounded-md border border-slate-200/80 bg-white px-4 py-3 transition-colors duration-200 hover:bg-slate-50 sm:grid-cols-[90px_minmax(0,1fr)_auto] sm:items-center"
          >
            <div className="border-slate-200/90 sm:border-r sm:pr-4">
              <time className="text-[0.72rem] font-semibold leading-none text-slate-400">{lesson.startTime}–{lesson.endTime}</time>
              <div className="mt-1 text-[0.82rem] font-bold text-slate-900">{lesson.lessonNumber}-й урок</div>
            </div>

            <div className="min-w-0">
              <h3 className="truncate text-[0.95rem] font-bold leading-5 text-slate-900">{lesson.subject}</h3>
              <p className="mt-0.5 text-[0.74rem] leading-4 text-slate-500">{lesson.className} · {lesson.room ? `Кабінет ${lesson.room}` : "Кабінет не вказано"}</p>
            </div>

            <Button
              type="button"
              variant="ghost"
              onClick={() => { window.location.hash = journalLink(lesson, todayIsoDate()); }}
              className="h-9 justify-self-start rounded-md px-3 text-[0.78rem] font-semibold text-slate-500 hover:bg-slate-100 sm:justify-self-end"
            >
              Журнал
            </Button>
          </article>
        )) : null}
        <a href="#teacher/schedule" className="inline-flex text-[0.76rem] font-semibold text-slate-600 hover:text-slate-900">Відкрити розклад</a>
      </CardContent>
    </Card>
  );
}

function RecentChangesCard({ dashboard }) {
  return (
    <Card className="rounded-lg px-4 py-4 shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)] sm:px-5 xl:col-start-3 xl:row-span-2 xl:row-start-1">
      <CardHeader className="mb-4 space-y-0">
        <SectionTitle>Останні зміни в журналі</SectionTitle>
      </CardHeader>

      <CardContent className="space-y-3">
        <p className="text-[0.73rem] text-slate-500">Останні оновлені уроки</p>
        {dashboard.loading ? <p className="py-8 text-center text-sm text-slate-400">Завантаження записів...</p> : null}
        {dashboard.error ? <p role="alert" className="py-8 text-center text-sm text-red-600">{dashboard.error}</p> : null}
        {!dashboard.loading && !dashboard.error && !dashboard.data?.recentChanges?.length ? (
          <p className="py-8 text-center text-sm text-slate-500">Записів у журналі поки немає.</p>
        ) : null}
        {(dashboard.data?.recentChanges || []).map((item) => (
          <a key={item.lessonId} href={journalLink(item, item.lessonDate)}
            className="block rounded-md border border-slate-200 bg-slate-50/70 px-4 py-3 transition-colors hover:border-sky-200 hover:bg-sky-50/60">
            <div className="flex items-center justify-between gap-3">
              <span className="truncate text-[0.76rem] font-semibold text-slate-700">{item.subjectName} · {item.className}</span>
              <span className="shrink-0 text-[0.69rem] text-slate-400">{formatUpdatedAt(item.updatedAt)}</span>
            </div>
            <p className="mt-1.5 line-clamp-2 text-[0.82rem] font-medium text-slate-900">{item.topic || "Оновлено запис уроку"}</p>
            <p className="mt-1 text-[0.72rem] text-slate-500">Урок {formatDate(item.lessonDate)} · {item.lessonNumber}-й урок</p>
          </a>
        ))}
        <a href="#teacher/journal" className="inline-flex pt-1 text-[0.76rem] font-semibold text-slate-600 hover:text-slate-900">Відкрити журнал</a>
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

      <CardContent className="flex min-h-28 flex-col justify-between gap-4">
        <p className="text-[0.8rem] text-slate-500">Повідомлень поки немає.</p>
        <a href="#teacher/messages" className="inline-flex w-fit items-center gap-2 text-[0.8rem] font-semibold text-slate-800 hover:underline"><Mail className="h-4 w-4" aria-hidden="true" />Перейти до повідомлень</a>
      </CardContent>
    </Card>
  );
}

function LessonsToCompleteCard({ dashboard }) {
  return (
    <Card className="rounded-lg px-4 py-4 shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)] sm:px-5">
      <CardHeader className="mb-3 flex-row items-center justify-between space-y-0">
        <SectionTitle>Потрібно заповнити</SectionTitle>
        {dashboard.data?.lessonsWithoutTopicCount ? <span className="rounded-full bg-amber-50 px-2.5 py-1 text-xs font-bold text-amber-700">{dashboard.data.lessonsWithoutTopicCount}</span> : null}
      </CardHeader>
      <CardContent className="space-y-2.5">
        <p className="text-[0.73rem] text-slate-500">Уроки без теми за останні 14 днів</p>
        {dashboard.loading ? <p className="py-4 text-sm text-slate-400">Завантаження...</p> : null}
        {dashboard.error ? <p role="alert" className="py-4 text-sm text-red-600">{dashboard.error}</p> : null}
        {!dashboard.loading && !dashboard.error && !dashboard.data?.lessonsWithoutTopicCount ? (
          <div className="flex items-center gap-2 py-3 text-[0.8rem] text-emerald-700"><BookOpenCheck className="h-4 w-4" />Уроків без теми немає</div>
        ) : null}
        {(dashboard.data?.lessonsWithoutTopic || []).map((item) => (
          <a key={`${item.classId}-${item.subjectId}-${item.date}-${item.lessonNumber}`} href={journalLink(item, item.date)} className="flex items-center justify-between gap-3 rounded-md border border-slate-200 px-3 py-2.5 transition-colors hover:border-amber-200 hover:bg-amber-50/50">
            <div className="min-w-0">
              <h3 className="truncate text-[0.8rem] font-semibold text-slate-900">{item.subjectName} · {item.className}</h3>
              <p className="mt-0.5 text-[0.72rem] text-slate-500">{formatDate(item.date)} · {item.lessonNumber}-й урок</p>
            </div>
            <span className="shrink-0 text-[0.72rem] font-semibold text-sky-700">Журнал</span>
          </a>
        ))}
        {dashboard.data?.lessonsWithoutTopicCount > (dashboard.data?.lessonsWithoutTopic || []).length ? (
          <p className="text-[0.71rem] text-slate-500">Ще {dashboard.data.lessonsWithoutTopicCount - dashboard.data.lessonsWithoutTopic.length} уроків у журналі</p>
        ) : null}
      </CardContent>
    </Card>
  );
}
