import { useEffect, useState } from "react";
import {
  BookOpen,
  CalendarDays,
  CheckSquare,
  ChevronDown,
  GraduationCap,
  Home,
  Loader2,
  LogOut,
  Mail,
  Plus,
  User,
  UserRound,
  X,
} from "lucide-react";
import { AppFooter } from "@/components/AppFooter.jsx";
import { NotificationButton } from "@/components/NotificationButton.jsx";
import { Alert } from "@/components/ui/alert.jsx";
import { Card, CardContent, CardHeader } from "@/components/ui/card.jsx";
import { ApiError, authApi, parentApi } from "@/lib/api.js";
import { userIdentities } from "@/lib/userIdentity.js";
import { cn } from "@/lib/utils.js";
import { formatToday, useTodaySchedule } from "@/hooks/useTodaySchedule.js";

const navigationItems = [
  { label: "Головна", icon: Home, active: true, route: "parent" },
  { label: "Розклад", icon: CalendarDays, route: "parent/schedule" },
  { label: "Предмети", icon: BookOpen, route: "parent/subjects" },
  { label: "Повідомлення", shortLabel: "Повідомл.", icon: Mail },
  { label: "Профіль", icon: User, route: "parent/profile" },
];

const grades = [
  { subject: "Математика", type: "Контрольна робота", grade: "7", date: "15.10.2026", comment: "—" },
  { subject: "Англійська мова", type: "Самостійна робота", grade: "11", date: "14.10.2026", comment: "—" },
  { subject: "Біологія", type: "Самостійна робота", grade: "9", date: "13.10.2026", comment: "—" },
  { subject: "Географія", type: "Самостійна робота", grade: "8", date: "12.10.2026", comment: "—" },
  { subject: "Інформатика", type: "Практична робота", grade: "9", date: "11.10.2026", comment: "—" },
  { subject: "Географія", type: "Домашня робота", grade: "8", date: "11.10.2026", comment: "—" },
  { subject: "Хімія", type: "Лабораторна робота", grade: "6", date: "11.10.2026", comment: "—" },
];

const homework = [
  { title: "Українська мова - Впр. 142", status: "Виконано", completed: true },
  { title: "Алгебра - Параграф 5, №12-15", status: "До 20.10" },
  { title: "Хімія - Лабораторна робота №2", status: "До 21.10" },
];

const messages = [
  { author: "Олена Миколаївна", text: "Підготуватися до контрольної роботи з української мови у четвер.", time: "10:45" },
  { author: "Адміністрація", text: "Завтра відбудуться загальношкільні збори в актовій залі о 15:00.", time: "Вчора" },
];

export function ParentHomePage() {
  const [children, setChildren] = useState([]);
  const [selectedStudentId, setSelectedStudentId] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const [isLinkModalOpen, setIsLinkModalOpen] = useState(false);
  const [linkCode, setLinkCode] = useState("");
  const [linkError, setLinkError] = useState("");
  const [isLinking, setIsLinking] = useState(false);
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
      setLinkError("");
      setLinkCode("");
      setIsLinkModalOpen(true);
      return;
    }
    setSelectedStudentId(value);
  };

  const closeLinkModal = () => {
    if (isLinking) return;
    setIsLinkModalOpen(false);
    setLinkCode("");
    setLinkError("");
  };

  const handleLinkChild = async (event) => {
    event.preventDefault();
    if (!/^[A-Z0-9]{4}-[A-Z0-9]{4}$/.test(linkCode)) {
      setLinkError("Введіть код у форматі XXXX-XXXX");
      return;
    }

    setIsLinking(true);
    setLinkError("");
    try {
      const linkedChild = await parentApi.linkChild(linkCode);
      setChildren((current) => [...current, linkedChild].sort(compareChildren));
      setSelectedStudentId(String(linkedChild.studentId));
      setIsLinkModalOpen(false);
      setLinkCode("");
    } catch (requestError) {
      setLinkError(requestError instanceof ApiError ? requestError.message : "Не вдалося додати дитину.");
    } finally {
      setIsLinking(false);
    }
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
                        <RecentGradesCard />
                      </div>
                      <div className="mt-5 grid gap-5 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
                        <HomeworkCard />
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
      {isLinkModalOpen ? (
        <LinkChildModal
          code={linkCode}
          error={linkError}
          isSubmitting={isLinking}
          onChange={(value) => {
            setLinkCode(formatLinkCode(value));
            setLinkError("");
          }}
          onClose={closeLinkModal}
          onSubmit={handleLinkChild}
        />
      ) : null}
    </div>
  );
}

