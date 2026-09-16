import { useEffect, useRef, useState } from "react";
import {
  ArrowLeftRight,
  ChevronDown,
  Clock3,
  Copy,
  EllipsisVertical,
  MapPin,
  Pencil,
  Plus,
  Save,
  Trash2,
  UserRound,
  X,
} from "lucide-react";
import { Alert } from "@/components/ui/alert.jsx";
import { Button } from "@/components/ui/button.jsx";
import { Card } from "@/components/ui/card.jsx";
import { Input } from "@/components/ui/input.jsx";
import { ApiError, adminClassesApi, adminScheduleApi, adminSubjectsApi } from "@/lib/api.js";
import { cn } from "@/lib/utils.js";
import { AdminLayout } from "@/pages/AdminHome/AdminLayout.jsx";
import { COPY_MODES, copySchedule, countCopiedLessons, countLessons } from "./scheduleCopy.js";

const weekdays = [
  { id: "MONDAY", label: "Понеділок" },
  { id: "TUESDAY", label: "Вівторок" },
  { id: "WEDNESDAY", label: "Середа" },
  { id: "THURSDAY", label: "Четвер" },
  { id: "FRIDAY", label: "П’ятниця" },
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

const emptyLesson = {
  subject: "",
  teacher: "",
  room: "",
};

function getCurrentAcademicYear() {
  const today = new Date();
  const startYear = today.getMonth() >= 8 ? today.getFullYear() : today.getFullYear() - 1;
  return `${startYear}/${startYear + 1}`;
}

export function AdminSchedulePage() {
  const [classes, setClasses] = useState([]);
  const [selectedClassId, setSelectedClassId] = useState("");
  const [academicYears, setAcademicYears] = useState([]);
  const [selectedYear, setSelectedYear] = useState("");
  const [week, setWeek] = useState(1);
  const [schedule, setSchedule] = useState(createEmptySchedule);
  const [lessonDialog, setLessonDialog] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [yearDialogOpen, setYearDialogOpen] = useState(false);
  const [copyDialogOpen, setCopyDialogOpen] = useState(false);
  const [openMenu, setOpenMenu] = useState("");
  const [loadingClasses, setLoadingClasses] = useState(true);
  const [loadingSchedule, setLoadingSchedule] = useState(false);
  const [savingSchedule, setSavingSchedule] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const pendingCopiedSchedule = useRef(null);

  useEffect(() => {
    let active = true;
    adminScheduleApi.findAcademicYears()
      .then((years) => {
        if (!active) return;
        const availableYears = years.length ? years : ["2026/2027"];
        setAcademicYears(availableYears);
        const currentAcademicYear = getCurrentAcademicYear();
        setSelectedYear(availableYears.includes(currentAcademicYear) ? currentAcademicYear : availableYears.at(-1));
      })
      .catch((requestError) => {
        if (!active) return;
        setAcademicYears(["2026/2027"]);
        setError(requestError instanceof ApiError ? requestError.message : "Не вдалося завантажити навчальні роки.");
      });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!selectedYear) return undefined;
    let active = true;
    setLoadingClasses(true);
    adminClassesApi.findAll({ academicYear: selectedYear })
      .then((items) => {
        if (!active) return;
        setClasses(items);
        setSelectedClassId((current) => items.some((item) => String(item.id) === current)
          ? current
          : (items[0] ? String(items[0].id) : ""));
      })
      .catch(() => {
        if (!active) return;
        setClasses([]);
        setSelectedClassId("");
      })
      .finally(() => active && setLoadingClasses(false));
    return () => { active = false; };
  }, [selectedYear]);

  useEffect(() => {
    if (!selectedClassId || !selectedYear || loadingClasses ||
        !classes.some((schoolClass) => String(schoolClass.id) === selectedClassId && schoolClass.academicYear === selectedYear)) {
      setSchedule(createEmptySchedule());
      setDirty(false);
      return undefined;
    }
    const pending = pendingCopiedSchedule.current;
    if (pending && pending.week === week && pending.classId === selectedClassId && pending.academicYear === selectedYear) {
      pendingCopiedSchedule.current = null;
      setSchedule(pending.schedule);
      setDirty(true);
      setLoadingSchedule(false);
      setError("");
      setSuccess("Розклад скопійовано в чернетку. Перевірте зміни та збережіть їх.");
      return undefined;
    }
    let active = true;
    setLoadingSchedule(true);
    setError("");
    setSuccess("");
    adminScheduleApi.find({ classId: selectedClassId, academicYear: selectedYear, week })
      .then((response) => {
        if (!active) return;
        setSchedule(scheduleFromResponse(response));
        setDirty(false);
      })
      .catch((requestError) => {
        if (!active) return;
        setSchedule(createEmptySchedule());
        setDirty(false);
        setError(requestError instanceof ApiError ? requestError.message : "Не вдалося завантажити розклад.");
      })
      .finally(() => active && setLoadingSchedule(false));
    return () => { active = false; };
  }, [classes, loadingClasses, selectedClassId, selectedYear, week]);

  const updateSchedule = (updater) => {
    setSchedule(updater);
    setDirty(true);
    setError("");
    setSuccess("");
  };

  const saveLesson = (lesson) => {
    updateSchedule((current) => {
      const dayLessons = current[lessonDialog.dayId] || [];
      const nextLessons = lessonDialog.lesson
        ? dayLessons.map((item) => item.id === lessonDialog.lesson.id ? { ...lesson, id: item.id } : item)
        : [...dayLessons, { ...lesson, id: crypto.randomUUID() }];
      return {
        ...current,
        [lessonDialog.dayId]: nextLessons.sort((first, second) => first.lessonNumber - second.lessonNumber),
      };
    });
    setLessonDialog(null);
  };

  const deleteLesson = () => {
    updateSchedule((current) => ({
      ...current,
      [deleteTarget.dayId]: current[deleteTarget.dayId].filter((lesson) => lesson.id !== deleteTarget.lesson.id),
    }));
    setDeleteTarget(null);
  };

  const changeAcademicYear = (value) => {
    if (value === "__add_year__") {
      setYearDialogOpen(true);
      return;
    }
    setSelectedClassId("");
    setSelectedYear(value);
  };

  const addAcademicYear = async (year) => {
    const response = await adminScheduleApi.createAcademicYear(year);
    setAcademicYears((current) => [...current, response.academicYear].sort());
    setSelectedClassId("");
    setSelectedYear(response.academicYear);
    setYearDialogOpen(false);
  };

  const saveSchedule = async () => {
    if (!selectedClassId || !selectedYear || savingSchedule) return;
    setSavingSchedule(true);
    setError("");
    setSuccess("");
    try {
      const lessons = weekdays.flatMap((day) => (schedule[day.id] || []).map((lesson) => ({
        weekday: day.id,
        lessonNumber: lesson.lessonNumber,
        subjectId: Number(lesson.subjectId),
        teacherId: Number(lesson.teacherId),
        room: lesson.room,
      })));
      const response = await adminScheduleApi.save({
        classId: selectedClassId,
        academicYear: selectedYear,
        week,
        lessons,
      });
      setSchedule(scheduleFromResponse(response));
      setDirty(false);
      setSuccess("Зміни в розкладі збережено.");
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : "Не вдалося зберегти розклад.");
    } finally {
      setSavingSchedule(false);
    }
  };

  const applyCopiedSchedule = (targetWeek, copiedSchedule) => {
    setCopyDialogOpen(false);
    setOpenMenu("");
    if (targetWeek === week) {
      setSchedule(copiedSchedule);
      setDirty(true);
      setError("");
      setSuccess("Розклад скопійовано в чернетку. Перевірте зміни та збережіть їх.");
      return;
    }
    pendingCopiedSchedule.current = {
      week: targetWeek,
      classId: selectedClassId,
      academicYear: selectedYear,
      schedule: copiedSchedule,
    };
    setWeek(targetWeek);
  };

  return (
    <AdminLayout
      activeItem="Розклад"
      title="Розклад"
      subtitle="Перегляд та редагування розкладу занять для класів"
    >
      <Card className="rounded-lg p-4 sm:p-5">
        <div className="grid gap-4 lg:grid-cols-[minmax(180px,1fr)_minmax(180px,1fr)_auto_auto_auto] lg:items-end">
          <SelectField label="Клас">
            <select
              value={selectedClassId}
              onChange={(event) => setSelectedClassId(event.target.value)}
              disabled={loadingClasses}
              className="h-10 w-full appearance-none rounded-md border border-slate-300 bg-white px-3 pr-9 text-[0.82rem] font-medium text-slate-700 outline-none hover:border-slate-400 focus:border-slate-500 disabled:opacity-60"
            >
              {loadingClasses ? <option value="">Завантаження...</option> : null}
              {!loadingClasses && classes.length === 0 ? <option value="">Класи не знайдено</option> : null}
              {classes.map((schoolClass) => (
                <option key={schoolClass.id} value={schoolClass.id}>{schoolClass.number}-{schoolClass.letter}</option>
              ))}
            </select>
          </SelectField>

          <SelectField label="Навчальний рік">
            <select
              value={selectedYear}
              onChange={(event) => changeAcademicYear(event.target.value)}
              className="h-10 w-full appearance-none rounded-md border border-slate-300 bg-white px-3 pr-9 text-[0.82rem] font-medium text-slate-700 outline-none hover:border-slate-400 focus:border-slate-500"
            >
              {academicYears.map((year) => <option key={year} value={year}>{year} н. р.</option>)}
              <option value="__add_year__">+ Додати навчальний рік</option>
            </select>
          </SelectField>

          <div className="grid h-10 grid-cols-2 rounded-md border border-slate-200 bg-slate-100 p-1" role="group" aria-label="Вибір навчального тижня">
            {[1, 2].map((item) => (
              <button
                key={item}
                type="button"
                onClick={() => setWeek(item)}
                className={cn(
                  "min-w-[140px] rounded px-4 text-[0.78rem] font-semibold transition-colors",
                  week === item ? "bg-white text-slate-900 shadow-sm" : "text-slate-500 hover:text-slate-800",
                )}
              >
                {item === 1 ? "Перший тиждень" : "Другий тиждень"}
              </button>
            ))}
          </div>

          <Button
            type="button"
            variant="ghost"
            onClick={() => setCopyDialogOpen(true)}
            disabled={!selectedClassId || loadingSchedule || savingSchedule}
            className="h-10 rounded-md border border-slate-300 bg-white px-4 text-xs"
          >
            <Copy className="h-4 w-4" aria-hidden="true" />
            Скопіювати розклад
          </Button>

          <Button
            type="button"
            onClick={saveSchedule}
            disabled={!selectedClassId || loadingSchedule || savingSchedule || !dirty}
            className="h-10 rounded-md px-4 text-xs"
          >
            <Save className="h-4 w-4" aria-hidden="true" />
            {savingSchedule ? "Збереження..." : "Зберегти зміни"}
          </Button>
        </div>
      </Card>

      {error ? <Alert className="mt-4">{error}</Alert> : null}
      {success ? (
        <div role="status" className="mt-4 rounded-md border border-emerald-300 bg-emerald-50 px-4 py-3 text-[0.82rem] font-medium text-emerald-700">
          {success}
        </div>
      ) : null}

      <div className={cn("mt-5 overflow-x-auto pb-3 transition-opacity", loadingSchedule && "pointer-events-none opacity-50")} aria-busy={loadingSchedule}>
        <div className="grid min-w-[1180px] grid-cols-5 gap-4">
          {weekdays.map((day) => (
            <DayColumn
              key={day.id}
              day={day}
              lessons={schedule[day.id] || []}
              openMenu={openMenu}
              onToggleMenu={(menuId) => setOpenMenu((current) => current === menuId ? "" : menuId)}
              onAdd={(lessonNumber) => setLessonDialog({ dayId: day.id, lessonNumber, lesson: null })}
              onEdit={(lesson) => { setOpenMenu(""); setLessonDialog({ dayId: day.id, lessonNumber: lesson.lessonNumber, lesson }); }}
              onDelete={(lesson) => { setOpenMenu(""); setDeleteTarget({ dayId: day.id, lesson }); }}
            />
          ))}
        </div>
      </div>

      {lessonDialog ? (
        <LessonDialog
          dialog={lessonDialog}
          onClose={() => setLessonDialog(null)}
          onSave={saveLesson}
        />
      ) : null}
      {deleteTarget ? <DeleteLessonDialog target={deleteTarget} onClose={() => setDeleteTarget(null)} onConfirm={deleteLesson} /> : null}
      {yearDialogOpen ? <AcademicYearDialog years={academicYears} onClose={() => setYearDialogOpen(false)} onCreate={addAcademicYear} /> : null}
      {copyDialogOpen ? (
        <CopyScheduleDialog
          currentWeek={week}
          currentSchedule={schedule}
          classId={selectedClassId}
          academicYear={selectedYear}
          onClose={() => setCopyDialogOpen(false)}
          onApply={applyCopiedSchedule}
        />
      ) : null}
    </AdminLayout>
  );
}

