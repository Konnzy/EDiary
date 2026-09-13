import { useEffect, useMemo, useState } from "react";
import {
  BookOpen,
  CalendarDays,
  ChevronDown,
  Clock3,
  GraduationCap,
  Home,
  Loader2,
  LogOut,
  Mail,
  MapPin,
  NotebookText,
  User,
  UserRound,
  Users,
} from "lucide-react";
import { AppFooter } from "@/components/AppFooter.jsx";
import { NotificationButton } from "@/components/NotificationButton.jsx";
import { Alert } from "@/components/ui/alert.jsx";
import { ApiError, authApi, parentApi, scheduleApi } from "@/lib/api.js";
import { cn } from "@/lib/utils.js";
import { findNextLesson, timeToMinutes } from "@/pages/Schedule/nextLesson.js";

const weekdays = [
  { value: "MONDAY", label: "Понеділок" },
  { value: "TUESDAY", label: "Вівторок" },
  { value: "WEDNESDAY", label: "Середа" },
  { value: "THURSDAY", label: "Четвер" },
  { value: "FRIDAY", label: "П’ятниця" },
];

const lessonTimes = [
  ["08:00", "08:45"],
  ["08:55", "09:40"],
  ["09:50", "10:35"],
  ["10:45", "11:30"],
  ["12:00", "12:45"],
  ["12:55", "13:40"],
  ["13:50", "14:35"],
  ["14:45", "15:30"],
];

const roleConfig = {
  STUDENT: {
    homeRoute: "student",
    scheduleRoute: "student/schedule",
    profileName: "Профіль учня",
    roleLabel: "Учень",
    subtitle: "Розклад занять вашого класу",
    navigation: [
      { label: "Головна", icon: Home, route: "student" },
      { label: "Розклад", icon: CalendarDays, route: "student/schedule" },
      { label: "Предмети", icon: BookOpen },
      { label: "Повідомлення", shortLabel: "Повідомл.", icon: Mail },
      { label: "Профіль", icon: User },
    ],
  },
  PARENT: {
    homeRoute: "parent",
    scheduleRoute: "parent/schedule",
    profileName: "Профіль батьків",
    roleLabel: "Батьки",
    subtitle: "Розклад занять обраної дитини",
    navigation: [
      { label: "Головна", icon: Home, route: "parent" },
      { label: "Розклад", icon: CalendarDays, route: "parent/schedule" },
      { label: "Предмети", icon: BookOpen },
      { label: "Повідомлення", shortLabel: "Повідомл.", icon: Mail },
      { label: "Профіль", icon: User },
    ],
  },
  TEACHER: {
    homeRoute: "teacher",
    scheduleRoute: "teacher/schedule",
    profileName: "Профіль учителя",
    roleLabel: "Вчитель",
    subtitle: "Ваші уроки за класами та предметами",
    navigation: [
      { label: "Головна", icon: Home, route: "teacher" },
      { label: "Розклад", icon: CalendarDays, route: "teacher/schedule" },
      { label: "Журнал", icon: NotebookText },
      { label: "Учні", icon: Users },
      { label: "Повідомлення", shortLabel: "Повідомл.", icon: Mail },
      { label: "Профіль", icon: User },
    ],
  },
};

