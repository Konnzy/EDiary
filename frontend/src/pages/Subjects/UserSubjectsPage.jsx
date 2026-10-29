import { WorkTypeBadge } from "@/components/WorkTypeBadge.jsx";
import { gradeDisplay } from "@/lib/assessment.js";
import { useEffect, useMemo, useRef, useState } from "react";
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
  ChevronUp,
  ChevronsUpDown,
  CircleAlert,
  ClipboardCheck,
  GraduationCap,
  Home,
  Loader2,
  LogOut,
  Mail,
  Minus,
  Search,
  Target,
  User,
} from "lucide-react";
import { AppFooter } from "@/components/AppFooter.jsx";
import { LearnerNavigation } from "@/components/LearnerNavigation.jsx";
import { NotificationButton } from "@/components/NotificationButton.jsx";
import { CurrentUserBadge } from "@/lib/currentUser.jsx";
import { Alert } from "@/components/ui/alert.jsx";
import { Card } from "@/components/ui/card.jsx";
import { ApiError, authApi, parentApi, subjectsApi } from "@/lib/api.js";
import { childOptionLabel } from "@/lib/childOption.js";
import { cn } from "@/lib/utils.js";
import {
  getCurrentMonthPeriod,
  getGradeForecast,
  getPeriodOptions,
  getSubjectPeriodView,
  getSubjectStats,
  paginateProgressRows,
  sortProgressRows,
} from "./subjectProgressModel.js";

const workTypeLabels = {
  CLASSWORK: "Класна робота", HOMEWORK: "Домашня робота", INDEPENDENT: "Самостійна робота",
  CONTROL: "Контрольна робота", PRACTICAL: "Практична робота", LABORATORY: "Лабораторна робота", THEMATIC: "Тематична",
  SEMESTER: "Семестрова", YEAR: "Річна",
};


const tabs = [
  { id: "grades", label: "Оцінки" },
  { id: "attendance", label: "Відвідування" },
  { id: "homework", label: "Домашні завдання" },
];
const ALL_SUBJECTS_ID = "ALL";

function mergeSubjects(subjects) {
  const teachers = [...new Set(subjects.map((subject) => subject.teacher).filter(Boolean))];
  const combine = (key, dateKey) => subjects
    .flatMap((subject) => subject[key].map((item) => ({ ...item, subjectName: subject.name })))
    .sort((first, second) => second[dateKey].localeCompare(first[dateKey]));
  return {
    id: ALL_SUBJECTS_ID,
    name: "Всі предмети",
    teacher: teachers.length === 0 ? "Не призначено" : teachers.length === 1 ? teachers[0] : "Кілька викладачів",
    grades: combine("grades", "date"),
    attendance: combine("attendance", "date"),
    homework: combine("homework", "assignedDate"),
  };
}

const attendanceLabels = {
  PRESENT: { label: "Присутній", className: "bg-emerald-50 text-emerald-700 ring-emerald-200" },
  ABSENT: { label: "Відсутній", className: "bg-red-50 text-red-600 ring-red-200" },
  EXCUSED_ABSENCE: { label: "Поважна причина", className: "bg-sky-50 text-sky-700 ring-sky-200" },
  LATE: { label: "Запізнився", className: "bg-amber-50 text-amber-700 ring-amber-200" },
  EXEMPTED: { label: "Звільнений від заняття", className: "bg-violet-50 text-violet-700 ring-violet-200" },
};

const homeworkLabels = {
  ASSIGNED: { label: "Призначено", className: "bg-sky-50 text-sky-700 ring-sky-200" },
  COMPLETED: { label: "Виконано", className: "bg-emerald-50 text-emerald-700 ring-emerald-200" },
  SUBMITTED_LATE: { label: "Виконано із запізненням", className: "bg-amber-50 text-amber-700 ring-amber-200" },
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
      { label: "Повідомлення", shortLabel: "Повідомл.", icon: Mail, route: "student/messages" },
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
      { label: "Повідомлення", shortLabel: "Повідомл.", icon: Mail, route: "parent/messages" },
      { label: "Профіль", icon: User, route: "parent/profile" },
    ],
  },
};

export function UserSubjectsPage({ role, initialStudentId = "" }) {
  const config = roleConfig[role];
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
              <SubjectProgressView role={role} initialStudentId={initialStudentId} />
            </div>
          </main>
          <AppFooter />
        </div>
      </div>
    </div>
  );
}