function CopyScheduleDialog({ currentWeek, currentSchedule, classId, academicYear, onClose, onApply }) {
  const [sourceWeek, setSourceWeek] = useState(currentWeek);
  const [targetWeek, setTargetWeek] = useState(currentWeek === 1 ? 2 : 1);
  const [mode, setMode] = useState(COPY_MODES.EMPTY_SLOTS);
  const [schedules, setSchedules] = useState({ [currentWeek]: currentSchedule });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    const otherWeek = currentWeek === 1 ? 2 : 1;
    adminScheduleApi.find({ classId, academicYear, week: otherWeek })
      .then((response) => {
        if (active) setSchedules((current) => ({ ...current, [otherWeek]: scheduleFromResponse(response) }));
      })
      .catch((requestError) => {
        if (active) setError(requestError instanceof ApiError ? requestError.message : "Не вдалося завантажити розклад іншого тижня.");
      })
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [academicYear, classId, currentWeek]);

  const selectSource = (value) => {
    const next = Number(value);
    setSourceWeek(next);
    if (next === targetWeek) setTargetWeek(next === 1 ? 2 : 1);
  };
  const selectTarget = (value) => {
    const next = Number(value);
    setTargetWeek(next);
    if (next === sourceWeek) setSourceWeek(next === 1 ? 2 : 1);
  };
  const swap = () => {
    setSourceWeek(targetWeek);
    setTargetWeek(sourceWeek);
  };
  const source = schedules[sourceWeek] || createEmptySchedule();
  const target = schedules[targetWeek] || createEmptySchedule();
  const sourceCount = countLessons(source);
  const targetCount = countLessons(target);
  const copiedCount = countCopiedLessons(source, target, mode);
  const apply = () => onApply(targetWeek, copySchedule(source, target, mode));

  return (
    <ModalFrame title="Скопіювати розклад" subtitle="Оберіть напрямок і спосіб копіювання" onClose={onClose}>
      <div className="mt-5 grid grid-cols-[1fr_auto_1fr] items-end gap-3">
        <FormField label="З">
          <WeekSelect value={sourceWeek} onChange={selectSource} />
        </FormField>
        <button type="button" onClick={swap} className="mb-0.5 flex h-10 w-10 items-center justify-center rounded-md border border-slate-300 bg-white text-slate-600 hover:bg-slate-50 hover:text-slate-900" aria-label="Поміняти тижні місцями" title="Поміняти напрямок">
          <ArrowLeftRight className="h-4 w-4" aria-hidden="true" />
        </button>
        <FormField label="До">
          <WeekSelect value={targetWeek} onChange={selectTarget} />
        </FormField>
      </div>

      <fieldset className="mt-5 space-y-2">
        <legend className="mb-2 text-[0.76rem] font-semibold text-slate-700">Спосіб копіювання</legend>
        <CopyModeOption
          checked={mode === COPY_MODES.EMPTY_SLOTS}
          onChange={() => setMode(COPY_MODES.EMPTY_SLOTS)}
          title="Заповнити лише порожні слоти"
          description="Наявні уроки цільового тижня залишаться без змін."
        />
        <CopyModeOption
          checked={mode === COPY_MODES.REPLACE}
          onChange={() => setMode(COPY_MODES.REPLACE)}
          title="Замінити наявний розклад"
          description="Усі уроки цільового тижня буде замінено скопійованими."
        />
      </fieldset>

      <div className="mt-5 rounded-md border border-slate-200 bg-slate-50 p-4 text-[0.76rem] leading-5 text-slate-600">
        {loading ? <p>Завантаження розкладів...</p> : (
          <>
            <p className="font-bold text-slate-900">{weekLabel(sourceWeek)} → {weekLabel(targetWeek)}</p>
            <p className="mt-1">У джерелі: {sourceCount} уроків. У цільовому тижні: {targetCount} уроків.</p>
            <p>Буде скопійовано: {copiedCount} уроків.</p>
          </>
        )}
      </div>
      {error ? <Alert className="mt-3">{error}</Alert> : null}
      {!loading && !error && sourceCount === 0 ? <Alert className="mt-3">У вибраному тижні немає уроків для копіювання.</Alert> : null}

      <div className="mt-6 flex justify-end gap-2">
        <Button type="button" variant="ghost" onClick={onClose} className="h-10 rounded-md border border-slate-300 bg-white px-4 text-xs">Скасувати</Button>
        <Button type="button" onClick={apply} disabled={loading || Boolean(error) || sourceCount === 0 || copiedCount === 0} className="h-10 rounded-md px-4 text-xs">
          <Copy className="h-4 w-4" aria-hidden="true" />Застосувати
        </Button>
      </div>
    </ModalFrame>
  );
}