function compareChildren(first, second) {
  return `${first.lastName} ${first.firstName}`.localeCompare(
    `${second.lastName} ${second.firstName}`,
    "uk",
  );
}

function formatLinkCode(value) {
  const characters = value.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 8);
  return characters.length > 4
    ? `${characters.slice(0, 4)}-${characters.slice(4)}`
    : characters;
}

function ChildSelector({ children, value, onChange }) {
  return (
    <section className="flex flex-col gap-3 rounded-lg border border-slate-200 bg-white px-4 py-3 shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)] sm:flex-row sm:items-center sm:px-5" aria-label="Вибір дитини">
      <label htmlFor="parent-child-selector" className="shrink-0 text-[0.8rem] font-bold text-slate-700">Перегляд даних:</label>
      <div className="relative w-full sm:max-w-[310px]">
        <select id="parent-child-selector" value={value} onChange={(event) => onChange(event.target.value)} className="h-10 w-full appearance-none rounded-md border border-slate-300 bg-white px-3 pr-10 text-[0.82rem] font-semibold text-slate-700 outline-none transition-colors hover:border-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200">
          {!value ? <option value="" disabled>Оберіть дитину</option> : null}
          {children.map((child) => (
            <option key={child.studentId} value={child.studentId}>{child.firstName} {child.lastName}{child.className ? ` · ${child.className}` : ""}</option>
          ))}
          <option value="__add_child__">+ Додати дитину</option>
        </select>
        <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
      </div>
    </section>
  );
}

