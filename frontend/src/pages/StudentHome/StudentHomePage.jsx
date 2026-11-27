import { navigate } from "@/lib/navigation.js";
import { Link } from "react-router-dom";
import { RecentGradesCard, HomeworkCard } from "@/components/StudentProgressCards.jsx";
import { useStudentProgress } from "@/hooks/useStudentProgress.js";
import {
  BookOpen,
  CalendarDays,
  GraduationCap,
  Home,
  LogOut,
  Mail,
  User,
} from "lucide-react";
import { AppFooter } from "@/components/AppFooter.jsx";
import { RecentMessagesCard } from "@/components/RecentMessagesCard.jsx";
import { LearnerNavigation } from "@/components/LearnerNavigation.jsx";
import { NotificationButton } from "@/components/NotificationButton.jsx";
import { CurrentUserBadge } from "@/lib/currentUser.jsx";
import { Card, CardContent, CardHeader } from "@/components/ui/card.jsx";
import { authApi } from "@/lib/api.js";
import { cn } from "@/lib/utils.js";
import { formatToday, useTodaySchedule } from "@/hooks/useTodaySchedule.js";

const navigationItems = [
  { label: "Головна", icon: Home, active: true, route: "student" },
  { label: "Розклад", icon: CalendarDays, route: "student/schedule" },
  { label: "Предмети", icon: BookOpen, route: "student/subjects" },
  { label: "Повідомлення", shortLabel: "Повідомл.", icon: Mail, route: "student/messages" },
  { label: "Профіль", icon: User, route: "student/profile" },
];



export function StudentHomePage() {
  const progress = useStudentProgress("STUDENT");
  const todaySchedule = useTodaySchedule("STUDENT");

  return (
    <div className="flex min-h-screen flex-col bg-slate-50 text-slate-800">
      <TopBar />
      <MobileNavigation />

      <div className="flex flex-1">
        <Sidebar />

        <div className="flex min-w-0 flex-1 flex-col lg:border-l lg:border-slate-200/80">
          <main className="min-w-0 flex-1 px-4 py-5 pb-8 sm:px-6 lg:px-8 lg:py-6">
            <div className="mx-auto w-full max-w-[1280px] 2xl:max-w-[1500px] min-[1920px]:max-w-[1680px] min-[2300px]:max-w-[1840px]">
              <header className="mb-5 sm:mb-7">
                <div>
                  <h1 className="text-[1.45rem] font-bold leading-tight text-slate-900 sm:text-[1.65rem]">
                    Головна
                  </h1>
                  <p className="mt-2 text-[0.9rem] text-slate-500">
                    Ваш навчальний прогрес за поточний тиждень
                  </p>
                </div>
              </header>

              <div className="grid gap-5 xl:grid-cols-[minmax(0,0.85fr)_minmax(0,1.35fr)]">
                <TodayScheduleCard schedule={todaySchedule} />
                <RecentGradesCard data={progress} />
              </div>

              <div className="mt-5 grid gap-5 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
                <HomeworkCard data={progress} />
                <MessagesCard />
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
    navigate("login");
  };

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-slate-200/80 bg-white px-4 sm:px-5 lg:px-6">
      <Link
        to="/student"
        className="inline-flex min-w-0 items-center gap-2 text-slate-900 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        aria-label="Електронний щоденник - головна"
      >
        <GraduationCap className="h-7 w-7 shrink-0 text-slate-900 sm:h-8 sm:w-8" strokeWidth={2.4} aria-hidden="true" />
        <span className="max-w-[150px] truncate text-[0.92rem] font-bold min-[420px]:max-w-none sm:text-[1.05rem]">
          Електронний щоденник
        </span>
      </Link>

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
  return <LearnerNavigation role="STUDENT" activeRoute="student" compact />;
}

function Sidebar() {
  return <LearnerNavigation role="STUDENT" activeRoute="student" />;
}

function SectionTitle({ children }) {
  return <h2 className="text-[0.98rem] font-bold leading-tight text-slate-900">{children}</h2>;
}

function TodayScheduleCard({ schedule }) {
  return (
    <Card className="rounded-lg px-4 py-4 shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)] sm:px-5">
      <CardHeader className="mb-3 flex-row items-center justify-between space-y-0">
        <SectionTitle>Сьогоднішній розклад</SectionTitle>
        <span className="text-[0.74rem] font-medium text-slate-400">{formatToday()}</span>
      </CardHeader>

      <CardContent className="space-y-2">
        {schedule.loading ? <ScheduleLoading /> : null}
        {!schedule.loading && schedule.error ? <p className="py-8 text-center text-[0.78rem] text-red-500">{schedule.error}</p> : null}
        {!schedule.loading && !schedule.error && !schedule.lessons.length ? <p className="py-8 text-center text-[0.78rem] text-slate-400">Сьогодні уроків немає</p> : null}
        {!schedule.loading && !schedule.error ? schedule.lessons.map((lesson) => (
          <article
            key={lesson.id}
            className="rounded-md border border-slate-200/80 bg-white px-3.5 py-2 transition-colors duration-200 hover:bg-slate-50"
          >
            <time className="text-[0.72rem] font-medium leading-none text-slate-400">{lesson.startTime}–{lesson.endTime}</time>
            <h3 className="mt-1 text-[0.84rem] font-bold leading-4 text-slate-800">{lesson.subject}</h3>
            <p className="mt-0.5 text-[0.72rem] leading-4 text-slate-500">{lesson.teacher} · {lesson.room ? `Кабінет ${lesson.room}` : "Кабінет не вказано"}</p>
          </article>
        )) : null}
        <Link to="/student/schedule" className="mt-3 inline-flex text-[0.76rem] font-semibold text-slate-600 hover:text-slate-900">Відкрити розклад</Link>
      </CardContent>
    </Card>
  );
}

function ScheduleLoading() {
  return <div className="flex min-h-24 items-center justify-center text-slate-400"><CalendarDays className="h-5 w-5 animate-pulse" aria-label="Завантаження розкладу" /></div>;
}

function MessagesCard() {
  return <RecentMessagesCard role="STUDENT" />;
}