export function UserSchedulePage({ role }) {
  const config = roleConfig[role];
  const [options, setOptions] = useState(null);
  const [academicYear, setAcademicYear] = useState("");
  const [week, setWeek] = useState(1);
  const [children, setChildren] = useState([]);
  const [studentId, setStudentId] = useState("");
  const [schedule, setSchedule] = useState(null);
  const [otherWeekSchedule, setOtherWeekSchedule] = useState(undefined);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 60_000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    let active = true;
    setLoading(true);

    Promise.all([
      scheduleApi.findOptions(),
      role === "PARENT" ? parentApi.findChildren() : Promise.resolve([]),
    ])
      .then(([nextOptions, nextChildren]) => {
        if (!active) return;
        const years = nextOptions.academicYears || [];
        const selectedYear = years.includes(nextOptions.currentAcademicYear)
          ? nextOptions.currentAcademicYear
          : "";
        setOptions(nextOptions);
        setAcademicYear(selectedYear);
        setWeek(nextOptions.currentWeek || 1);
        setChildren(nextChildren);
        setStudentId(nextChildren[0] ? String(nextChildren[0].studentId) : "");
        if (!selectedYear || (role === "PARENT" && !nextChildren.length)) {
          setLoading(false);
        }
      })
      .catch((requestError) => {
        if (!active) return;
        setError(errorMessage(requestError, "Не вдалося завантажити параметри розкладу."));
        setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [role]);

  useEffect(() => {
    if (!academicYear || (role === "PARENT" && !studentId)) return undefined;

    let active = true;
    setLoading(true);
    setError("");
    const period = { week };
    const request = role === "STUDENT"
      ? scheduleApi.findForStudent(period)
      : role === "TEACHER"
        ? scheduleApi.findForTeacher(period)
        : parentApi.findSchedule(Number(studentId), period);

    request
      .then((data) => {
        if (active) setSchedule(data);
      })
      .catch((requestError) => {
        if (active) {
          setSchedule(null);
          setError(errorMessage(requestError, "Не вдалося завантажити розклад."));
        }
      })
      .finally(() => active && setLoading(false));

    return () => {
      active = false;
    };
  }, [academicYear, role, studentId, week]);

  useEffect(() => {
    if (!academicYear || (role === "PARENT" && !studentId)) return undefined;

    let active = true;
    setOtherWeekSchedule(undefined);
    const period = { week: 3 - week };
    const request = role === "STUDENT"
      ? scheduleApi.findForStudent(period)
      : role === "TEACHER"
        ? scheduleApi.findForTeacher(period)
        : parentApi.findSchedule(Number(studentId), period);

    request
      .then((data) => {
        if (active) setOtherWeekSchedule(data);
      })
      .catch(() => {
        if (active) setOtherWeekSchedule(null);
      });

    return () => { active = false; };
  }, [academicYear, role, studentId, week]);

  const groupedLessons = useMemo(() => Object.fromEntries(
    weekdays.map((day) => [
      day.value,
      (schedule?.lessons || [])
        .filter((lesson) => lesson.weekday === day.value)
        .sort((left, right) => left.lessonNumber - right.lessonNumber),
    ]),
  ), [schedule]);

  const selectedChild = children.find((child) => String(child.studentId) === studentId);
  const hasNoClass = schedule && role !== "TEACHER" && !schedule.className;
  const title = role !== "TEACHER" && schedule?.className
    ? `Розклад ${schedule.className} класу`
    : "Розклад";
  const subtitle = role === "PARENT" && selectedChild
    ? `Розклад занять: ${selectedChild.firstName} ${selectedChild.lastName}`
    : config.subtitle;
  const nextLesson = useMemo(() => {
    if (!schedule || academicYear !== options?.currentAcademicYear) return null;
    if (week === options.currentWeek) {
      const thisWeek = findNextLesson({ [week]: schedule }, week, now);
      if (thisWeek) return thisWeek;
    }
    if (!otherWeekSchedule) return null;
    return findNextLesson({ [week]: schedule, [3 - week]: otherWeekSchedule }, options.currentWeek, now, true);
  }, [academicYear, now, options, schedule, otherWeekSchedule, week]);
  const teacherNextLessonLabel = !academicYear
    ? "Навчальний рік не налаштовано"
    : error || (!nextLesson && otherWeekSchedule === null)
      ? "Не вдалося визначити наступний урок"
      : loading || (!nextLesson && otherWeekSchedule === undefined)
        ? "Завантаження розкладу..."
        : nextLesson
          ? `${weekdays[nextLesson.dayIndex].label} · ${nextLesson.lesson.lessonNumber}-й урок · ${nextLesson.lesson.startTime}–${nextLesson.lesson.endTime}`
          : "Запланованих уроків немає";

  return (
    <div className="flex min-h-screen flex-col bg-slate-50 text-slate-800">
      <TopBar config={config} />
      <Navigation config={config} compact />
      <div className="flex flex-1">
        <Navigation config={config} />
        <div className="flex min-w-0 flex-1 flex-col border-slate-200/80 lg:border-l">
          <main className="min-w-0 flex-1 px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
            <div className="mx-auto w-full max-w-[1500px]">
              <header className="mb-6">
                <h1 className="text-[1.55rem] font-bold leading-tight text-slate-950 sm:text-[1.85rem]">{title}</h1>
                <p className="mt-2 text-[0.9rem] text-slate-500">{subtitle}</p>
              </header>

              <ScheduleFilters
                role={role}
                academicYear={academicYear}
                week={week}
                children={children}
                studentId={studentId}
                nextLessonLabel={teacherNextLessonLabel}
                onWeekChange={(value) => {
                  if (value === week) return;
                  setSchedule(null);
                  setOtherWeekSchedule(undefined);
                  setWeek(value);
                }}
                onStudentChange={(value) => {
                  if (value === "__add_child__") {
                    window.location.hash = "parent";
                    return;
                  }
                  if (value === studentId) return;
                  setSchedule(null);
                  setOtherWeekSchedule(undefined);
                  setStudentId(value);
                }}
              />

              {error ? <div className="mt-5"><Alert>{error}</Alert></div> : null}
              {loading ? <LoadingState /> : null}
              {!loading && !error && !options?.academicYears?.length ? (
                <EmptyState title="Навчальний рік не налаштовано" description="Зверніться до адміністратора навчального закладу." />
              ) : null}
              {!loading && !error && role === "PARENT" && !children.length ? (
                <EmptyState title="Немає прив’язаних дітей" description="Додайте дитину за кодом прив’язки на головній сторінці." />
              ) : null}
              {!loading && !error && hasNoClass ? (
                <EmptyState title="Клас ще не призначено" description="Після додавання учня до класу тут з’явиться його розклад." />
              ) : null}
              {!loading && !error && schedule && !hasNoClass ? (
                <ScheduleGrid
                  groupedLessons={groupedLessons}
                  role={role}
                  academicYear={academicYear}
                  week={week}
                  options={options}
                  now={now}
                  nextLesson={nextLesson}
                />
              ) : null}
            </div>
          </main>
          <AppFooter />
        </div>
      </div>
    </div>
  );
}

function ScheduleFilters({ role, academicYear, week, children, studentId, nextLessonLabel, onWeekChange, onStudentChange }) {
  return (
    <section className={cn(
      "grid gap-4 rounded-lg border border-slate-200 bg-white p-4 shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)]",
      role === "TEACHER" && "md:grid-cols-2 xl:grid-cols-[minmax(190px,1fr)_minmax(190px,1fr)_310px] xl:items-end",
      role === "PARENT" && "max-w-[760px] sm:grid-cols-[minmax(0,1fr)_310px] sm:items-end",
      role === "STUDENT" && "max-w-[380px]",
    )}>
      {role === "PARENT" ? (
        <SelectField label="Дитина" value={studentId} onChange={onStudentChange}>
          {!studentId ? <option value="">Оберіть дитину</option> : null}
          {children.map((child) => (
            <option key={child.studentId} value={child.studentId}>
              {child.firstName} {child.lastName}{child.className ? ` · ${child.className}` : ""}
            </option>
          ))}
          <option value="__add_child__">+ Додати дитину</option>
        </SelectField>
      ) : role === "TEACHER" ? (
        <div className="min-w-0">
          <p className="text-[0.75rem] font-semibold text-slate-500">Наступний урок</p>
          <p className="mt-2 flex min-h-10 items-center text-[0.82rem] font-semibold leading-5 text-slate-800">
            {nextLessonLabel}
          </p>
        </div>
      ) : null}

      {role === "TEACHER" ? <ReadOnlyField label="Навчальний рік" value={academicYear ? `${academicYear} н. р.` : "Не налаштовано"} /> : null}

      <div>
        <span className="mb-2 block text-[0.75rem] font-semibold text-slate-600">Навчальний тиждень</span>
        <div className="grid h-10 grid-cols-2 rounded-md border border-slate-200 bg-slate-100 p-1" role="group" aria-label="Вибір навчального тижня">
          {[1, 2].map((value) => (
            <button
              key={value}
              type="button"
              onClick={() => onWeekChange(value)}
              className={cn(
                "rounded text-[0.76rem] font-semibold transition-colors",
                week === value ? "bg-white text-slate-900 shadow-sm" : "text-slate-500 hover:text-slate-800",
              )}
              aria-pressed={week === value}
            >
              {value === 1 ? "Перший тиждень" : "Другий тиждень"}
            </button>
          ))}
        </div>
      </div>
    </section>
  );
}

function ReadOnlyField({ label, value }) {
  return (
    <div>
      <span className="mb-2 block text-[0.75rem] font-semibold text-slate-600">{label}</span>
      <p className="flex h-10 items-center rounded-md border border-slate-200 bg-slate-50 px-3 text-[0.8rem] font-semibold text-slate-700">
        {value}
      </p>
    </div>
  );
}

function SelectField({ label, value, onChange, children }) {
  return (
    <label className="block">
      <span className="mb-2 block text-[0.75rem] font-semibold text-slate-600">{label}</span>
      <span className="relative block">
        <select value={value} onChange={(event) => onChange(event.target.value)} className="h-10 w-full appearance-none rounded-md border border-slate-300 bg-white px-3 pr-9 text-[0.8rem] font-semibold text-slate-700 outline-none hover:border-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200">
          {children}
        </select>
        <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
      </span>
    </label>
  );
}

function ScheduleGrid({ groupedLessons, role, academicYear, week, options, now, nextLesson }) {
  if (role === "TEACHER") {
    return (
      <TeacherScheduleGrid
        groupedLessons={groupedLessons}
        academicYear={academicYear}
        week={week}
        options={options}
        now={now}
        nextLesson={nextLesson}
      />
    );
  }

  return (
    <section className="mt-7 grid gap-4 md:grid-cols-2 xl:grid-cols-5" aria-label="Розклад на тиждень">
      {weekdays.map((day) => {
        const lessons = groupedLessons[day.value];
        const isToday = academicYear === options?.currentAcademicYear
          && week === options?.currentWeek
          && weekdays[now.getDay() - 1]?.value === day.value;
        return (
          <div key={day.value} className="min-w-0">
            <div className={cn("mb-3 flex items-center justify-between border-b-2 px-1 pb-3", isToday ? "border-emerald-500" : "border-slate-800")}>
              <div className="flex min-w-0 items-center gap-2">
                <h2 className="text-[0.88rem] font-bold text-slate-900">{day.label}</h2>
                {isToday ? <span className="rounded bg-emerald-100 px-1.5 py-0.5 text-[0.65rem] font-bold text-emerald-700">Сьогодні</span> : null}
              </div>
              <span className="text-[0.7rem] font-medium text-slate-400">{lessonCount(lessons.length)}</span>
            </div>
            <div className="space-y-3">
              {lessons.length ? lessons.map((lesson) => (
                <LessonCard
                  key={lesson.id}
                  lesson={lesson}
                  role={role}
                  current={isToday && isCurrentLesson(lesson, now)}
                  next={nextLesson?.week === week && nextLesson.lesson.id === lesson.id}
                />
              )) : (
                <div className="flex min-h-32 items-center justify-center rounded-lg border border-dashed border-slate-300 px-4 text-center text-[0.78rem] text-slate-400">
                  Уроків немає
                </div>
              )}
            </div>
          </div>
        );
      })}
    </section>
  );
}

function TeacherScheduleGrid({ groupedLessons, academicYear, week, options, now, nextLesson }) {
  return (
    <section className="mt-7 grid gap-4 md:grid-cols-2 xl:grid-cols-5" aria-label="Розклад на тиждень">
      {weekdays.map((day) => {
        const lessons = groupedLessons[day.value];
        const lessonsByNumber = new Map(lessons.map((lesson) => [lesson.lessonNumber, lesson]));
        const isToday = academicYear === options?.currentAcademicYear
          && week === options?.currentWeek
          && weekdays[now.getDay() - 1]?.value === day.value;

        return (
          <div key={day.value} className="grid min-w-0 grid-rows-[auto_repeat(8,minmax(0,1fr))] gap-y-3 md:row-span-9 md:grid-rows-subgrid">
            <div className={cn("flex items-center justify-between border-b-2 px-1 pb-3", isToday ? "border-emerald-500" : "border-slate-800")}>
              <div className="flex min-w-0 items-center gap-2">
                <h2 className="text-[0.88rem] font-bold text-slate-900">{day.label}</h2>
                {isToday ? <span className="rounded bg-emerald-100 px-1.5 py-0.5 text-[0.65rem] font-bold text-emerald-700">Сьогодні</span> : null}
              </div>
              <span className="text-[0.7rem] font-medium text-slate-400">{lessonCount(lessons.length)}</span>
            </div>
            {lessonTimes.map(([startTime, endTime], index) => {
              const lessonNumber = index + 1;
              const lesson = lessonsByNumber.get(lessonNumber);
              return lesson ? (
                <LessonCard
                  key={lessonNumber}
                  lesson={lesson}
                  role="TEACHER"
                  current={isToday && isCurrentLesson(lesson, now)}
                  next={nextLesson?.week === week && nextLesson.lesson.id === lesson.id}
                />
              ) : (
                <TeacherEmptyLessonCard
                  key={lessonNumber}
                  lessonNumber={lessonNumber}
                  startTime={startTime}
                  endTime={endTime}
                />
              );
            })}
          </div>
        );
      })}
    </section>
  );
}

function TeacherEmptyLessonCard({ lessonNumber, startTime, endTime }) {
  return (
    <div className="flex min-h-36 flex-col rounded-lg border border-dashed border-slate-200 bg-slate-50/50 p-4 text-slate-400" aria-label={`${lessonNumber}-й урок, ${startTime}–${endTime}: немає уроку`}>
      <div className="flex items-center gap-1.5 text-[0.72rem] font-semibold">
        <Clock3 className="h-3.5 w-3.5" aria-hidden="true" />
        <span>{startTime}–{endTime}</span>
      </div>
      <p className="mt-3 text-[0.68rem] font-bold uppercase">{lessonNumber}-й урок</p>
      <p className="mt-1 text-[0.82rem]">Немає уроку</p>
    </div>
  );
}

function LessonCard({ lesson, role, current, next }) {
  const details = role === "TEACHER" ? lesson.className : lesson.teacher;
  return (
    <article className={cn(
      "rounded-lg border bg-white p-4 shadow-[0_10px_28px_-26px_rgba(15,23,42,0.5)]",
      current ? "border-emerald-400 ring-2 ring-emerald-100" : next ? "border-sky-300 bg-sky-50/40 ring-1 ring-sky-100" : "border-slate-200",
    )}>
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-1.5 text-[0.72rem] font-semibold text-slate-500">
          <Clock3 className="h-3.5 w-3.5" aria-hidden="true" />
          <time>{lesson.startTime}–{lesson.endTime}</time>
        </div>
        {current ? <span className="rounded bg-emerald-100 px-2 py-1 text-[0.64rem] font-bold text-emerald-700">Зараз триває</span> : null}
        {!current && next ? <span className="rounded bg-sky-100 px-2 py-1 text-[0.64rem] font-bold text-sky-700">Наступний</span> : null}
      </div>
      <p className="mt-3 text-[0.68rem] font-bold uppercase text-slate-400">{lesson.lessonNumber}-й урок</p>
      <h3 className="mt-1 break-words text-[0.9rem] font-bold leading-5 text-slate-900">{lesson.subject}</h3>
      <p className="mt-3 flex items-start gap-2 text-[0.74rem] leading-5 text-slate-500">
        {role === "TEACHER" ? <GraduationCap className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" /> : <UserRound className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />}
        <span>{details}</span>
      </p>
      <p className="mt-1.5 flex items-center gap-2 text-[0.74rem] text-slate-500">
        <MapPin className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
        <span>{lesson.room ? `Кабінет ${lesson.room}` : "Кабінет не вказано"}</span>
      </p>
    </article>
  );
}

function TopBar({ config }) {
  const logout = async () => {
    try {
      await authApi.logout();
    } catch {
      // The session may already be expired.
    }
    window.location.hash = "login";
  };

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-slate-200/80 bg-white px-4 sm:px-5 lg:px-6">
      <a href={`#${config.homeRoute}`} className="inline-flex min-w-0 items-center gap-2 text-slate-900" aria-label="Електронний щоденник - головна">
        <GraduationCap className="h-7 w-7 shrink-0 sm:h-8 sm:w-8" strokeWidth={2.4} aria-hidden="true" />
        <span className="max-w-[150px] truncate text-[0.92rem] font-bold min-[420px]:max-w-none sm:text-[1.05rem]">Електронний щоденник</span>
      </a>
      <div className="flex items-center gap-1.5 sm:gap-4">
        <NotificationButton />
        <div className="hidden items-center gap-3 bg-slate-50 px-3 py-1.5 sm:flex">
          <div className="text-right leading-tight"><div className="text-[0.78rem] font-bold">{config.profileName}</div><div className="text-[0.7rem] text-slate-500">{config.roleLabel}</div></div>
          <UserRound className="h-8 w-8" strokeWidth={1.8} aria-hidden="true" />
        </div>
        <button type="button" onClick={logout} className="inline-flex h-10 items-center gap-2 rounded-md px-3 text-[0.82rem] font-semibold text-slate-500 hover:bg-slate-100 hover:text-slate-900">
          <LogOut className="h-4 w-4" aria-hidden="true" /><span className="hidden sm:inline">Вийти</span>
        </button>
      </div>
    </header>
  );
}

function Navigation({ config, compact = false }) {
  const content = config.navigation.map((item) => {
    const Icon = item.icon;
    const active = item.route === config.scheduleRoute;
    const className = cn(
      compact ? "inline-flex h-10 min-w-0 items-center justify-center gap-1 rounded-md px-1" : "flex h-11 w-full items-center gap-3 rounded-md px-3 text-left",
      "text-[0.78rem] font-semibold transition-colors",
      active ? "bg-slate-100 text-slate-900" : "text-slate-500 hover:bg-slate-50 hover:text-slate-800",
      !item.route && "cursor-default hover:bg-transparent hover:text-slate-500",
    );
    const label = <><Icon className={compact ? "h-4 w-4 shrink-0" : "h-5 w-5 shrink-0"} aria-hidden="true" />{compact ? <span className="hidden truncate min-[540px]:inline">{item.shortLabel || item.label}</span> : item.label}</>;
    return item.route
      ? <a key={item.label} href={`#${item.route}`} className={className} aria-current={active ? "page" : undefined} aria-label={compact ? item.label : undefined}>{label}</a>
      : <button key={item.label} type="button" disabled className={className} title={`${item.label}: розділ у розробці`}>{label}</button>;
  });

  if (compact) {
    return <nav className="sticky top-16 z-20 border-b border-slate-200/80 bg-white px-3 py-2 lg:hidden" aria-label="Основна навігація"><div className="grid gap-1" style={{ gridTemplateColumns: `repeat(${config.navigation.length}, minmax(0, 1fr))` }}>{content}</div></nav>;
  }
  return <aside className="hidden w-[210px] shrink-0 bg-white px-3 py-4 lg:block"><nav className="space-y-2" aria-label="Основна навігація">{content}</nav></aside>;
}

function LoadingState() {
  return <div className="flex min-h-72 items-center justify-center text-slate-400"><Loader2 className="h-6 w-6 animate-spin" aria-label="Завантаження розкладу" /></div>;
}

function EmptyState({ title, description }) {
  return <section className="mt-6 rounded-lg border border-slate-200 bg-white px-5 py-12 text-center"><CalendarDays className="mx-auto h-8 w-8 text-slate-300" aria-hidden="true" /><h2 className="mt-4 text-base font-bold text-slate-900">{title}</h2><p className="mt-2 text-[0.8rem] text-slate-500">{description}</p></section>;
}

function isCurrentLesson(lesson, now) {
  const minutes = now.getHours() * 60 + now.getMinutes();
  return minutes >= timeToMinutes(lesson.startTime) && minutes < timeToMinutes(lesson.endTime);
}

function lessonCount(count) {
  if (count === 1) return "1 урок";
  if (count >= 2 && count <= 4) return `${count} уроки`;
  return `${count} уроків`;
}

function errorMessage(error, fallback) {
  return error instanceof ApiError ? error.message : fallback;
}