function WeekSelect({ value, onChange }) {
  return (
    <span className="relative block">
      <select value={value} onChange={(event) => onChange(event.target.value)} className="h-10 w-full appearance-none rounded-md border border-slate-300 bg-white px-3 pr-8 text-[0.78rem] font-medium text-slate-700 outline-none hover:border-slate-400 focus:border-slate-500">
        <option value={1}>Перший тиждень</option>
        <option value={2}>Другий тиждень</option>
      </select>
      <ChevronDown className="pointer-events-none absolute right-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
    </span>
  );
}

function CopyModeOption({ checked, onChange, title, description }) {
  return (
    <label className={cn("flex cursor-pointer gap-3 rounded-md border p-3 transition-colors", checked ? "border-slate-500 bg-slate-50" : "border-slate-200 bg-white hover:border-slate-300")}>
      <input type="radio" name="copy-mode" checked={checked} onChange={onChange} className="mt-0.5 h-4 w-4 accent-slate-800" />
      <span><span className="block text-[0.8rem] font-semibold text-slate-800">{title}</span><span className="mt-1 block text-[0.72rem] leading-5 text-slate-500">{description}</span></span>
    </label>
  );
}

function weekLabel(weekNumber) {
  return weekNumber === 1 ? "Перший тиждень" : "Другий тиждень";
}