export function SubjectProgressView({ role, adminStudentId, classId, initialAcademicYear = "", initialStudentId = "" }) {
  const staffView = role === "ADMIN" || role === "HOMEROOM";
  const [subjectId, setSubjectId] = useState("");
  const [academicYear, setAcademicYear] = useState(initialAcademicYear);
  const [academicYears, setAcademicYears] = useState([]);
  const [subjects, setSubjects] = useState([]);
  const [period, setPeriod] = useState(staffView ? "YEAR" : getCurrentMonthPeriod(initialAcademicYear));
  const [activeTab, setActiveTab] = useState("grades");
  const [children, setChildren] = useState([]);
  const [studentId, setStudentId] = useState(staffView ? String(adminStudentId) : "");
  const [loadingChildren, setLoadingChildren] = useState(role === "PARENT");
  const [loadingSubjects, setLoadingSubjects] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (role !== "PARENT") return undefined;
    let active = true;

    parentApi.findChildren()
      .then((items) => {
        if (!active) return;
        setChildren(items);
        const requestedChild = items.find((child) => String(child.studentId) === String(initialStudentId));
        setStudentId(requestedChild ? String(requestedChild.studentId) : items[0] ? String(items[0].studentId) : "");
      })
      .catch((requestError) => {
        if (active) setError(requestError instanceof ApiError ? requestError.message : "Не вдалося завантажити дані дітей.");
      })
      .finally(() => active && setLoadingChildren(false));

    return () => { active = false; };
  }, [role, initialStudentId]);

  useEffect(() => {
    if (role === "PARENT" && !studentId) return undefined;
    let active = true;
    setLoadingSubjects(true);
    setError("");
    const load = role === "PARENT"
      ? subjectsApi.parent(studentId, academicYear)
      : role === "ADMIN"
        ? subjectsApi.admin(studentId, academicYear)
        : role === "HOMEROOM"
          ? subjectsApi.homeroom(classId, studentId, academicYear)
        : subjectsApi.student(academicYear);
    load.then((result) => {
      if (!active) return;
      setAcademicYears([...new Set([result.currentAcademicYear, ...(result.academicYears || [])].filter(Boolean))]);
      if (!academicYear) {
        const initialAcademicYear = result.currentAcademicYear || result.academicYears?.[0] || "";
        setAcademicYear(initialAcademicYear);
        setPeriod(staffView ? "YEAR" : getCurrentMonthPeriod(initialAcademicYear));
      }
      const items = (result.subjects || []).map((item) => ({
        ...item,
        grades: item.grades.map((grade) => ({
          ...grade,
          grade: gradeDisplay(grade),
          workTypeCode: grade.workType,
          workType: workTypeLabels[grade.workType] || grade.workType,
        })),
      }));
      setSubjects(items);
      setSubjectId((current) => {
        if (staffView && (current === ALL_SUBJECTS_ID || !current)) return ALL_SUBJECTS_ID;
        return items.some((item) => String(item.id) === String(current)) ? current : String(items[0]?.id || "");
      });
    }).catch((requestError) => {
      if (active) setError(requestError instanceof ApiError ? requestError.message : "Не вдалося завантажити предмети.");
    }).finally(() => { if (active) setLoadingSubjects(false); });
    return () => { active = false; };
  }, [role, studentId, classId, academicYear, staffView]);

  const subject = useMemo(
    () => staffView && subjectId === ALL_SUBJECTS_ID && subjects.length
      ? mergeSubjects(subjects)
      : subjects.find((item) => String(item.id) === String(subjectId)) || null,
    [staffView, subjects, subjectId],
  );
  const allSubjects = staffView && subjectId === ALL_SUBJECTS_ID;
  const periodOptions = useMemo(() => getPeriodOptions(academicYear), [academicYear]);
  const periodSubject = useMemo(
    () => getSubjectPeriodView(subject, academicYear, period),
    [academicYear, period, subject],
  );
  const stats = useMemo(() => getSubjectStats(periodSubject), [periodSubject]);
  const yearStats = useMemo(() => {
    const yearSubject = getSubjectPeriodView(subject, academicYear, "YEAR");
    return getSubjectStats(yearSubject);
  }, [academicYear, subject]);
  const forecast = useMemo(
    () => getGradeForecast(subject, academicYear, period, staffView ? 2 : 1),
    [academicYear, period, staffView, subject],
  );
  const selectedChild = children.find((item) => String(item.studentId) === studentId) || null;
  const canShowContent = role !== "PARENT" || selectedChild;

  return (
    <>
              {error ? <Alert>{error}</Alert> : null}
              {loadingChildren || loadingSubjects ? <LoadingState /> : null}
              {!loadingChildren && !loadingSubjects && !error ? (
                <>
                  <SubjectControls
                    role={role}
                    children={children}
                    studentId={studentId}
                    subjectId={subjectId}
                    subject={subject}
                    subjects={subjects}
                    academicYear={academicYear}
                    academicYears={academicYears}
                    period={period}
                    periodOptions={periodOptions}
                    onStudentChange={(value) => {
                      if (value === "__add_child__") {
                        window.location.hash = "parent/profile?addChild=1";
                        return;
                      }
                      setStudentId(value);
                    }}
                    onSubjectChange={setSubjectId}
                    onAcademicYearChange={(value) => {
                      setAcademicYear(value);
                      setPeriod("YEAR");
                    }}
                    onPeriodChange={setPeriod}
                  />

                  {!canShowContent ? <EmptyChildren /> : null}
                  {canShowContent && !subject ? <EmptyPeriodData message="Для вибраного навчального року предметів поки немає" /> : null}
                  {canShowContent && subject ? (
                    <>
                      <SummaryCards tab={activeTab} stats={stats} yearStats={yearStats} period={period} forecast={forecast} allSubjects={allSubjects} gradeCount={periodSubject.grades.length} passFail={periodSubject.assessmentPolicy?.mode === "PASS_FAIL"} />
                      <section className="mt-5 overflow-hidden rounded-lg border border-slate-200 bg-white shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)]">
                        <SubjectTabs activeTab={activeTab} onChange={setActiveTab} allSubjects={allSubjects} />
                        <AnimatePresence mode="wait" initial={false}>
                          <motion.div
                            key={`${studentId}-${subject.id}-${academicYear}-${period}-${activeTab}`}
                            initial={{ opacity: 0, y: 5 }}
                            animate={{ opacity: 1, y: 0 }}
                            exit={{ opacity: 0, y: -3 }}
                            transition={{ duration: 0.18, ease: "easeOut" }}
                            className="p-4 sm:p-5"
                          >
                            {activeTab === "grades" ? <GradesPanel subject={periodSubject} stats={stats} showSubject={allSubjects} /> : null}
                            {activeTab === "attendance" ? <AttendancePanel subject={periodSubject} showSubject={allSubjects} /> : null}
                            {activeTab === "homework" ? <HomeworkPanel subject={periodSubject} showSubject={allSubjects} /> : null}
                          </motion.div>
                        </AnimatePresence>
                      </section>
                    </>
                  ) : null}
                </>
              ) : null}
    </>
  );
}

