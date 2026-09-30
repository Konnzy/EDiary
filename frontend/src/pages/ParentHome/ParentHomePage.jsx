import { RecentGradesCard, HomeworkCard } from "@/components/StudentProgressCards.jsx";
import { useStudentProgress } from "@/hooks/useStudentProgress.js";
import { useEffect, useState } from "react";
import {
  BookOpen,
  CalendarDays,
  ChevronDown,
  GraduationCap,
  Home,
  Loader2,
  LogOut,
  Mail,
  User,
  UserRound,
} from "lucide-react";
import { AppFooter } from "@/components/AppFooter.jsx";
import { LearnerNavigation } from "@/components/LearnerNavigation.jsx";
import { NotificationButton } from "@/components/NotificationButton.jsx";
import { CurrentUserBadge } from "@/lib/currentUser.jsx";
import { Alert } from "@/components/ui/alert.jsx";
import { Card, CardContent, CardHeader } from "@/components/ui/card.jsx";
import { ApiError, authApi, parentApi } from "@/lib/api.js";
import { childOptionLabel } from "@/lib/childOption.js";
import { formatToday, useTodaySchedule } from "@/hooks/useTodaySchedule.js";

const navigationItems = [
  { label: "Головна", icon: Home, active: true, route: "parent" },
  { label: "Розклад", icon: CalendarDays, route: "parent/schedule" },
  { label: "Предмети", icon: BookOpen, route: "parent/subjects" },
  { label: "Повідомлення", shortLabel: "Повідомл.", icon: Mail, route: "parent/messages" },
  { label: "Профіль", icon: User, route: "parent/profile" },
];



export function ParentHomePage() {
  const [children, setChildren] = useState([]);
  const [selectedStudentId, setSelectedStudentId] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const progress = useStudentProgress("PARENT", selectedStudentId);
  const todaySchedule = useTodaySchedule("PARENT", selectedStudentId);

  useEffect(() => {
    let active = true;
    parentApi.findChildren()
      .then((items) => {
        if (!active) return;
        setChildren(items);
        setSelectedStudentId(items[0] ? String(items[0].studentId) : "");
      })
      .catch((requestError) => {
        if (active) {
          setError(requestError instanceof ApiError ? requestError.message : "Не вдалося завантажити дані дітей.");
        }
      })
      .finally(() => active && setIsLoading(false));
    return () => { active = false; };
  }, []);

  const selectedChild = children.find((child) => String(child.studentId) === selectedStudentId) || null;

  const handleChildSelection = (value) => {
    if (value === "__add_child__") {
      window.location.hash = "parent/profile?addChild=1";
      return;
    }
    setSelectedStudentId(value);
  };

  return (
    <div className="flex min-h-screen flex-col bg-slate-50 text-slate-800">
      <TopBar />
      <MobileNavigation />
      <div className="flex flex-1">
        <Sidebar />
        <div className="flex min-w-0 flex-1 flex-col lg:border-l lg:border-slate-200/80">
          <main className="min-w-0 flex-1 px-4 py-5 pb-8 sm:px-6 lg:px-8 lg:py-6">
            <div className="mx-auto w-full max-w-[1280px] 2xl:max-w-[1500px] min-[1920px]:max-w-[1680px] min-[2300px]:max-w-[1840px]">
              <header className="mb-5 sm:mb-6">
                <h1 className="text-[1.45rem] font-bold leading-tight text-slate-900 sm:text-[1.65rem]">Головна</h1>
                <p className="mt-2 text-[0.9rem] text-slate-500">Навчальна інформація вашої дитини.</p>
              </header>

              {error ? <Alert>{error}</Alert> : null}
              {isLoading ? <LoadingState /> : null}
              {!isLoading && !error ? (
                <>
                  <ChildSelector children={children} value={selectedStudentId} onChange={handleChildSelection} />
                  {!selectedChild ? <div className="mt-5"><EmptyChildren /></div> : null}
                  {selectedChild ? (
                    <>
                      <div className="mt-5 grid gap-5 xl:grid-cols-[minmax(0,0.85fr)_minmax(0,1.35fr)]">
                        <TodayScheduleCard schedule={todaySchedule} />
                        <RecentGradesCard data={progress} />
                      </div>
                      <div className="mt-5 grid gap-5 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
                        <HomeworkCard data={progress} />
                        <MessagesCard />
                      </div>
                    </>
                  ) : null}
                </>
              ) : null}
            </div>
          </main>
          <AppFooter />
        </div>
      </div>
    </div>
  );
}

function ChildSelector({ children, value, onChange }) {
  return (
    <section className="flex w-full max-w-[480px] flex-col gap-3 rounded-lg border border-slate-200 bg-white px-4 py-3 shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)] sm:flex-row sm:items-center sm:px-5" aria-label="Вибір дитини">
      <label htmlFor="parent-child-selector" className="shrink-0 text-[0.8rem] font-bold text-slate-700">Перегляд даних:</label>
      <div className="relative w-full sm:max-w-[310px]">
        <select id="parent-child-selector" value={value} onChange={(event) => onChange(event.target.value)} className="h-10 w-full appearance-none rounded-md border border-slate-300 bg-white px-3 pr-10 text-[0.82rem] font-semibold text-slate-700 outline-hidden transition-colors hover:border-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200">
          {!value ? <option value="" disabled>Оберіть дитину</option> : null}
          {children.map((child) => (
            <option key={child.studentId} value={child.studentId}>{childOptionLabel(child)}</option>
          ))}
          <option value="__add_child__">+ Додати дитину</option>
        </select>
        <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
      </div>
    </section>
  );
}