function SelectField({ label, children }) {
  return (
    <label className="block">
      <span className="mb-2 block text-[0.75rem] font-semibold text-slate-600">{label}</span>
      <span className="relative block">
        {children}
        <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
      </span>
    </label>
  );
}

function DayColumn({ day, lessons, openMenu, onToggleMenu, onAdd, onEdit, onDelete }) {
  const lessonsByNumber = new Map(lessons.map((lesson) => [lesson.lessonNumber, lesson]));
  return (
    <section aria-labelledby={`day-${day.id}`}>
      <div className="mb-3 flex h-11 items-center justify-between border-b-2 border-slate-800 px-1">
        <h2 id={`day-${day.id}`} className="text-[0.9rem] font-bold text-slate-900">{day.label}</h2>
        <span className="text-[0.7rem] font-medium text-slate-400">{lessons.length} уроків</span>
      </div>
      <div className="space-y-3">
        {lessonTimes.map((times, index) => {
          const lessonNumber = index + 1;
          const lesson = lessonsByNumber.get(lessonNumber);
          return lesson ? (
            <LessonCard
              key={lesson.id}
              dayId={day.id}
              lesson={lesson}
              menuOpen={openMenu === `${day.id}-${lesson.id}`}
              onToggleMenu={() => onToggleMenu(`${day.id}-${lesson.id}`)}
              onEdit={() => onEdit(lesson)}
              onDelete={() => onDelete(lesson)}
            />
          ) : (
            <button
              key={`${day.id}-${lessonNumber}`}
              type="button"
              onClick={() => onAdd(lessonNumber)}
              className="flex h-[154px] w-full flex-col items-center justify-center rounded-lg border border-dashed border-slate-300 bg-slate-50/50 text-[0.78rem] font-semibold text-slate-400 transition-colors hover:border-slate-500 hover:bg-white hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-400"
            >
              <Plus className="mb-2 h-4 w-4" aria-hidden="true" />
              Додати урок
              <span className="mt-1 text-[0.68rem] font-normal text-slate-400">{lessonNumber}-й урок · {times[0]}</span>
            </button>
          );
        })}
      </div>
    </section>
  );
}

