import { useEffect, useState } from "react";
import {
  ChevronDown,
  Clock3,
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

export function AdminSchedulePage() {
  const [classes, setClasses] = useState([]);
  const [selectedClassId, setSelectedClassId] = useState("");
  const [academicYears, setAcademicYears] = useState([]);
  const [selectedYear, setSelectedYear] = useState("2026/2027");
  const [week, setWeek] = useState(1);
  const [schedule, setSchedule] = useState(createEmptySchedule);
  const [lessonDialog, setLessonDialog] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [yearDialogOpen, setYearDialogOpen] = useState(false);
  const [openMenu, setOpenMenu] = useState("");
  const [loadingClasses, setLoadingClasses] = useState(true);
  const [loadingSchedule, setLoadingSchedule] = useState(false);
  const [savingSchedule, setSavingSchedule] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  useEffect(() => {
    let active = true;
    adminClassesApi.findAll()
      .then((items) => {
        if (!active) return;
        setClasses(items);
        setSelectedClassId(items[0] ? String(items[0].id) : "");
      })
      .catch(() => {
        if (!active) return;
        setClasses([]);
        setSelectedClassId("");
      })
      .finally(() => active && setLoadingClasses(false));
    return () => { active = false; };
  }, []);

  useEffect(() => {
    let active = true;
    adminScheduleApi.findAcademicYears()
      .then((years) => {
        if (!active) return;
        const availableYears = years.length ? years : ["2026/2027"];
        setAcademicYears(availableYears);
        setSelectedYear((current) => availableYears.includes(current) ? current : availableYears[0]);
      })
      .catch((requestError) => {
        if (!active) return;
        setAcademicYears(["2026/2027"]);
        setError(requestError instanceof ApiError ? requestError.message : "Не вдалося завантажити навчальні роки.");
      });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!selectedClassId || !selectedYear) {
      setSchedule(createEmptySchedule());
      setDirty(false);
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
  }, [selectedClassId, selectedYear, week]);

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
    setSelectedYear(value);
  };

  const addAcademicYear = async (year) => {
    const response = await adminScheduleApi.createAcademicYear(year);
    setAcademicYears((current) => [...current, response.academicYear].sort());
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

  return (
    <AdminLayout
      activeItem="Розклад"
      title="Розклад"
      subtitle="Перегляд та редагування розкладу занять для класів"
    >
      <Card className="rounded-lg p-4 sm:p-5">
        <div className="grid gap-4 lg:grid-cols-[minmax(200px,1fr)_minmax(200px,1fr)_auto_auto] lg:items-end">
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
    </AdminLayout>
  );
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
  const nextStartYear = years.length
    ? Math.max(...years.map((year) => Number(year.split("/")[0]))) + 1
    : 2026;
  const nextYear = `${nextStartYear}/${nextStartYear + 1}`;
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
      {error ? <Alert className="mt-3">{error}</Alert> : null}
      <div className="mt-6 flex justify-end gap-2">
        <Button type="button" variant="ghost" onClick={onClose} disabled={saving} className="h-10 rounded-md border border-slate-300 bg-white px-4 text-xs">Скасувати</Button>
        <Button type="button" onClick={create} disabled={saving} className="h-10 rounded-md px-4 text-xs"><Plus className="h-4 w-4" />{saving ? "Створення..." : "Створити"}</Button>
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