function SubjectControls({
  role,
  children,
  studentId,
  subjectId,
  subject,
  subjects,
  academicYear,
  academicYears,
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
                <option key={child.studentId} value={child.studentId}>{childOptionLabel(child)}</option>
              )) : <option value="">Немає прив’язаних дітей</option>}
              <option value="__add_child__">+ Додати дитину</option>
            </select>
          </SelectField>
        ) : null}

        <SubjectCombobox subjects={(role === "ADMIN" || role === "HOMEROOM") && subjects.length ? [{ id: ALL_SUBJECTS_ID, name: "Всі предмети" }, ...subjects] : subjects} value={subjectId} onChange={onSubjectChange} />

        <SelectField label="Навчальний рік" htmlFor="subjects-academic-year" className="xl:col-span-2">
          <select id="subjects-academic-year" value={academicYear} onChange={(event) => onAcademicYearChange(event.target.value)} className="h-10 w-full appearance-none rounded-md border border-slate-300 bg-white px-3 pr-9 text-[0.82rem] font-medium text-slate-700 outline-hidden transition-colors hover:border-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200">
            {academicYears.map((item) => <option key={item} value={item}>{item} н. р.</option>)}
          </select>
        </SelectField>

        <PeriodField
          value={period}
          options={periodOptions}
          onChange={onPeriodChange}
          className={role === "PARENT" ? "xl:col-span-3" : (role === "ADMIN" || role === "HOMEROOM") && subjectId === ALL_SUBJECTS_ID ? "xl:col-span-7" : "xl:col-span-4"}
        />

        {!((role === "ADMIN" || role === "HOMEROOM") && subjectId === ALL_SUBJECTS_ID) ? (
          <div className={cn("min-w-0", role === "PARENT" ? "xl:col-span-2" : "xl:col-span-3")}>
            <p className="text-[0.72rem] font-semibold text-slate-500">Викладач</p>
            <p className="mt-2 truncate text-[0.82rem] font-bold text-slate-800">{subject?.teacher || "Не призначено"}</p>
            <p className="mt-1 text-[0.7rem] text-slate-400">{academicYear} н. р.</p>
          </div>
        ) : null}
      </div>
    </Card>
  );
}