function LessonCard({ lesson, menuOpen, onToggleMenu, onEdit, onDelete }) {
  const [startTime, endTime] = lessonTimes[lesson.lessonNumber - 1];
  return (
    <article className="relative h-[154px] rounded-lg border border-slate-200 bg-white p-3.5 shadow-[0_10px_30px_-26px_rgba(15,23,42,0.6)]">
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-1.5 text-[0.7rem] font-medium text-slate-500">
          <Clock3 className="h-3.5 w-3.5" aria-hidden="true" />
          {startTime}–{endTime}
        </div>
        <button type="button" onClick={onToggleMenu} className="-mr-1 -mt-1 flex h-7 w-7 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label={`Дії для уроку ${lesson.subject}`} aria-expanded={menuOpen}>
          <EllipsisVertical className="h-4 w-4" aria-hidden="true" />
        </button>
      </div>
      {menuOpen ? (
        <div className="absolute right-2 top-10 z-20 w-36 rounded-md border border-slate-200 bg-white p-1 shadow-lg">
          <button type="button" onClick={onEdit} className="flex h-8 w-full items-center gap-2 rounded px-2 text-left text-[0.72rem] font-medium text-slate-600 hover:bg-slate-50"><Pencil className="h-3.5 w-3.5" />Редагувати</button>
          <button type="button" onClick={onDelete} className="flex h-8 w-full items-center gap-2 rounded px-2 text-left text-[0.72rem] font-medium text-red-600 hover:bg-red-50"><Trash2 className="h-3.5 w-3.5" />Видалити</button>
        </div>
      ) : null}
      <div className="mt-1 text-[0.66rem] font-bold uppercase text-slate-400">{lesson.lessonNumber}-й урок</div>
      <h3 className="mt-1 truncate text-[0.88rem] font-bold text-slate-900" title={lesson.subject}>{lesson.subject}</h3>
      <div className="mt-2 space-y-1.5 text-[0.7rem] text-slate-500">
        <p className="flex min-w-0 items-center gap-1.5"><UserRound className="h-3.5 w-3.5 shrink-0" /><span className="truncate" title={lesson.teacher}>{lesson.teacher}</span></p>
        <p className="flex items-center gap-1.5"><MapPin className="h-3.5 w-3.5 shrink-0" />Кабінет {lesson.room}</p>
      </div>
    </article>
  );
}