function LinkChildModal({ code, error, isSubmitting, onChange, onClose, onSubmit }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 px-4 py-8" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section role="dialog" aria-modal="true" aria-labelledby="link-child-title" className="w-full max-w-[420px] rounded-lg border border-slate-200 bg-white p-5 shadow-2xl sm:p-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 id="link-child-title" className="text-[1.05rem] font-bold text-slate-900">Додати дитину</h2>
            <p className="mt-1.5 text-[0.78rem] leading-5 text-slate-500">Введіть код прив’язки, отриманий разом із реєстраційними даними дитини.</p>
          </div>
          <button type="button" onClick={onClose} disabled={isSubmitting} className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-50" aria-label="Закрити"><X className="h-4 w-4" aria-hidden="true" /></button>
        </div>

        <form className="mt-5" onSubmit={onSubmit}>
          <label htmlFor="parent-link-code" className="text-[0.78rem] font-semibold text-slate-700">Код прив’язки до дитини</label>
          <input
            id="parent-link-code"
            value={code}
            onChange={(event) => onChange(event.target.value)}
            placeholder="XXXX-XXXX"
            maxLength={9}
            autoComplete="off"
            autoFocus
            aria-invalid={Boolean(error)}
            aria-describedby={error ? "parent-link-code-error" : undefined}
            className={cn("mt-2 h-11 w-full rounded-md border bg-white px-3 text-[0.9rem] font-semibold uppercase tracking-[0.08em] text-slate-800 outline-none transition-colors focus:ring-2", error ? "border-red-300 focus:border-red-400 focus:ring-red-100" : "border-slate-300 focus:border-slate-500 focus:ring-slate-200")}
          />
          {error ? <p id="parent-link-code-error" className="mt-2 text-[0.76rem] text-red-600">{error}</p> : null}

          <div className="mt-6 flex justify-end gap-3">
            <button type="button" onClick={onClose} disabled={isSubmitting} className="h-10 rounded-md border border-slate-300 bg-white px-4 text-[0.8rem] font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50">Скасувати</button>
            <button type="submit" disabled={isSubmitting || code.length !== 9} className="inline-flex h-10 items-center justify-center gap-2 rounded-md bg-slate-800 px-4 text-[0.8rem] font-semibold text-white hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-60">
              {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <Plus className="h-4 w-4" aria-hidden="true" />}
              Додати
            </button>
          </div>
        </form>
      </section>
    </div>
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

function RecentGradesCard() {
  return (
    <Card className="rounded-lg px-4 py-4 shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)] sm:px-5 sm:py-5">
      <CardHeader className="mb-4 space-y-0"><SectionTitle>Останні оцінки</SectionTitle></CardHeader>
      <CardContent>
        <div className="hidden overflow-x-auto sm:block">
          <table className="w-full min-w-[540px] border-collapse text-left">
            <thead><tr className="text-[0.72rem] font-bold text-slate-400"><th className="pb-4 pr-4">Предмет</th><th className="pb-4 pr-4">Тип роботи</th><th className="pb-4 pr-4 text-center">Оцінка</th><th className="pb-4 pr-4">Дата</th><th className="pb-4">Коментар</th></tr></thead>
            <tbody>{grades.map((item) => <tr key={`${item.subject}-${item.type}-${item.date}`} className="text-[0.82rem] text-slate-600"><td className="py-2.5 pr-4 font-semibold text-slate-700">{item.subject}</td><td className="py-2.5 pr-4">{item.type}</td><td className="py-2.5 pr-4 text-center font-bold text-slate-900">{item.grade}</td><td className="py-2.5 pr-4 font-medium">{item.date}</td><td className="py-2.5 text-slate-400">{item.comment}</td></tr>)}</tbody>
          </table>
        </div>
        <div className="space-y-3 sm:hidden">
          {grades.map((item) => <article key={`${item.subject}-${item.type}-${item.date}-mobile`} className="rounded-md border border-slate-200/80 bg-slate-50/70 px-3 py-3"><div className="flex items-start justify-between gap-3"><div className="min-w-0"><h3 className="truncate text-[0.86rem] font-bold text-slate-800">{item.subject}</h3><p className="mt-1 text-[0.76rem] text-slate-500">{item.type}</p></div><div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-slate-900 text-[0.94rem] font-bold text-white">{item.grade}</div></div><div className="mt-3 flex items-center justify-between gap-3 text-[0.72rem] font-medium text-slate-400"><span>{item.date}</span><span>Коментар: {item.comment}</span></div></article>)}
        </div>
      </CardContent>
    </Card>
  );
}

function HomeworkCard() {
  return (
    <Card className="rounded-lg px-4 py-4 shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)] sm:px-5 sm:py-5">
      <CardHeader className="mb-4 space-y-0"><SectionTitle>Домашні завдання</SectionTitle></CardHeader>
      <CardContent className="space-y-3">{homework.map((item) => <article key={item.title} className="flex min-h-11 items-start justify-between gap-3 rounded-md border border-slate-200/80 bg-white px-3 py-2.5 sm:items-center"><div className="flex min-w-0 items-center gap-3"><CheckSquare className="h-5 w-5 shrink-0 text-slate-800" aria-hidden="true" /><p className={cn("text-[0.84rem] font-medium leading-5 sm:truncate", item.completed ? "text-slate-400 line-through" : "text-slate-700")}>{item.title}</p></div><span className={cn("shrink-0 rounded px-2 py-1 text-[0.66rem] font-bold", item.completed ? "bg-slate-100 text-slate-400" : "bg-slate-800 text-white")}>{item.status}</span></article>)}</CardContent>
    </Card>
  );
}

function MessagesCard() {
  return (
    <Card className="rounded-lg px-4 py-4 shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)] sm:px-5 sm:py-5">
      <CardHeader className="mb-4 space-y-0"><SectionTitle>Останні повідомлення</SectionTitle></CardHeader>
      <CardContent className="space-y-4">{messages.map((message) => <article key={`${message.author}-${message.time}`} className="flex gap-3"><UserRound className="mt-0.5 h-8 w-8 shrink-0 text-slate-800" strokeWidth={1.7} aria-hidden="true" /><div className="min-w-0 flex-1"><div className="flex items-start justify-between gap-3"><h3 className="text-[0.84rem] font-bold text-slate-800">{message.author}</h3><time className="shrink-0 text-[0.66rem] font-medium text-slate-400">{message.time}</time></div><p className="mt-0.5 text-[0.78rem] leading-5 text-slate-500">{message.text}</p></div></article>)}</CardContent>
    </Card>
  );
}

function LoadingState() {
  return <div className="flex min-h-72 items-center justify-center text-slate-400"><Loader2 className="h-6 w-6 animate-spin" aria-label="Завантаження" /></div>;
}

function EmptyChildren() {
  return <Card className="rounded-lg px-5 py-12 text-center"><UserRound className="mx-auto h-8 w-8 text-slate-400" aria-hidden="true" /><h2 className="mt-4 text-[0.98rem] font-bold text-slate-900">Немає прив’язаних дітей</h2><p className="mt-2 text-[0.8rem] text-slate-500">Зверніться до адміністратора навчального закладу.</p></Card>;
}

function TopBar() {
  const handleLogout = async () => {
    try { await authApi.logout(); } catch { /* The session may already be expired. */ }
    window.location.hash = "login";
  };
  return (
    <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-slate-200/80 bg-white px-4 sm:px-5 lg:px-6">
      <a href="#parent" className="inline-flex min-w-0 items-center gap-2 text-slate-900" aria-label="Електронний щоденник - головна"><GraduationCap className="h-7 w-7 shrink-0 sm:h-8 sm:w-8" strokeWidth={2.4} aria-hidden="true" /><span className="max-w-[150px] truncate text-[0.92rem] font-bold min-[420px]:max-w-none sm:text-[1.05rem]">Електронний щоденник</span></a>
      <div className="flex items-center gap-1.5 sm:gap-4"><NotificationButton /><div className="hidden items-center gap-3 bg-slate-50 px-3 py-1.5 sm:flex"><div className="text-right leading-tight"><div className="text-[0.78rem] font-bold">{userIdentities.PARENT.displayName}</div><div className="text-[0.7rem] text-slate-500">{userIdentities.PARENT.roleLabel}</div></div><UserRound className="h-8 w-8" strokeWidth={1.8} aria-hidden="true" /></div><button type="button" onClick={handleLogout} className="inline-flex h-10 items-center gap-2 rounded-md px-3 text-[0.82rem] font-semibold text-slate-500 hover:bg-slate-100 hover:text-slate-900"><LogOut className="h-4 w-4" aria-hidden="true" /><span className="hidden sm:inline">Вийти</span></button></div>
    </header>
  );
}

function NavigationItem({ item, compact = false }) {
  const Icon = item.icon;
  return <button type="button" disabled={!item.route} onClick={() => { if (item.route) window.location.hash = item.route; }} className={cn(compact ? "inline-flex h-10 min-w-0 items-center justify-center gap-1.5 rounded-md px-1" : "flex h-11 w-full items-center gap-3 rounded-md px-3 text-left", "text-[0.82rem] font-semibold transition-colors", item.active ? "bg-slate-100 text-slate-900" : "text-slate-500 hover:bg-slate-50 hover:text-slate-800 disabled:cursor-default disabled:hover:bg-transparent disabled:hover:text-slate-500")} aria-current={item.active ? "page" : undefined} title={!item.route ? `${item.label}: розділ у розробці` : undefined}><Icon className={compact ? "h-4 w-4" : "h-5 w-5"} aria-hidden="true" />{compact ? <span className="hidden truncate min-[480px]:inline">{item.shortLabel || item.label}</span> : item.label}</button>;
}

function MobileNavigation() {
  return <nav className="sticky top-16 z-20 border-b border-slate-200/80 bg-white px-3 py-2 lg:hidden" aria-label="Основна навігація"><div className="grid grid-cols-5 gap-1">{navigationItems.map((item) => <NavigationItem key={item.label} item={item} compact />)}</div></nav>;
}

function Sidebar() {
  return <aside className="hidden w-[210px] shrink-0 bg-white px-3 py-4 lg:block"><nav className="space-y-2" aria-label="Основна навігація">{navigationItems.map((item) => <NavigationItem key={item.label} item={item} />)}</nav></aside>;
}
