import { useEffect, useMemo, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import {
  ArrowDownRight,
  ArrowUpRight,
  BarChart3,
  BookOpen,
  CalendarDays,
  CheckCircle2,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  CircleAlert,
  ClipboardCheck,
  GraduationCap,
  Home,
  Loader2,
  LogOut,
  Mail,
  Minus,
  Target,
  User,
  UserRound,
} from "lucide-react";
import { AppFooter } from "@/components/AppFooter.jsx";
import { NotificationButton } from "@/components/NotificationButton.jsx";
import { CurrentUserBadge } from "@/lib/currentUser.jsx";
import { Alert } from "@/components/ui/alert.jsx";
import { Card } from "@/components/ui/card.jsx";
import { ApiError, authApi, parentApi } from "@/lib/api.js";
import { cn } from "@/lib/utils.js";
import {
  academicYears,
  getGradeForecast,
  getPeriodOptions,
  getSubjectPeriodView,
  getSubjectStats,
  subjectProgress,
} from "./subjectProgressModel.js";

const tabs = [
  { id: "grades", label: "Оцінки" },
  { id: "attendance", label: "Відвідування" },
  { id: "homework", label: "Домашні завдання" },
];

const attendanceLabels = {
  PRESENT: { label: "Присутній", className: "bg-emerald-50 text-emerald-700 ring-emerald-200" },
  ABSENT: { label: "Відсутній", className: "bg-red-50 text-red-600 ring-red-200" },
  EXCUSED: { label: "Поважна причина", className: "bg-sky-50 text-sky-700 ring-sky-200" },
  RELEASED: { label: "Звільнений", className: "bg-amber-50 text-amber-700 ring-amber-200" },
};

const homeworkLabels = {
  ACTIVE: { label: "Призначено", className: "bg-sky-50 text-sky-700 ring-sky-200" },
  COMPLETED: { label: "Виконано", className: "bg-emerald-50 text-emerald-700 ring-emerald-200" },
  COMPLETED_LATE: { label: "Виконано із запізненням", className: "bg-amber-50 text-amber-700 ring-amber-200" },
  OVERDUE: { label: "Прострочено", className: "bg-red-50 text-red-600 ring-red-200" },
};

const roleConfig = {
  STUDENT: {
    homeRoute: "student",
    subjectsRoute: "student/subjects",
    subtitle: "Успішність, відвідування та завдання з ваших предметів",
    navigation: [
      { label: "Головна", icon: Home, route: "student" },
      { label: "Розклад", icon: CalendarDays, route: "student/schedule" },
      { label: "Предмети", icon: BookOpen, route: "student/subjects" },
      { label: "Повідомлення", shortLabel: "Повідомл.", icon: Mail },
      { label: "Профіль", icon: User, route: "student/profile" },
    ],
  },
  PARENT: {
    homeRoute: "parent",
    subjectsRoute: "parent/subjects",
    subtitle: "Успішність, відвідування та завдання обраної дитини",
    navigation: [
      { label: "Головна", icon: Home, route: "parent" },
      { label: "Розклад", icon: CalendarDays, route: "parent/schedule" },
      { label: "Предмети", icon: BookOpen, route: "parent/subjects" },
      { label: "Повідомлення", shortLabel: "Повідомл.", icon: Mail },
      { label: "Профіль", icon: User, route: "parent/profile" },
    ],
  },
};

export function UserSubjectsPage({ role }) {
  const config = roleConfig[role];
  const [subjectId, setSubjectId] = useState(subjectProgress[0].id);
  const [academicYear, setAcademicYear] = useState(academicYears[0]);
  const [period, setPeriod] = useState("2026-09");
  const [activeTab, setActiveTab] = useState("grades");
  const [children, setChildren] = useState([]);
  const [studentId, setStudentId] = useState("");
  const [loadingChildren, setLoadingChildren] = useState(role === "PARENT");
  const [error, setError] = useState("");

  useEffect(() => {
    if (role !== "PARENT") return undefined;
    let active = true;

    parentApi.findChildren()
      .then((items) => {
        if (!active) return;
        setChildren(items);
        setStudentId(items[0] ? String(items[0].studentId) : "");
      })
      .catch((requestError) => {
        if (active) setError(requestError instanceof ApiError ? requestError.message : "Не вдалося завантажити дані дітей.");
      })
      .finally(() => active && setLoadingChildren(false));

    return () => { active = false; };
  }, [role]);

  const subject = useMemo(
    () => subjectProgress.find((item) => item.id === subjectId) || subjectProgress[0],
    [subjectId],
  );
  const periodOptions = useMemo(() => getPeriodOptions(academicYear), [academicYear]);
  const periodSubject = useMemo(
    () => getSubjectPeriodView(subject, academicYear, period),
    [academicYear, period, subject],
  );
  const stats = useMemo(() => getSubjectStats(periodSubject), [periodSubject]);
  const forecast = useMemo(
    () => getGradeForecast(subject, academicYear, period),
    [academicYear, period, subject],
  );
  const selectedChild = children.find((item) => String(item.studentId) === studentId) || null;
  const canShowContent = role === "STUDENT" || selectedChild;

  return (
    <div className="flex min-h-screen flex-col bg-slate-50 text-slate-800">
      <TopBar config={config} />
      <Navigation config={config} compact />

      <div className="flex flex-1">
        <Navigation config={config} />
        <div className="flex min-w-0 flex-1 flex-col lg:border-l lg:border-slate-200/80">
          <main className="min-w-0 flex-1 px-4 py-5 pb-8 sm:px-6 lg:px-8 lg:py-6">
            <div className="mx-auto w-full max-w-[1280px] 2xl:max-w-[1500px] min-[1920px]:max-w-[1680px] min-[2300px]:max-w-[1840px]">
              <header className="mb-5 sm:mb-6">
                <h1 className="text-[1.45rem] font-bold leading-tight text-slate-900 sm:text-[1.65rem]">Предмети</h1>
                <p className="mt-2 text-[0.9rem] text-slate-500">{config.subtitle}</p>
              </header>

              {error ? <Alert>{error}</Alert> : null}
              {loadingChildren ? <LoadingState /> : null}
              {!loadingChildren && !error ? (
                <>
                  <SubjectControls
                    role={role}
                    children={children}
                    studentId={studentId}
                    subjectId={subjectId}
                    subject={subject}
                    academicYear={academicYear}
                    period={period}
                    periodOptions={periodOptions}
                    onStudentChange={setStudentId}
                    onSubjectChange={setSubjectId}
                    onAcademicYearChange={(value) => {
                      setAcademicYear(value);
                      setPeriod("YEAR");
                    }}
                    onPeriodChange={setPeriod}
                  />

                  {!canShowContent ? <EmptyChildren /> : null}
                  {canShowContent ? (
                    <>
                      <SummaryCards tab={activeTab} stats={stats} forecast={forecast} />
                      <section className="mt-5 overflow-hidden rounded-lg border border-slate-200 bg-white shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)]">
                        <SubjectTabs activeTab={activeTab} onChange={setActiveTab} />
                        <AnimatePresence mode="wait" initial={false}>
                          <motion.div
                            key={`${subject.id}-${academicYear}-${period}-${activeTab}`}
                            initial={{ opacity: 0, y: 5 }}
                            animate={{ opacity: 1, y: 0 }}
                            exit={{ opacity: 0, y: -3 }}
                            transition={{ duration: 0.18, ease: "easeOut" }}
                            className="p-4 sm:p-5"
                          >
                            {activeTab === "grades" ? <GradesPanel subject={periodSubject} stats={stats} /> : null}
                            {activeTab === "attendance" ? <AttendancePanel subject={periodSubject} /> : null}
                            {activeTab === "homework" ? <HomeworkPanel subject={periodSubject} /> : null}
                          </motion.div>
                        </AnimatePresence>
                      </section>
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

function SubjectControls({
  role,
  children,
  studentId,
  subjectId,
  subject,
  academicYear,
  period,
  periodOptions,
  onStudentChange,
  onSubjectChange,
  onAcademicYearChange,
  onPeriodChange,
}) {
  return (
    <Card className="rounded-lg p-4 sm:p-5">
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-12 xl:items-end">
        {role === "PARENT" ? (
          <SelectField label="Дитина" htmlFor="subjects-child" className="xl:col-span-2">
            <select id="subjects-child" value={studentId} onChange={(event) => onStudentChange(event.target.value)} className="h-10 w-full appearance-none rounded-md border border-slate-300 bg-white px-3 pr-9 text-[0.82rem] font-medium text-slate-700 outline-hidden transition-colors hover:border-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200">
              {children.length ? children.map((child) => (
                <option key={child.studentId} value={child.studentId}>{child.firstName} {child.lastName}{child.className ? ` · ${child.className}` : ""}</option>
              )) : <option value="">Немає прив’язаних дітей</option>}
            </select>
          </SelectField>
        ) : null}

        <SelectField label="Предмет" htmlFor="subjects-subject" className="xl:col-span-3">
          <select id="subjects-subject" value={subjectId} onChange={(event) => onSubjectChange(event.target.value)} className="h-10 w-full appearance-none rounded-md border border-slate-300 bg-white px-3 pr-9 text-[0.82rem] font-medium text-slate-700 outline-hidden transition-colors hover:border-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200">
            {subjectProgress.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
          </select>
        </SelectField>

        <SelectField label="Навчальний рік" htmlFor="subjects-academic-year" className="xl:col-span-2">
          <select id="subjects-academic-year" value={academicYear} onChange={(event) => onAcademicYearChange(event.target.value)} className="h-10 w-full appearance-none rounded-md border border-slate-300 bg-white px-3 pr-9 text-[0.82rem] font-medium text-slate-700 outline-hidden transition-colors hover:border-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200">
            {academicYears.map((item) => <option key={item} value={item}>{item} н. р.</option>)}
          </select>
        </SelectField>

        <PeriodField
          value={period}
          options={periodOptions}
          onChange={onPeriodChange}
          className={role === "PARENT" ? "xl:col-span-3" : "xl:col-span-4"}
        />

        <div className={cn("min-w-0", role === "PARENT" ? "xl:col-span-2" : "xl:col-span-3")}>
          <p className="text-[0.72rem] font-semibold text-slate-500">Викладач</p>
          <p className="mt-2 truncate text-[0.82rem] font-bold text-slate-800">{subject.teacher}</p>
          <p className="mt-1 text-[0.7rem] text-slate-400">{academicYear} н. р.</p>
        </div>
      </div>
    </Card>
  );
}

function SelectField({ label, htmlFor, className, children }) {
  return (
    <label htmlFor={htmlFor} className={cn("block", className)}>
      <span className="mb-2 block text-[0.75rem] font-semibold text-slate-600">{label}</span>
      <span className="relative block">
        {children}
        <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
      </span>
    </label>
  );
}

function PeriodField({ value, options, onChange, className }) {
  const currentIndex = options.findIndex((item) => item.value === value);
  const selectByOffset = (offset) => {
    const next = options[currentIndex + offset];
    if (next) onChange(next.value);
  };

  return (
    <div className={className}>
      <label id="subjects-period-label" htmlFor="subjects-period" className="mb-2 block text-[0.75rem] font-semibold text-slate-600">Період</label>
      <div className="grid h-10 grid-cols-[40px_minmax(0,1fr)_40px] overflow-hidden rounded-md border border-slate-300 bg-white">
        <button type="button" onClick={() => selectByOffset(-1)} disabled={currentIndex <= 0} className="flex items-center justify-center border-r border-slate-200 text-slate-500 transition-colors hover:bg-slate-50 hover:text-slate-900 disabled:cursor-default disabled:opacity-30" aria-label="Попередній період"><ChevronLeft className="h-4 w-4" aria-hidden="true" /></button>
        <span className="relative min-w-0">
          <select id="subjects-period" value={value} onChange={(event) => onChange(event.target.value)} className="h-full w-full appearance-none bg-white px-3 pr-8 text-center text-[0.78rem] font-semibold text-slate-800 outline-hidden" aria-labelledby="subjects-period-label">
            {options.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
          </select>
          <ChevronDown className="pointer-events-none absolute right-2 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-slate-400" aria-hidden="true" />
        </span>
        <button type="button" onClick={() => selectByOffset(1)} disabled={currentIndex < 0 || currentIndex >= options.length - 1} className="flex items-center justify-center border-l border-slate-200 text-slate-500 transition-colors hover:bg-slate-50 hover:text-slate-900 disabled:cursor-default disabled:opacity-30" aria-label="Наступний період"><ChevronRight className="h-4 w-4" aria-hidden="true" /></button>
      </div>
    </div>
  );
}

function SummaryCards({ tab, stats, forecast }) {
  const cards = tab === "attendance"
    ? [
      { label: "Кількість пропущених уроків", value: stats.missed, icon: CalendarDays, tone: "slate" },
      { label: "Пропущено уроків", value: `${stats.missedPercent}%`, icon: CircleAlert, tone: stats.missedPercent > 20 ? "red" : "slate" },
      { label: "Відвідано уроків", value: `${stats.attended} із ${stats.totalLessons}`, icon: CheckCircle2, tone: "emerald" },
    ]
    : tab === "homework"
      ? [
        { label: "Призначено завдань", value: stats.activeHomework, icon: CheckCircle2, tone: "emerald" },
        { label: "Прострочені завдання", value: stats.overdueHomework, icon: CircleAlert, tone: stats.overdueHomework ? "red" : "slate" },
        { label: "Виконано завдань", value: stats.completedHomework, detail: `з них ${stats.completedLateHomework} із запізненням`, icon: ClipboardCheck, tone: "emerald" },
      ]
      : [
        { label: "Середній бал з предмета", value: stats.average ?? "—", suffix: stats.average === null ? "" : "з 12", icon: BarChart3, tone: "slate" },
        { label: "Динаміка успішності", value: formatDelta(stats.delta), detail: deltaDescription(stats.delta), icon: deltaIcon(stats.delta), tone: stats.delta > 0 ? "emerald" : stats.delta < 0 ? "red" : "slate" },
      ];

  return (
    <div className="mt-5 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      {cards.map((card) => <MetricCard key={card.label} {...card} />)}
      {tab === "grades" ? <GradeForecastCard forecast={forecast} /> : null}
    </div>
  );
}

function GradeForecastCard({ forecast }) {
  const items = [
    { label: "Тематична", value: forecast.thematic },
    { label: "Семестрова", value: forecast.semester },
    { label: "Річна", value: forecast.annual },
  ];

  return (
    <Card className="rounded-lg p-4 sm:col-span-2 sm:p-5 xl:col-span-1">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-[0.82rem] font-bold text-slate-900">Прогноз оцінок</h2>
          <p className="mt-1 text-[0.7rem] text-slate-500">За наявними оцінками</p>
        </div>
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-slate-100 text-slate-600"><Target className="h-4.5 w-4.5" aria-hidden="true" /></span>
      </div>
      <div className="mt-3 grid grid-cols-3">
        {items.map((item, index) => (
          <div key={item.label} className={cn("min-w-0 px-1 text-center sm:px-2", index > 0 && "border-l border-slate-200")}>
            <p className="truncate text-[0.64rem] font-semibold text-slate-500">{item.label}</p>
            <p className="mt-2 text-[1.05rem] font-bold leading-none text-slate-900">{item.value ?? "—"}</p>
          </div>
        ))}
      </div>
      <p className="mt-3 truncate text-[0.64rem] text-slate-400" title="Тематична оцінка виставляється на останньому уроці місяця.">Тематична — на останньому уроці місяця</p>
    </Card>
  );
}

function MetricCard({ label, value, suffix, detail, icon: Icon, tone }) {
  const iconClass = {
    emerald: "bg-emerald-50 text-emerald-700",
    red: "bg-red-50 text-red-600",
    slate: "bg-slate-100 text-slate-600",
  }[tone];

  return (
    <Card className="rounded-lg p-4 sm:p-5">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="text-[0.74rem] font-semibold text-slate-500">{label}</p>
          <div className="mt-3 flex items-baseline gap-2">
            <strong className="text-[1.65rem] font-bold leading-none text-slate-900">{value}</strong>
            {suffix ? <span className="text-[0.72rem] font-medium text-slate-400">{suffix}</span> : null}
          </div>
          {detail ? <p className="mt-2 text-[0.72rem] text-slate-500">{detail}</p> : null}
        </div>
        <span className={cn("flex h-9 w-9 shrink-0 items-center justify-center rounded-md", iconClass)}>
          <Icon className="h-4.5 w-4.5" aria-hidden="true" />
        </span>
      </div>
    </Card>
  );
}

function SubjectTabs({ activeTab, onChange }) {
  return (
    <div className="border-b border-slate-200 px-2 sm:px-4" role="tablist" aria-label="Дані з предмета">
      <div className="flex overflow-x-auto">
        {tabs.map((tab) => {
          const active = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              type="button"
              role="tab"
              aria-selected={active}
              onClick={() => onChange(tab.id)}
              className={cn("relative min-w-max px-4 py-4 text-[0.78rem] font-semibold transition-colors sm:px-6", active ? "text-slate-900" : "text-slate-400 hover:text-slate-700")}
            >
              {tab.label}
              {active ? <motion.span layoutId="subject-tab-indicator" className="absolute inset-x-3 bottom-0 h-0.5 bg-slate-900" transition={{ duration: 0.2, ease: "easeOut" }} /> : null}
            </button>
          );
        })}
      </div>
    </div>
  );
}

function GradesPanel({ subject, stats }) {
  if (!subject.grades.length) return <EmptyPeriodData message="За вибраний період оцінок немає" />;
  return (
    <>
      <PanelHeading title="Оцінки за період" meta={`${countLabel(subject.grades.length, "оцінка", "оцінки", "оцінок")} · середній бал ${stats.average ?? "—"}`} />
      <div className="mt-4 hidden overflow-x-auto rounded-md border border-slate-200 md:block">
        <table className="w-full min-w-[680px] table-fixed border-collapse text-left">
          <colgroup><col className="w-[16%]" /><col className="w-[30%]" /><col className="w-[14%]" /><col className="w-[40%]" /></colgroup>
          <thead className="bg-slate-50"><tr className="border-b border-slate-200 text-[0.7rem] font-bold text-slate-500"><th className="border-r border-slate-200 px-4 py-3">Дата</th><th className="border-r border-slate-200 px-4 py-3">Тип роботи</th><th className="border-r border-slate-200 px-4 py-3 text-center">Оцінка</th><th className="px-4 py-3">Коментар учителя</th></tr></thead>
          <tbody>{subject.grades.map((item) => <tr key={item.id} className="border-b border-slate-100 bg-white text-[0.8rem] transition-colors even:bg-slate-50/40 hover:bg-slate-50 last:border-0"><td className="border-r border-slate-100 px-4 py-3.5 font-medium text-slate-500">{item.date}</td><td className="border-r border-slate-100 px-4 py-3.5 font-semibold text-slate-800">{item.workType}</td><td className="border-r border-slate-100 px-4 py-3.5 text-center"><GradeBadge value={item.grade} /></td><td className="px-4 py-3.5 text-slate-500">{item.comment || "—"}</td></tr>)}</tbody>
        </table>
      </div>
      <div className="mt-4 space-y-3 md:hidden">
        {subject.grades.map((item) => <article key={item.id} className="rounded-md border border-slate-200 bg-slate-50/70 p-3"><div className="flex items-start justify-between gap-3"><div><p className="text-[0.7rem] font-medium text-slate-400">{item.date}</p><h3 className="mt-1 text-[0.82rem] font-bold text-slate-800">{item.workType}</h3></div><GradeBadge value={item.grade} /></div><p className="mt-3 text-[0.74rem] leading-5 text-slate-500">{item.comment || "Без коментаря"}</p></article>)}
      </div>
    </>
  );
}

function AttendancePanel({ subject }) {
  if (!subject.attendance.length) return <EmptyPeriodData message="За вибраний період записів відвідування немає" />;
  return (
    <>
      <PanelHeading title="Відвідування уроків" meta={`${countLabel(subject.attendance.length, "заняття", "заняття", "занять")} у періоді`} />
      <div className="mt-4 hidden overflow-x-auto rounded-md border border-slate-200 md:block">
        <table className="w-full min-w-[640px] table-fixed border-collapse text-left">
          <colgroup><col className="w-[16%]" /><col className="w-[58%]" /><col className="w-[26%]" /></colgroup>
          <thead className="bg-slate-50">
            <tr className="border-b border-slate-200 text-[0.7rem] font-bold text-slate-500">
              <th className="border-r border-slate-200 px-4 py-3">Дата</th>
              <th className="border-r border-slate-200 px-4 py-3">Тема уроку</th>
              <th className="px-4 py-3 text-center">Статус відвідування</th>
            </tr>
          </thead>
          <tbody>
            {subject.attendance.map((item) => {
              const status = attendanceLabels[item.status];
              return (
                <tr key={item.id} className="border-b border-slate-100 bg-white text-[0.8rem] transition-colors even:bg-slate-50/40 hover:bg-slate-50 last:border-0">
                  <td className="border-r border-slate-100 px-4 py-3.5 font-medium text-slate-500">{item.date}</td>
                  <td className="border-r border-slate-100 px-4 py-3.5 font-semibold text-slate-800">{item.topic}</td>
                  <td className="px-4 py-3.5 text-center"><StatusBadge status={status} /></td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <div className="mt-4 space-y-3 md:hidden">
        {subject.attendance.map((item) => {
          const status = attendanceLabels[item.status];
          return (
            <article key={item.id} className="rounded-md border border-slate-200 bg-slate-50/70 p-3">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <time className="text-[0.7rem] font-medium text-slate-400">{item.date}</time>
                  <h3 className="mt-1 text-[0.82rem] font-bold leading-5 text-slate-800">{item.topic}</h3>
                </div>
                <StatusBadge status={status} />
              </div>
            </article>
          );
        })}
      </div>
    </>
  );
}

function HomeworkPanel({ subject }) {
  if (!subject.homework.length) return <EmptyPeriodData message="За вибраний період домашніх завдань немає" />;
  return (
    <>
      <PanelHeading title="Домашні завдання" meta={`${countLabel(subject.homework.length, "завдання", "завдання", "завдань")} у періоді`} />
      <div className="mt-4 hidden overflow-x-auto rounded-md border border-slate-200 md:block">
        <table className="w-full min-w-[760px] table-fixed border-collapse text-left">
          <colgroup><col className="w-[16%]" /><col className="w-[16%]" /><col className="w-[42%]" /><col className="w-[26%]" /></colgroup>
          <thead className="bg-slate-50">
            <tr className="border-b border-slate-200 text-[0.7rem] font-bold text-slate-500">
              <th className="border-r border-slate-200 px-4 py-3">Дата видачі</th>
              <th className="border-r border-slate-200 px-4 py-3">Термін здачі</th>
              <th className="border-r border-slate-200 px-4 py-3">Опис домашнього завдання</th>
              <th className="px-4 py-3 text-center">Статус домашнього завдання</th>
            </tr>
          </thead>
          <tbody>
            {subject.homework.map((item) => {
              const status = homeworkLabels[item.status];
              return (
                <tr key={item.id} className="border-b border-slate-100 bg-white text-[0.8rem] transition-colors even:bg-slate-50/40 hover:bg-slate-50 last:border-0">
                  <td className="border-r border-slate-100 px-4 py-3.5 font-medium text-slate-500">{item.assignedDate}</td>
                  <td className="border-r border-slate-100 px-4 py-3.5 font-medium text-slate-500">{item.dueDate || "—"}</td>
                  <td className="border-r border-slate-100 px-4 py-3.5 font-semibold text-slate-800">{item.title}</td>
                  <td className="px-4 py-3.5 text-center"><StatusBadge status={status} /></td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <div className="mt-4 space-y-3 md:hidden">
        {subject.homework.map((item) => {
          const status = homeworkLabels[item.status];
          return (
            <article key={item.id} className="rounded-md border border-slate-200 bg-slate-50/70 p-3">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-[0.7rem] font-medium text-slate-400">{item.assignedDate} · до {item.dueDate || "без терміну"}</p>
                  <h3 className="mt-1 text-[0.82rem] font-bold leading-5 text-slate-800">{item.title}</h3>
                </div>
                <StatusBadge status={status} />
              </div>
            </article>
          );
        })}
      </div>
    </>
  );
}

function StatusBadge({ status }) {
  return <span className={cn("inline-flex w-fit shrink-0 rounded-md px-2.5 py-1.5 text-[0.68rem] font-bold ring-1 ring-inset", status.className)}>{status.label}</span>;
}

function PanelHeading({ title, meta }) {
  return <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between"><h2 className="text-[0.98rem] font-bold text-slate-900">{title}</h2><p className="text-[0.72rem] font-medium text-slate-400">{meta}</p></div>;
}

function EmptyPeriodData({ message }) {
  return <div className="flex min-h-52 flex-col items-center justify-center text-center"><BookOpen className="h-7 w-7 text-slate-300" aria-hidden="true" /><p className="mt-3 text-[0.8rem] font-medium text-slate-500">{message}</p></div>;
}

function GradeBadge({ value }) {
  const tone = value >= 10
    ? "bg-emerald-50 text-emerald-700 ring-emerald-200"
    : value >= 7
      ? "bg-sky-50 text-sky-700 ring-sky-200"
      : value >= 4
        ? "bg-amber-50 text-amber-700 ring-amber-200"
        : "bg-red-50 text-red-600 ring-red-200";
  return <span className={cn("inline-flex h-8 min-w-8 items-center justify-center rounded-md px-2 text-[0.78rem] font-bold ring-1 ring-inset", tone)}>{value}</span>;
}

function TopBar({ config }) {
  const logout = async () => {
    try { await authApi.logout(); } catch { /* The session may already be expired. */ }
    window.location.hash = "login";
  };
  return (
    <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-slate-200/80 bg-white px-4 sm:px-5 lg:px-6">
      <a href={`#${config.homeRoute}`} className="inline-flex min-w-0 items-center gap-2 text-slate-900" aria-label="Електронний щоденник - головна"><GraduationCap className="h-7 w-7 shrink-0 sm:h-8 sm:w-8" strokeWidth={2.4} aria-hidden="true" /><span className="max-w-[150px] truncate text-[0.92rem] font-bold min-[420px]:max-w-none sm:text-[1.05rem]">Електронний щоденник</span></a>
      <div className="flex items-center gap-1.5 sm:gap-4"><NotificationButton /><CurrentUserBadge /><button type="button" onClick={logout} className="inline-flex h-10 items-center gap-2 rounded-md px-3 text-[0.82rem] font-semibold text-slate-500 hover:bg-slate-100 hover:text-slate-900"><LogOut className="h-4 w-4" aria-hidden="true" /><span className="hidden sm:inline">Вийти</span></button></div>
    </header>
  );
}

function Navigation({ config, compact = false }) {
  const content = config.navigation.map((item) => {
    const Icon = item.icon;
    const active = item.route === config.subjectsRoute;
    const classes = cn(compact ? "inline-flex h-10 min-w-0 items-center justify-center gap-1.5 rounded-md px-1" : "flex h-11 w-full items-center gap-3 rounded-md px-3 text-left", "text-[0.82rem] font-semibold transition-colors", active ? "bg-slate-100 text-slate-900" : "text-slate-500 hover:bg-slate-50 hover:text-slate-800", !item.route && "cursor-default hover:bg-transparent hover:text-slate-500");
    const label = <><Icon className={compact ? "h-4 w-4 shrink-0" : "h-5 w-5 shrink-0"} aria-hidden="true" />{compact ? <span className="hidden truncate min-[480px]:inline">{item.shortLabel || item.label}</span> : item.label}</>;
    return item.route
      ? <a key={item.label} href={`#${item.route}`} className={classes} aria-current={active ? "page" : undefined} aria-label={compact ? item.label : undefined}>{label}</a>
      : <button key={item.label} type="button" disabled className={classes} title={`${item.label}: розділ у розробці`}>{label}</button>;
  });

  if (compact) return <nav className="sticky top-16 z-20 border-b border-slate-200/80 bg-white px-3 py-2 lg:hidden" aria-label="Основна навігація"><div className="grid grid-cols-5 gap-1">{content}</div></nav>;
  return <aside className="hidden w-[210px] shrink-0 bg-white px-3 py-4 lg:block"><nav className="space-y-2" aria-label="Основна навігація">{content}</nav></aside>;
}

function LoadingState() {
  return <div className="flex min-h-72 items-center justify-center text-slate-400"><Loader2 className="h-6 w-6 animate-spin" aria-label="Завантаження" /></div>;
}

function EmptyChildren() {
  return <Card className="mt-5 rounded-lg px-5 py-12 text-center"><UserRound className="mx-auto h-8 w-8 text-slate-400" aria-hidden="true" /><h2 className="mt-4 text-[0.98rem] font-bold text-slate-900">Немає прив’язаних дітей</h2><p className="mt-2 text-[0.8rem] text-slate-500">Додайте дитину на головній сторінці профілю батьків.</p></Card>;
}

function formatDelta(delta) {
  if (delta === null) return "—";
  if (delta > 0) return `+${delta}`;
  return String(delta);
}

function deltaDescription(delta) {
  if (delta === null) return "Недостатньо даних для порівняння";
  if (delta > 0) return "Покращення порівняно з попереднім періодом";
  if (delta < 0) return "Зниження порівняно з попереднім періодом";
  return "Без змін порівняно з попереднім періодом";
}

function deltaIcon(delta) {
  if (delta > 0) return ArrowUpRight;
  if (delta < 0) return ArrowDownRight;
  return Minus;
}

function countLabel(count, one, few, many) {
  const mod100 = count % 100;
  const mod10 = count % 10;
  const word = mod100 >= 11 && mod100 <= 14 ? many : mod10 === 1 ? one : mod10 >= 2 && mod10 <= 4 ? few : many;
  return `${count} ${word}`;
}