function LessonDialog({ dialog, onClose, onSave }) {
  const editing = Boolean(dialog.lesson);
  const [form, setForm] = useState(() => dialog.lesson ? { ...dialog.lesson } : {
    ...emptyLesson,
    lessonNumber: dialog.lessonNumber,
    subjectId: "",
    teacherId: "",
  });
  const [subjects, setSubjects] = useState([]);
  const [isLoadingOptions, setIsLoadingOptions] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    adminSubjectsApi.findAll()
      .then((items) => {
        if (!active) return;
        setSubjects(items);
        if (dialog.lesson && !dialog.lesson.subjectId) {
          const matchedSubject = items.find((subject) => subject.title === dialog.lesson.subject);
          const matchedTeacher = matchedSubject?.teachers.find((teacher) => teacher.fullName === dialog.lesson.teacher);
          if (matchedSubject) {
            setForm((current) => ({
              ...current,
              subjectId: String(matchedSubject.id),
              teacherId: matchedTeacher ? String(matchedTeacher.id) : "",
            }));
          }
        }
      })
      .catch((requestError) => {
        if (active) setError(requestError instanceof ApiError ? requestError.message : "Не вдалося завантажити предмети.");
      })
      .finally(() => active && setIsLoadingOptions(false));
    return () => { active = false; };
  }, [dialog.lesson]);

  const selectedSubject = subjects.find((subject) => String(subject.id) === String(form.subjectId));
  const availableTeachers = (selectedSubject?.teachers || []).filter((teacher) => teacher.status === "ACTIVE");

  const update = (field, value) => {
    setForm((current) => ({ ...current, [field]: value }));
    setError("");
  };

  const selectSubject = (subjectId) => {
    const subject = subjects.find((item) => String(item.id) === subjectId);
    setForm((current) => ({
      ...current,
      subjectId,
      subject: subject?.title || "",
      teacherId: "",
      teacher: "",
    }));
    setError("");
  };

  const selectTeacher = (teacherId) => {
    const teacher = availableTeachers.find((item) => String(item.id) === teacherId);
    setForm((current) => ({
      ...current,
      teacherId,
      teacher: teacher?.fullName || "",
    }));
    setError("");
  };

  const submit = (event) => {
    event.preventDefault();
    if (!form.subjectId || !form.teacherId || !form.room.trim()) {
      setError("Оберіть предмет і вчителя та вкажіть кабінет.");
      return;
    }
    onSave({ ...form, lessonNumber: Number(form.lessonNumber), subject: form.subject.trim(), teacher: form.teacher.trim(), room: form.room.trim() });
  };

  return (
    <ModalFrame title={editing ? "Редагувати урок" : "Додати урок"} subtitle={weekdays.find((day) => day.id === dialog.dayId)?.label} onClose={onClose}>
      <form onSubmit={submit} className="mt-5 space-y-4">
        <div className="rounded-md border border-slate-200 bg-slate-50 px-4 py-3">
          <p className="text-[0.7rem] font-medium text-slate-500">Обраний слот</p>
          <p className="mt-1 text-[0.88rem] font-bold text-slate-900">{form.lessonNumber}-й урок · {lessonTimes[Number(form.lessonNumber) - 1].join("–")}</p>
        </div>

        <FormField label="Предмет">
          <div className="relative">
            <select value={form.subjectId || ""} onChange={(event) => selectSubject(event.target.value)} disabled={isLoadingOptions} autoFocus className="h-10 w-full appearance-none rounded-md border border-slate-300 bg-white px-3 pr-9 text-[0.82rem] text-slate-700 outline-none hover:border-slate-400 focus:border-slate-500 disabled:bg-slate-50 disabled:opacity-60">
              <option value="">{isLoadingOptions ? "Завантаження..." : "Оберіть предмет"}</option>
              {subjects.map((subject) => <option key={subject.id} value={subject.id}>{subject.title}</option>)}
            </select>
            <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
          </div>
        </FormField>

        <FormField label="Учитель">
          <div className="relative">
            <select value={form.teacherId || ""} onChange={(event) => selectTeacher(event.target.value)} disabled={!form.subjectId || isLoadingOptions || availableTeachers.length === 0} className="h-10 w-full appearance-none rounded-md border border-slate-300 bg-white px-3 pr-9 text-[0.82rem] text-slate-700 outline-none hover:border-slate-400 focus:border-slate-500 disabled:bg-slate-50 disabled:text-slate-400">
              <option value="">{!form.subjectId ? "Спочатку оберіть предмет" : availableTeachers.length ? "Оберіть учителя" : "Для предмета немає активних учителів"}</option>
              {availableTeachers.map((teacher) => <option key={teacher.id} value={teacher.id}>{teacher.fullName}</option>)}
            </select>
            <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
          </div>
        </FormField>

        <FormField label="Кабінет"><Input value={form.room} onChange={(event) => update("room", event.target.value)} maxLength={20} placeholder="Наприклад, 302" /></FormField>
        {error ? <Alert>{error}</Alert> : null}
        <DialogActions onClose={onClose} submitLabel={editing ? "Зберегти" : "Додати урок"} disabled={isLoadingOptions} />
      </form>
    </ModalFrame>
  );
}