function SectionTitle({ children }) {
  return <h2 className="text-[0.98rem] font-bold leading-tight text-slate-900">{children}</h2>;
}

function TodayScheduleCard({ schedule }) {
  return (
    <Card className="rounded-lg px-4 py-4 shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)] sm:px-5">
      <CardHeader className="mb-3 flex-row items-center justify-between space-y-0"><SectionTitle>Сьогоднішній розклад</SectionTitle><span className="text-[0.74rem] font-medium text-slate-400">{formatToday()}</span></CardHeader>
      <CardContent className="space-y-2">
        {schedule.loading ? <div className="flex min-h-24 items-center justify-center text-slate-400"><CalendarDays className="h-5 w-5 animate-pulse" aria-label="Завантаження розкладу" /></div> : null}
        {!schedule.loading && schedule.error ? <p className="py-8 text-center text-[0.78rem] text-red-500">{schedule.error}</p> : null}
        {!schedule.loading && !schedule.error && !schedule.lessons.length ? <p className="py-8 text-center text-[0.78rem] text-slate-400">Сьогодні уроків немає</p> : null}
        {!schedule.loading && !schedule.error ? schedule.lessons.map((lesson) => (
          <article key={lesson.id} className="rounded-md border border-slate-200/80 bg-white px-3.5 py-2 transition-colors duration-200 hover:bg-slate-50"><time className="text-[0.72rem] font-medium leading-none text-slate-400">{lesson.startTime}–{lesson.endTime}</time><h3 className="mt-1 text-[0.84rem] font-bold leading-4 text-slate-800">{lesson.subject}</h3><p className="mt-0.5 text-[0.72rem] leading-4 text-slate-500">{lesson.teacher} · {lesson.room ? `Кабінет ${lesson.room}` : "Кабінет не вказано"}</p></article>
        )) : null}
        <a href="#parent/schedule" className="mt-3 inline-flex text-[0.76rem] font-semibold text-slate-600 hover:text-slate-900">Відкрити розклад</a>
      </CardContent>
    </Card>
  );
}

function MessagesCard() {
  return (
    <Card className="rounded-lg px-4 py-4 shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)] sm:px-5 sm:py-5">
      <CardHeader className="mb-4 space-y-0"><SectionTitle>Останні повідомлення</SectionTitle></CardHeader>
      <CardContent className="flex min-h-28 flex-col justify-between gap-4"><p className="text-[0.8rem] text-slate-500">Повідомлень поки немає.</p><a href="#parent/messages" className="inline-flex w-fit items-center gap-2 text-[0.8rem] font-semibold text-slate-800 hover:underline"><Mail className="h-4 w-4" aria-hidden="true" />Перейти до повідомлень</a></CardContent>
    </Card>
  );
}

function LoadingState() {
  return <div className="flex min-h-72 items-center justify-center text-slate-400"><Loader2 className="h-6 w-6 animate-spin" aria-label="Завантаження" /></div>;
}

function EmptyChildren() {
  return <Card className="rounded-lg px-5 py-12 text-center"><UserRound className="mx-auto h-8 w-8 text-slate-400" aria-hidden="true" /><h2 className="mt-4 text-[0.98rem] font-bold text-slate-900">Немає прив’язаних дітей</h2><p className="mt-2 text-[0.8rem] text-slate-500">Додайте дитину за кодом прив’язки у профілі.</p><a href="#parent/profile?addChild=1" className="mt-4 inline-flex text-[0.8rem] font-semibold text-slate-800 hover:underline">Додати дитину</a></Card>;
}

function TopBar() {
  const handleLogout = async () => {
    try { await authApi.logout(); } catch { /* The session may already be expired. */ }
    window.location.hash = "login";
  };
  return (
    <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-slate-200/80 bg-white px-4 sm:px-5 lg:px-6">
      <a href="#parent" className="inline-flex min-w-0 items-center gap-2 text-slate-900" aria-label="Електронний щоденник - головна"><GraduationCap className="h-7 w-7 shrink-0 sm:h-8 sm:w-8" strokeWidth={2.4} aria-hidden="true" /><span className="max-w-[150px] truncate text-[0.92rem] font-bold min-[420px]:max-w-none sm:text-[1.05rem]">Електронний щоденник</span></a>
      <div className="flex items-center gap-1.5 sm:gap-4"><NotificationButton /><CurrentUserBadge /><button type="button" onClick={handleLogout} className="inline-flex h-10 items-center gap-2 rounded-md px-3 text-[0.82rem] font-semibold text-slate-500 hover:bg-slate-100 hover:text-slate-900"><LogOut className="h-4 w-4" aria-hidden="true" /><span className="hidden sm:inline">Вийти</span></button></div>
    </header>
  );
}

function MobileNavigation() {
  return <LearnerNavigation role="PARENT" activeRoute="parent" compact />;
}

function Sidebar() {
  return <LearnerNavigation role="PARENT" activeRoute="parent" />;
}