function SubjectCombobox({ subjects, value, onChange }) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [activeIndex, setActiveIndex] = useState(0);
  const containerRef = useRef(null);
  const optionRefs = useRef([]);
  const selected = subjects.find((item) => String(item.id) === String(value));
  const searchTerms = query.trim().toLocaleLowerCase("uk-UA").split(/\s+/).filter(Boolean);
  const filtered = subjects.filter((item) => searchTerms.every((term) => item.name.toLocaleLowerCase("uk-UA").includes(term)));

  useEffect(() => {
    if (open) optionRefs.current[activeIndex]?.scrollIntoView({ block: "nearest" });
  }, [activeIndex, open, query]);

  const openOptions = () => {
    setQuery("");
    setActiveIndex(0);
    setOpen(true);
  };
  const select = (item) => {
    onChange(String(item.id));
    setOpen(false);
    setQuery("");
  };

  return (
    <div ref={containerRef} className="min-w-0 xl:col-span-3">
      <label htmlFor="subjects-subject" className="mb-2 block text-[0.75rem] font-semibold text-slate-600">Предмет</label>
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
        <input
          id="subjects-subject"
          role="combobox"
          aria-autocomplete="list"
          aria-expanded={open}
          aria-controls="subjects-subject-options"
          aria-activedescendant={open && filtered[activeIndex] ? `subject-option-${filtered[activeIndex].id}` : undefined}
          autoComplete="off"
          value={open ? query : selected?.name || ""}
          placeholder={subjects.length ? "Пошук предмета" : "Немає предметів"}
          disabled={!subjects.length}
          onFocus={() => !open && openOptions()}
          onChange={(event) => { setQuery(event.target.value); setActiveIndex(0); setOpen(true); }}
          onBlur={(event) => {
            if (!containerRef.current?.contains(event.relatedTarget)) {
              setOpen(false);
              setQuery("");
            }
          }}
          onKeyDown={(event) => {
            if (event.key === "ArrowDown" || event.key === "ArrowUp") {
              event.preventDefault();
              if (!open) openOptions();
              else setActiveIndex((index) => Math.max(0, Math.min(filtered.length - 1, index + (event.key === "ArrowDown" ? 1 : -1))));
            } else if (event.key === "Enter" && open) {
              event.preventDefault();
              if (filtered[activeIndex]) select(filtered[activeIndex]);
            } else if (event.key === "Escape") {
              setOpen(false);
              setQuery("");
            }
          }}
          className="h-10 w-full rounded-md border border-slate-300 bg-white pl-9 pr-9 text-[0.82rem] font-medium text-slate-700 outline-hidden hover:border-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200 disabled:opacity-60"
        />
        <ChevronDown className="pointer-events-none absolute right-3 top-5 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
        {open ? (
          <ul id="subjects-subject-options" role="listbox" aria-label="Предмети" className="absolute z-40 mt-1 max-h-64 w-full overflow-y-auto rounded-md border border-slate-200 bg-white py-1 shadow-lg">
            {filtered.length ? filtered.map((item, index) => (
              <li key={item.id} id={`subject-option-${item.id}`} ref={(node) => { optionRefs.current[index] = node; }} role="option" aria-selected={String(item.id) === String(value)} onMouseDown={(event) => event.preventDefault()} onMouseEnter={() => setActiveIndex(index)} onClick={() => select(item)} className={cn("min-h-10 cursor-pointer break-words px-3 py-2 text-[0.82rem] font-medium", activeIndex === index ? "bg-slate-100 text-slate-900" : "text-slate-700 hover:bg-slate-50")}>
                {item.name}
              </li>
            )) : <li className="px-3 py-3 text-xs text-slate-500">За цим запитом предметів не знайдено</li>}
          </ul>
        ) : null}
      </div>
    </div>
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

function SummaryCards({ tab, stats, yearStats, period, forecast, allSubjects = false, gradeCount = 0, passFail = false }) {
  const selectedAttendanceLabel = /^\d{4}-\d{2}$/.test(period)
    ? "Пропущено уроків за місяць"
    : period.startsWith("SEMESTER_")
      ? "Пропущено уроків за семестр"
      : "Пропущено уроків за навчальний рік";
  const cards = tab === "attendance"
    ? [
      { label: selectedAttendanceLabel, value: stats.missed, icon: CalendarDays, tone: "slate" },
      ...(period === "YEAR" ? [] : [{ label: "Пропущено уроків за навчальний рік", value: yearStats.missed, icon: CircleAlert, tone: yearStats.missed ? "red" : "slate" }]),
      { label: "Відвідано уроків", value: `${stats.attended} із ${stats.countedLessons}`, icon: CheckCircle2, tone: "emerald" },
    ]
    : tab === "homework"
      ? [
        { label: "Призначено завдань", value: stats.activeHomework, icon: CheckCircle2, tone: "emerald" },
        { label: "Прострочені завдання", value: stats.overdueHomework, icon: CircleAlert, tone: stats.overdueHomework ? "red" : "slate" },
        { label: "Виконано завдань", value: stats.completedHomework, detail: `з них ${stats.completedLateHomework} із запізненням`, icon: ClipboardCheck, tone: "emerald" },
      ]
      : [
        allSubjects || period === "YEAR"
          ? { label: passFail ? "Система оцінювання" : allSubjects ? "Середній бал з усіх предметів" : "Середній бал", value: passFail ? "Залікова" : stats.average ?? "—", suffix: passFail || stats.average === null ? "" : "з 12", icon: BarChart3, tone: "slate" }
          : { label: passFail ? "Система оцінювання" : "Орієнтовна тематична", value: passFail ? "Залікова" : forecast.thematic ?? "—", suffix: passFail || forecast.thematic === null ? "" : "з 12", detail: passFail ? undefined : "За оцінками після останньої тематичної", icon: BarChart3, tone: "slate" },
        passFail ? { label: "Зараховано", value: stats.passed, icon: CheckCircle2, tone: "emerald" }
          : { label: "Динаміка успішності", value: formatDelta(stats.delta), detail: deltaDescription(stats.delta), icon: deltaIcon(stats.delta), tone: stats.delta > 0 ? "emerald" : stats.delta < 0 ? "red" : "slate" },
      ];

  return (
    <div className="mt-5 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      {cards.map((card) => <MetricCard key={card.label} {...card} />)}
      {tab === "grades" ? passFail ? <MetricCard label="Не зараховано" value={stats.notPassed} icon={CircleAlert} tone={stats.notPassed ? "red" : "slate"} /> : allSubjects
        ? <MetricCard label="Оцінок за період" value={gradeCount} icon={BookOpen} tone="slate" />
        : !passFail ? <GradeForecastCard forecast={forecast} /> : null : null}
    </div>
  );
}

function GradeForecastCard({ forecast }) {
  const items = [
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
      <div className="mt-3 grid grid-cols-2">
        {items.map((item, index) => (
          <div key={item.label} className={cn("min-w-0 px-1 text-center sm:px-2", index > 0 && "border-l border-slate-200")}>
            <p className="truncate text-[0.64rem] font-semibold text-slate-500">{item.label}</p>
            <p className="mt-2 text-[1.05rem] font-bold leading-none text-slate-900">{item.value ?? "—"}</p>
          </div>
        ))}
      </div>
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

function SubjectTabs({ activeTab, onChange, allSubjects = false }) {
  return (
    <div className="border-b border-slate-200 px-2 sm:px-4" role="tablist" aria-label={allSubjects ? "Навчальні дані з усіх предметів" : "Дані з предмета"}>
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

function useSortableRows(rows, dateColumn = "date") {
  const [sort, setSort] = useState({ column: dateColumn, direction: "desc" });
  const [page, setPage] = useState(1);
  const sortedRows = useMemo(() => sortProgressRows(rows, sort, { ...attendanceLabels, ...homeworkLabels }), [rows, sort]);
  const pagination = paginateProgressRows(sortedRows, page);
  const onSort = (column) => {
    setPage(1);
    setSort((current) => {
      if (current.column !== column) return { column, direction: "desc" };
      if (current.direction === "desc") return { column, direction: "asc" };
      return { column: dateColumn, direction: "desc" };
    });
  };
  return { visibleRows: pagination.rows, pagination, onPageChange: setPage, sort, onSort };
}

function ProgressPagination({ pagination, onPageChange }) {
  const { page, totalPages, total, start, end } = pagination;
  return (
    <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 pt-4 text-[0.75rem] text-slate-500">
      <p role="status">Показано {start}–{end} із {total}</p>
      {totalPages > 1 ? <nav aria-label="Сторінки навчальних даних" className="flex items-center gap-2">
        <button type="button" aria-label="Попередня сторінка" title="Попередня сторінка" disabled={page === 1} onClick={() => onPageChange(page - 1)} className="flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 text-slate-600 hover:bg-slate-50 disabled:cursor-default disabled:opacity-40"><ChevronLeft className="h-4 w-4" aria-hidden="true" /></button>
        <span className="min-w-12 text-center font-semibold tabular-nums">{page} / {totalPages}</span>
        <button type="button" aria-label="Наступна сторінка" title="Наступна сторінка" disabled={page === totalPages} onClick={() => onPageChange(page + 1)} className="flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 text-slate-600 hover:bg-slate-50 disabled:cursor-default disabled:opacity-40"><ChevronRight className="h-4 w-4" aria-hidden="true" /></button>
      </nav> : null}
    </div>
  );
}

function SortableHeader({ label, column, sort, onSort, className = "" }) {
  const direction = sort.column === column ? sort.direction : null;
  const Icon = direction === "desc" ? ChevronDown : direction === "asc" ? ChevronUp : ChevronsUpDown;
  const directionLabel = direction === "desc" ? "за спаданням" : direction === "asc" ? "за зростанням" : "без сортування";
  return (
    <th scope="col" aria-sort={direction === "desc" ? "descending" : direction === "asc" ? "ascending" : "none"} className={className}>
      <button type="button" onClick={() => onSort(column)} className="flex w-full cursor-pointer items-center gap-1.5 text-left transition-colors hover:text-slate-900 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-slate-400">
        <span>{label}</span><Icon className="h-3.5 w-3.5 shrink-0 text-slate-400" aria-hidden="true" />
        <span className="sr-only">Сортування {directionLabel}</span>
      </button>
    </th>
  );
}

function GradesPanel({ subject, stats, showSubject = false }) {
  const { visibleRows, pagination, onPageChange, sort, onSort } = useSortableRows(subject.grades);
  if (!subject.grades.length) return <EmptyPeriodData message="За вибраний період оцінок немає" />;
  return (
    <>
      <PanelHeading title="Оцінки за період" meta={`${countLabel(subject.grades.length, "результат", "результати", "результатів")}${subject.assessmentPolicy?.mode === "PASS_FAIL" ? "" : ` · середній бал ${stats.average ?? "—"}`}`} />
      <div className="mt-4 hidden overflow-x-auto rounded-md border border-slate-200 md:block">
        <table className="w-full min-w-[680px] table-fixed border-collapse text-left">
          <colgroup><col className="w-[14%]" />{showSubject ? <col className="w-[20%]" /> : null}<col className={showSubject ? "w-[24%]" : "w-[30%]"} /><col className="w-[12%]" /><col className={showSubject ? "w-[30%]" : "w-[44%]"} /></colgroup>
          <thead className="bg-slate-50"><tr className="border-b border-slate-200 text-[0.7rem] font-bold text-slate-500">
            <SortableHeader label="Дата" column="date" sort={sort} onSort={onSort} className="border-r border-slate-200 px-4 py-3" />
            {showSubject ? <th scope="col" className="border-r border-slate-200 px-4 py-3">Предмет</th> : null}
            <SortableHeader label="Тип роботи" column="workType" sort={sort} onSort={onSort} className="border-r border-slate-200 px-4 py-3" />
            <SortableHeader label="Оцінка" column="grade" sort={sort} onSort={onSort} className="border-r border-slate-200 px-4 py-3 text-center [&_button]:justify-center" />
            <th scope="col" className="px-4 py-3">Коментар учителя</th>
          </tr></thead>
          <tbody>{visibleRows.map((item) => <tr key={item.id} className="border-b border-slate-100 bg-white text-[0.8rem] transition-colors even:bg-slate-50/40 hover:bg-slate-50 last:border-0"><td className="border-r border-slate-100 px-4 py-3.5 font-medium text-slate-500">{formatDate(item.date)}</td>{showSubject ? <td className="border-r border-slate-100 px-4 py-3.5 font-semibold text-slate-800">{item.subjectName}</td> : null}<td className="border-r border-slate-100 px-4 py-3.5"><WorkTypeBadge type={item.workType} /></td><td className="border-r border-slate-100 px-4 py-3.5 text-center"><GradeBadge value={item.grade} /></td><td className="px-4 py-3.5 text-slate-500">{item.comment || "—"}</td></tr>)}</tbody>
        </table>
      </div>
      <div className="mt-4 space-y-3 md:hidden">
        {visibleRows.map((item) => <article key={item.id} className="rounded-md border border-slate-200 bg-slate-50/70 p-3"><div className="flex items-start justify-between gap-3"><div><p className="text-[0.7rem] font-medium text-slate-400">{formatDate(item.date)}</p>{showSubject ? <p className="mt-1 text-[0.82rem] font-bold text-slate-800">{item.subjectName}</p> : null}<div className="mt-1"><WorkTypeBadge type={item.workType} /></div></div><GradeBadge value={item.grade} /></div><p className="mt-3 text-[0.74rem] leading-5 text-slate-500">{item.comment || "Без коментаря"}</p></article>)}
      </div>
      <ProgressPagination pagination={pagination} onPageChange={onPageChange} />
    </>
  );
}

function AttendancePanel({ subject, showSubject = false }) {
  const { visibleRows, pagination, onPageChange, sort, onSort } = useSortableRows(subject.attendance);
  if (!subject.attendance.length) return <EmptyPeriodData message="За вибраний період записів відвідування немає" />;
  return (
    <>
      <PanelHeading title="Відвідування уроків" meta={`${countLabel(subject.attendance.length, "заняття", "заняття", "занять")} у періоді`} />
      <div className="mt-4 hidden overflow-x-auto rounded-md border border-slate-200 md:block">
        <table className="w-full min-w-[640px] table-fixed border-collapse text-left">
          <colgroup><col className="w-[16%]" />{showSubject ? <col className="w-[22%]" /> : null}<col className={showSubject ? "w-[38%]" : "w-[58%]"} /><col className={showSubject ? "w-[24%]" : "w-[26%]"} /></colgroup>
          <thead className="bg-slate-50"><tr className="border-b border-slate-200 text-[0.7rem] font-bold text-slate-500">
            <SortableHeader label="Дата" column="date" sort={sort} onSort={onSort} className="border-r border-slate-200 px-4 py-3" />
            {showSubject ? <th scope="col" className="border-r border-slate-200 px-4 py-3">Предмет</th> : null}
            <th scope="col" className="border-r border-slate-200 px-4 py-3">Тема уроку</th>
            <SortableHeader label="Статус відвідування" column="status" sort={sort} onSort={onSort} className="px-4 py-3 text-center [&_button]:justify-center" />
          </tr></thead>
          <tbody>{visibleRows.map((item) => <tr key={item.id} className="border-b border-slate-100 bg-white text-[0.8rem] transition-colors even:bg-slate-50/40 hover:bg-slate-50 last:border-0"><td className="border-r border-slate-100 px-4 py-3.5 font-medium text-slate-500">{formatDate(item.date)}</td>{showSubject ? <td className="border-r border-slate-100 px-4 py-3.5 font-semibold text-slate-800">{item.subjectName}</td> : null}<td className="border-r border-slate-100 px-4 py-3.5 font-semibold text-slate-800">{item.topic}</td><td className="px-4 py-3.5 text-center"><StatusBadge status={attendanceLabels[item.status]} /></td></tr>)}</tbody>
        </table>
      </div>
      <div className="mt-4 space-y-3 md:hidden">
        {visibleRows.map((item) => <article key={item.id} className="rounded-md border border-slate-200 bg-slate-50/70 p-3"><div className="flex items-start justify-between gap-3"><div className="min-w-0"><time className="text-[0.7rem] font-medium text-slate-400">{formatDate(item.date)}</time>{showSubject ? <p className="mt-1 text-[0.82rem] font-bold text-slate-800">{item.subjectName}</p> : null}<h3 className="mt-1 text-[0.82rem] font-bold leading-5 text-slate-800">{item.topic}</h3></div><StatusBadge status={attendanceLabels[item.status]} /></div></article>)}
      </div>
      <ProgressPagination pagination={pagination} onPageChange={onPageChange} />
    </>
  );
}

function HomeworkPanel({ subject, showSubject = false }) {
  const { visibleRows, pagination, onPageChange, sort, onSort } = useSortableRows(subject.homework, "assignedDate");
  if (!subject.homework.length) return <EmptyPeriodData message="За вибраний період домашніх завдань немає" />;
  return (
    <>
      <PanelHeading title="Домашні завдання" meta={`${countLabel(subject.homework.length, "завдання", "завдання", "завдань")} у періоді`} />
      <div className="mt-4 hidden overflow-x-auto rounded-md border border-slate-200 md:block">
        <table className="w-full min-w-[760px] table-fixed border-collapse text-left">
          <colgroup><col className="w-[14%]" /><col className="w-[14%]" />{showSubject ? <col className="w-[18%]" /> : null}<col className={showSubject ? "w-[30%]" : "w-[46%]"} /><col className={showSubject ? "w-[24%]" : "w-[26%]"} /></colgroup>
          <thead className="bg-slate-50"><tr className="border-b border-slate-200 text-[0.7rem] font-bold text-slate-500">
            <SortableHeader label="Дата видачі" column="assignedDate" sort={sort} onSort={onSort} className="border-r border-slate-200 px-4 py-3" />
            <SortableHeader label="Термін здачі" column="dueDate" sort={sort} onSort={onSort} className="border-r border-slate-200 px-4 py-3" />
            {showSubject ? <th scope="col" className="border-r border-slate-200 px-4 py-3">Предмет</th> : null}
            <th scope="col" className="border-r border-slate-200 px-4 py-3">Опис домашнього завдання</th>
            <SortableHeader label="Статус домашнього завдання" column="status" sort={sort} onSort={onSort} className="px-4 py-3 text-center [&_button]:justify-center" />
          </tr></thead>
          <tbody>{visibleRows.map((item) => <tr key={item.id} className="border-b border-slate-100 bg-white text-[0.8rem] transition-colors even:bg-slate-50/40 hover:bg-slate-50 last:border-0"><td className="border-r border-slate-100 px-4 py-3.5 font-medium text-slate-500">{formatDate(item.assignedDate)}</td><td className="border-r border-slate-100 px-4 py-3.5 font-medium text-slate-500">{formatDate(item.dueDate) || "—"}</td>{showSubject ? <td className="border-r border-slate-100 px-4 py-3.5 font-semibold text-slate-800">{item.subjectName}</td> : null}<td className="border-r border-slate-100 px-4 py-3.5 font-semibold text-slate-800">{item.title}</td><td className="px-4 py-3.5 text-center"><StatusBadge status={homeworkLabels[item.status]} /></td></tr>)}</tbody>
        </table>
      </div>
      <div className="mt-4 space-y-3 md:hidden">
        {visibleRows.map((item) => <article key={item.id} className="rounded-md border border-slate-200 bg-slate-50/70 p-3"><div className="flex items-start justify-between gap-3"><div className="min-w-0"><p className="text-[0.7rem] font-medium text-slate-400">{formatDate(item.assignedDate)} · до {formatDate(item.dueDate) || "без терміну"}</p>{showSubject ? <p className="mt-1 text-[0.82rem] font-bold text-slate-800">{item.subjectName}</p> : null}<h3 className="mt-1 text-[0.82rem] font-bold leading-5 text-slate-800">{item.title}</h3></div><StatusBadge status={homeworkLabels[item.status]} /></div></article>)}
      </div>
      <ProgressPagination pagination={pagination} onPageChange={onPageChange} />
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
  const tone = typeof value !== "number" ? "bg-slate-100 text-slate-700 ring-slate-200" : value >= 10
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
  return <LearnerNavigation role={config.homeRoute.toUpperCase()} activeRoute={config.subjectsRoute} compact={compact} />;
}

function LoadingState() {
  return <div className="flex min-h-72 items-center justify-center text-slate-400"><Loader2 className="h-6 w-6 animate-spin" aria-label="Завантаження" /></div>;
}

function EmptyChildren() {
  return <Card className="mt-5 rounded-lg px-5 py-12 text-center"><UserRound className="mx-auto h-8 w-8 text-slate-400" aria-hidden="true" /><h2 className="mt-4 text-[0.98rem] font-bold text-slate-900">Немає прив’язаних дітей</h2><p className="mt-2 text-[0.8rem] text-slate-500">Додайте дитину за кодом прив’язки у профілі.</p><a href="#parent/profile?addChild=1" className="mt-4 inline-flex text-[0.8rem] font-semibold text-slate-800 hover:underline">Додати дитину</a></Card>;
}

function formatDelta(delta) {
  if (delta === null) return "—";
  const formatted = new Intl.NumberFormat("uk-UA", { maximumFractionDigits: 1 }).format(Math.abs(delta));
  if (delta > 0) return `+${formatted}%`;
  if (delta < 0) return `−${formatted}%`;
  return "0%";
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

function formatDate(value) {
  if (!value) return "";
  const [year, month, day] = value.split("-");
  return `${day}.${month}.${year}`;
}