function DeleteLessonDialog({ target, onClose, onConfirm }) {
  return (
    <ModalFrame title="Видалити урок?" subtitle={`${target.lesson.subject}, ${target.lesson.lessonNumber}-й урок`} onClose={onClose} alert>
      <p className="mt-5 rounded-md border border-red-200 bg-red-50 p-4 text-[0.8rem] leading-5 text-red-700">Урок буде вилучено з розкладу обраного класу. Цю дію неможливо скасувати.</p>
      <div className="mt-6 flex justify-end gap-2">
        <Button type="button" variant="ghost" onClick={onClose} className="h-10 rounded-md border border-slate-300 bg-white px-4 text-xs">Скасувати</Button>
        <Button type="button" onClick={onConfirm} className="h-10 rounded-md border border-red-700 bg-red-700 px-4 text-xs hover:bg-red-600"><Trash2 className="h-4 w-4" />Видалити</Button>
      </div>
    </ModalFrame>
  );
}

function AcademicYearDialog({ years, onClose, onCreate }) {
  const today = new Date();
  const currentAcademicYearStart = today.getMonth() >= 8
    ? today.getFullYear()
    : today.getFullYear() - 1;
  const nextStartYear = years.length
    ? Math.max(...years.map((year) => Number(year.split("/")[0]))) + 1
    : currentAcademicYearStart;
  const nextYear = `${nextStartYear}/${nextStartYear + 1}`;
  const hasNotStarted = nextStartYear > currentAcademicYearStart;
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const create = async () => {
    setSaving(true);
    setError("");
    try {
      await onCreate(nextYear);
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : "Не вдалося створити навчальний рік.");
      setSaving(false);
    }
  };
  return (
    <ModalFrame title="Додати навчальний рік" subtitle="Створення наступного навчального періоду" onClose={onClose}>
      <div className="mt-5 rounded-md border border-slate-200 bg-slate-50 p-4">
        <p className="text-[0.72rem] font-medium text-slate-500">Новий навчальний рік</p>
        <p className="mt-1 text-lg font-bold text-slate-900">{nextYear}</p>
      </div>
      <p className="mt-3 text-[0.76rem] leading-5 text-slate-500">Після створення рік буде вибрано автоматично. Розклад нового року спочатку можна редагувати як окремий.</p>
      {hasNotStarted ? (
        <Alert className="mt-3">Навчальний рік ще не настав. Його можна створити не раніше 1 вересня {nextStartYear} року.</Alert>
      ) : null}
      {error ? <Alert className="mt-3">{error}</Alert> : null}
      <div className="mt-6 flex justify-end gap-2">
        <Button type="button" variant="ghost" onClick={onClose} disabled={saving} className="h-10 rounded-md border border-slate-300 bg-white px-4 text-xs">Скасувати</Button>
        <Button type="button" onClick={create} disabled={saving || hasNotStarted} className="h-10 rounded-md px-4 text-xs"><Plus className="h-4 w-4" />{saving ? "Створення..." : "Створити"}</Button>
      </div>
    </ModalFrame>
  );
}

function FormField({ label, children }) {
  return <label className="block text-[0.76rem] font-semibold text-slate-700">{label}<span className="mt-2 block">{children}</span></label>;
}

function DialogActions({ onClose, submitLabel, disabled = false }) {
  return (
    <div className="flex justify-end gap-2 pt-2">
      <Button type="button" variant="ghost" onClick={onClose} className="h-10 rounded-md border border-slate-300 bg-white px-4 text-xs">Скасувати</Button>
      <Button type="submit" disabled={disabled} className="h-10 rounded-md px-4 text-xs">{submitLabel}</Button>
    </div>
  );
}

function ModalFrame({ title, subtitle, onClose, alert = false, children }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section role={alert ? "alertdialog" : "dialog"} aria-modal="true" aria-labelledby="schedule-dialog-title" onKeyDown={(event) => event.key === "Escape" && onClose()} className="max-h-[calc(100dvh-2rem)] w-full max-w-[480px] overflow-y-auto rounded-lg border border-slate-200 bg-white p-5 shadow-2xl outline-none sm:p-6">
        <div className="flex items-start justify-between gap-4">
          <div><h2 id="schedule-dialog-title" className="text-base font-bold text-slate-950">{title}</h2><p className="mt-1 text-xs text-slate-500">{subtitle}</p></div>
          <button type="button" onClick={onClose} aria-label="Закрити" className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700"><X className="h-4 w-4" /></button>
        </div>
        {children}
      </section>
    </div>
  );
}

function createEmptySchedule() {
  return Object.fromEntries(weekdays.map((day) => [day.id, []]));
}

function scheduleFromResponse(response) {
  const result = createEmptySchedule();
  for (const lesson of response.lessons || []) {
    if (!result[lesson.weekday]) continue;
    result[lesson.weekday].push(lesson);
  }
  return result;
}
