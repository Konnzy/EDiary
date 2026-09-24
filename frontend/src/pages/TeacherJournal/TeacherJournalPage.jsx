import { useEffect, useMemo, useRef, useState } from "react";
import {
  AlertTriangle,
  CheckCircle2,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  GraduationCap,
  LogOut,
  Pencil,
  Plus,
  Save,
  Trash2,
  X,
} from "lucide-react";
import { AppFooter } from "@/components/AppFooter.jsx";
import { NotificationButton } from "@/components/NotificationButton.jsx";
import { CurrentUserBadge } from "@/lib/currentUser.jsx";
import { Button } from "@/components/ui/button.jsx";
import { authApi } from "@/lib/api.js";
import { allowRouteNavigation, registerNavigationGuard } from "@/lib/navigationGuard.js";
import { teacherNavigationItems } from "@/lib/teacherNavigation.js";
import { cn } from "@/lib/utils.js";
import {
  calculateAverage,
  contextKey,
  dateParts,
  gradeKey,
  homeworkStatuses,
  journalClasses,
  lessonDates,
  lessonKey,
  periods,
  readJournalState,
  resolveHomeworkStatus,
  workTypes,
  writeJournalState,
} from "./journalModel.js";

const gradeValues = Array.from({ length: 12 }, (_, index) => String(index + 1));
const attendanceOptions = [
  { value: "PRESENT", label: "П" },
  { value: "ABSENT", label: "Н" },
  { value: "EXCUSED", label: "ПП" },
  { value: "RELEASED", label: "З" },
];

const workTypeClasses = {
  CLASSWORK: "border-sky-200 bg-sky-50 text-sky-700",
  HOMEWORK: "border-emerald-200 bg-emerald-50 text-emerald-700",
  INDEPENDENT: "border-amber-200 bg-amber-50 text-amber-700",
  CONTROL: "border-red-200 bg-red-50 text-red-600",
  PRACTICAL: "border-teal-200 bg-teal-50 text-teal-700",
  LABORATORY: "border-violet-200 bg-violet-50 text-violet-700",
  THEMATIC: "border-indigo-200 bg-indigo-50 text-indigo-700",
};

export function TeacherJournalPage() {
  const [classId, setClassId] = useState(journalClasses[0].id);
  const [subjectId, setSubjectId] = useState(journalClasses[0].subjects[0].id);
  const [period, setPeriod] = useState(periods[0].value);
  const [journal, setJournal] = useState(readJournalState);
  const [lessonEditor, setLessonEditor] = useState(null);
  const [gradeEditor, setGradeEditor] = useState(null);
  const [notice, setNotice] = useState("");
  const [dirty, setDirty] = useState(false);
  const [pendingNavigation, setPendingNavigation] = useState(null);

  const selectedClass = journalClasses.find((item) => item.id === classId) || journalClasses[0];
  const selectedSubject = selectedClass.subjects.find((item) => item.id === subjectId)
    || selectedClass.subjects[0];
  const context = contextKey(selectedClass.id, selectedSubject.id, period);
  const dates = useMemo(
    () => lessonDates(period, selectedSubject.weekdays),
    [period, selectedSubject.weekdays],
  );

  useEffect(() => {
    if (!dirty) return undefined;
    const handleBeforeUnload = (event) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [dirty]);

  useEffect(() => {
    if (!dirty) return undefined;
    return registerNavigationGuard((request) => {
      setPendingNavigation(request);
      return false;
    });
  }, [dirty]);

  useEffect(() => {
    if (!notice) return undefined;
    const timeout = window.setTimeout(() => setNotice(""), 2400);
    return () => window.clearTimeout(timeout);
  }, [notice]);

  const changeClass = (nextClassId) => {
    const nextClass = journalClasses.find((item) => item.id === nextClassId);
    setClassId(nextClassId);
    setSubjectId(nextClass.subjects[0].id);
    setLessonEditor(null);
    setGradeEditor(null);
  };

  const saveLesson = (date, value) => {
    setJournal((current) => ({
      ...current,
      lessons: { ...current.lessons, [lessonKey(context, date)]: value },
    }));
    setDirty(true);
    setLessonEditor(null);
    setNotice("Дані уроку додано до чернетки.");
  };

  const saveGrade = (studentId, date, value) => {
    const key = gradeKey(context, studentId, date);
    const updating = Boolean(journal.grades[key]);
    setJournal((current) => ({
      ...current,
      grades: { ...current.grades, [key]: value },
    }));
    setDirty(true);
    setGradeEditor(null);
    setNotice(updating ? "Запис оновлено в чернетці." : "Запис додано до чернетки.");
  };

  const deleteGrade = (studentId, date) => {
    setJournal((current) => {
      const grades = { ...current.grades };
      delete grades[gradeKey(context, studentId, date)];
      return { ...current, grades };
    });
    setDirty(true);
    setGradeEditor(null);
    setNotice("Запис видалено з чернетки.");
  };

  const saveJournalChanges = () => {
    writeJournalState(journal);
    setDirty(false);
    setNotice("Зміни журналу збережено.");
  };

  const logout = async () => {
    try { await authApi.logout(); } catch { /* The session may already be expired. */ }
    window.location.hash = "login";
  };

  const requestLogout = () => {
    if (dirty) {
      setPendingNavigation({ action: "logout" });
      return;
    }
    logout();
  };

  const continueNavigation = async (saveChanges) => {
    const request = pendingNavigation;
    if (!request) return;
    if (saveChanges) writeJournalState(journal);
    setDirty(false);
    setPendingNavigation(null);
    if (request.route) {
      allowRouteNavigation(request.route);
      window.location.hash = request.route;
    } else if (request.action === "logout") {
      await logout();
    }
  };

  return (
    <div className="flex min-h-screen flex-col bg-slate-50 text-slate-800">
      <TeacherTopBar onLogout={requestLogout} />
      <TeacherNavigation compact />
      <div className="flex flex-1">
        <TeacherNavigation />
        <div className="flex min-w-0 flex-1 flex-col lg:border-l lg:border-slate-200/80">
          <main className="min-w-0 flex-1 px-4 py-5 sm:px-6 lg:px-8 lg:py-7">
            <div className="mx-auto w-full max-w-[1680px]">
              <header className="mb-6">
                <h1 className="text-[1.55rem] font-bold leading-tight text-slate-900 sm:text-[1.75rem]">Журнал</h1>
                <p className="mt-2 text-[0.88rem] text-slate-500">Оцінки, теми уроків та домашні завдання</p>
              </header>

              <JournalFilters
                classId={classId}
                subjectId={selectedSubject.id}
                period={period}
                subjects={selectedClass.subjects}
                dirty={dirty}
                onClassChange={changeClass}
                onSubjectChange={(value) => { setSubjectId(value); setLessonEditor(null); setGradeEditor(null); }}
                onPeriodChange={(value) => { setPeriod(value); setLessonEditor(null); setGradeEditor(null); }}
                onSave={saveJournalChanges}
              />

              <section className="mt-5 overflow-hidden rounded-lg border border-slate-200 bg-white shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)]" aria-label="Таблиця журналу">
                <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-4 py-3.5 sm:px-5">
                  <div className="min-w-0">
                    <h2 className="truncate text-[0.95rem] font-bold text-slate-900">{selectedSubject.label} · {selectedClass.label}</h2>
                    <p className="mt-1 text-[0.72rem] text-slate-500">{selectedClass.students.length} учнів · {dates.length} уроків</p>
                  </div>
                  <div className="flex flex-wrap items-center justify-end gap-2 text-[0.72rem] text-slate-500">
                    <span className="inline-flex h-7 items-center rounded border border-emerald-200 bg-emerald-50 px-2.5 text-emerald-700">П — присутній</span>
                    <span className="inline-flex h-7 items-center rounded border border-red-200 bg-red-50 px-2.5 text-red-600">Н — відсутній</span>
                    <span className="inline-flex h-7 items-center rounded border border-sky-200 bg-sky-50 px-2.5 text-sky-700">ПП — поважна причина</span>
                    <span className="inline-flex h-7 items-center rounded border border-amber-200 bg-amber-50 px-2.5 text-amber-700">З — звільнений</span>
                  </div>
                </div>

                <JournalTable
                  journal={journal}
                  context={context}
                  dates={dates}
                  students={selectedClass.students}
                  onLessonClick={setLessonEditor}
                  onGradeClick={(event, student, date) => setGradeEditor({
                    student,
                    date,
                    rect: event.currentTarget.getBoundingClientRect(),
                  })}
                />
              </section>
            </div>
          </main>
          <AppFooter />
        </div>
      </div>

      {lessonEditor ? (
        <LessonDetailsDialog
          date={lessonEditor}
          className={selectedClass.label}
          subject={selectedSubject.label}
          initialValue={journal.lessons[lessonKey(context, lessonEditor)]}
          onClose={() => setLessonEditor(null)}
          onSave={(value) => saveLesson(lessonEditor, value)}
        />
      ) : null}

      {gradeEditor ? (
        <GradePopover
          key={`${gradeEditor.student.id}:${gradeEditor.date}`}
          editor={gradeEditor}
          initialValue={journal.grades[gradeKey(context, gradeEditor.student.id, gradeEditor.date)]}
          lesson={journal.lessons[lessonKey(context, gradeEditor.date)]}
          onClose={() => setGradeEditor(null)}
          onSave={(value) => saveGrade(gradeEditor.student.id, gradeEditor.date, value)}
          onDelete={() => deleteGrade(gradeEditor.student.id, gradeEditor.date)}
        />
      ) : null}

      {notice ? (
        <div role="status" className="fixed bottom-20 right-4 z-[70] flex items-center gap-2 rounded-md border border-emerald-200 bg-white px-4 py-3 text-[0.78rem] font-semibold text-emerald-700 shadow-xl sm:right-5">
          <CheckCircle2 className="h-4 w-4" aria-hidden="true" />{notice}
        </div>
      ) : null}

      {pendingNavigation ? (
        <UnsavedJournalDialog
          className={selectedClass.label}
          subject={selectedSubject.label}
          period={periods.find((item) => item.value === period)?.label || period}
          onClose={() => setPendingNavigation(null)}
          onDiscard={() => continueNavigation(false)}
          onSave={() => continueNavigation(true)}
        />
      ) : null}
    </div>
  );
}

function JournalFilters({ classId, subjectId, period, subjects, dirty, onClassChange, onSubjectChange, onPeriodChange, onSave }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-4 shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)]">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-[minmax(150px,1fr)_minmax(150px,1fr)_minmax(210px,1fr)_auto] lg:items-end">
        <SelectField label="Клас" value={classId} onChange={onClassChange}>
          {journalClasses.map((item) => <option key={item.id} value={item.id}>{item.label}</option>)}
        </SelectField>
        <SelectField label="Предмет" value={subjectId} onChange={onSubjectChange}>
          {subjects.map((item) => <option key={item.id} value={item.id}>{item.label}</option>)}
        </SelectField>
        <PeriodSwitcher value={period} onChange={onPeriodChange} />
        <div className="sm:col-span-2 lg:col-span-1 lg:min-w-[178px]">
          <p className={cn("mb-2 inline-flex items-center gap-2 whitespace-nowrap text-[0.72rem] font-medium", dirty ? "text-amber-700" : "text-emerald-700")}>
            <span className={cn("h-2 w-2 rounded-full", dirty ? "bg-amber-400" : "bg-emerald-500")} aria-hidden="true" />
            {dirty ? "Є незбережені зміни" : "Усі зміни збережено"}
          </p>
          <Button type="button" onClick={onSave} disabled={!dirty} className="h-10 w-full rounded-md px-4 text-xs">
            <Save className="h-4 w-4" aria-hidden="true" />
            Зберегти зміни
          </Button>
        </div>
      </div>
    </section>
  );
}

function PeriodSwitcher({ value, onChange }) {
  const activeIndex = periods.findIndex((item) => item.value === value);
  const safeIndex = activeIndex >= 0 ? activeIndex : 0;
  const activePeriod = periods[safeIndex];
  const move = (direction) => {
    const nextPeriod = periods[safeIndex + direction];
    if (nextPeriod) onChange(nextPeriod.value);
  };

  return (
    <div className="min-w-0">
      <span className="mb-2 block text-[0.74rem] font-semibold text-slate-600">Період</span>
      <div className="grid h-10 grid-cols-[40px_minmax(0,1fr)_40px] overflow-hidden rounded-md border border-slate-300 bg-white">
        <button
          type="button"
          onClick={() => move(-1)}
          disabled={safeIndex === 0}
          className="flex items-center justify-center text-slate-600 transition-colors hover:bg-slate-100 disabled:cursor-not-allowed disabled:text-slate-300 disabled:hover:bg-transparent"
          aria-label="Попередній період"
        >
          <ChevronLeft className="h-4 w-4" aria-hidden="true" />
        </button>
        <span className="flex min-w-0 items-center justify-center border-x border-slate-200 px-2 text-center text-[0.8rem] font-semibold text-slate-700" aria-live="polite">
          {activePeriod.label}
        </span>
        <button
          type="button"
          onClick={() => move(1)}
          disabled={safeIndex === periods.length - 1}
          className="flex items-center justify-center text-slate-600 transition-colors hover:bg-slate-100 disabled:cursor-not-allowed disabled:text-slate-300 disabled:hover:bg-transparent"
          aria-label="Наступний період"
        >
          <ChevronRight className="h-4 w-4" aria-hidden="true" />
        </button>
      </div>
    </div>
  );
}

function SelectField({ label, value, onChange, children }) {
  return (
    <label className="block min-w-0">
      <span className="mb-2 block text-[0.74rem] font-semibold text-slate-600">{label}</span>
      <span className="relative block">
        <select value={value} onChange={(event) => onChange(event.target.value)} className="h-10 w-full appearance-none rounded-md border border-slate-300 bg-white px-3 pr-9 text-[0.8rem] font-semibold text-slate-700 outline-hidden transition-colors hover:border-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200">
          {children}
        </select>
        <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
      </span>
    </label>
  );
}

function JournalTable({ journal, context, dates, students, onLessonClick, onGradeClick }) {
  return (
    <div className="max-h-[calc(100vh-330px)] min-h-[360px] overflow-auto">
      <table className="w-max min-w-full border-separate border-spacing-0 text-left">
        <thead className="sticky top-0 z-30 bg-slate-50">
          <tr>
            <th className="sticky left-0 z-40 min-w-[210px] border-b border-r border-slate-200 bg-slate-50 px-4 py-3 text-[0.72rem] font-semibold text-slate-500">Учень</th>
            {dates.map((date) => (
              <DateHeader
                key={date}
                date={date}
                lesson={journal.lessons[lessonKey(context, date)]}
                onClick={() => onLessonClick(date)}
              />
            ))}
            <th className="min-w-[86px] border-b border-l border-slate-200 bg-slate-50 px-2 py-3 text-center text-[0.7rem] font-semibold text-slate-500">Середній Бал</th>
          </tr>
        </thead>
        <tbody>
          {students.map((student, studentIndex) => (
            <tr key={student.id} className="group/row">
              <th className="sticky left-0 z-20 border-b border-r border-slate-200 bg-white px-4 py-3.5">
                <div className="flex items-center gap-3">
                  <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded bg-slate-100 text-[0.68rem] font-bold text-slate-500">{studentIndex + 1}</span>
                  <span className="whitespace-nowrap text-[0.8rem] font-semibold text-slate-800">{student.name}</span>
                </div>
              </th>
              {dates.map((date) => {
                const grade = journal.grades[gradeKey(context, student.id, date)];
                return <GradeCell key={date} grade={grade} onClick={(event) => onGradeClick(event, student, date)} />;
              })}
              <td className="border-b border-l border-slate-200 bg-slate-50/50 px-2 text-center text-[0.8rem] font-bold text-slate-700">
                {calculateAverage(journal, context, student.id, dates)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function DateHeader({ date, lesson, onClick }) {
  const parts = dateParts(date);
  const type = workTypes.find((item) => item.value === lesson?.workType);
  const today = date === new Date().toLocaleDateString("sv-SE");
  const topic = lesson?.topic || "Не вказано";
  const homework = lesson?.homework || "Не задано";
  const homeworkDeadline = lesson?.homeworkDeadline
    ? new Intl.DateTimeFormat("uk-UA", { day: "2-digit", month: "2-digit" }).format(new Date(`${lesson.homeworkDeadline}T12:00:00`))
    : "";
  return (
    <th className={cn("w-[168px] min-w-[168px] max-w-[168px] border-b border-r border-slate-200 p-0 text-center align-top", today && "bg-emerald-50")}>
      <button type="button" onClick={onClick} className="group/date flex min-h-[154px] w-full flex-col items-center px-2 py-2.5 text-left hover:bg-slate-100" title="Редагувати дані уроку">
        <span className="text-[0.64rem] font-semibold text-slate-500">{parts.weekday}</span>
        <span className="mt-0.5 text-[0.82rem] font-bold text-slate-800">{parts.day}.{parts.month}</span>
        <span className={cn(
          "mt-1 flex h-5 max-w-full items-center rounded px-1.5 text-[0.6rem] font-bold",
          type
            ? cn("border", workTypeClasses[type.value])
            : "text-slate-300 group-hover/date:bg-white group-hover/date:text-slate-500",
        )}>
          {type?.label || <Pencil className="h-3 w-3" aria-hidden="true" />}
        </span>
        <span className="mt-2.5 w-full border-t border-slate-200 pt-2 text-[0.66rem] font-normal leading-4 text-slate-600" title={`Тема: ${topic}`}>
          <span className="block truncate"><span className="font-semibold">Тема:</span> {topic}</span>
          <span className="mt-0.5 block truncate" title={`Домашнє завдання: ${homework}`}><span className="font-semibold">ДЗ:</span> {homework}</span>
          {homeworkDeadline ? <span className="mt-0.5 block truncate text-slate-400"><span className="font-semibold">До:</span> {homeworkDeadline}</span> : null}
        </span>
      </button>
    </th>
  );
}

function GradeCell({ grade, onClick }) {
  const gradeValue = grade?.grade || "";
  const attendanceStatus = grade?.attendanceStatus || "PRESENT";
  const attendanceLabel = attendanceStatus === "ABSENT"
    ? "Н"
    : attendanceStatus === "EXCUSED"
      ? "ПП"
      : attendanceStatus === "RELEASED"
        ? "З"
        : "";
  const numeric = /^\d+$/.test(gradeValue);
  const title = [
    grade?.comment,
    attendanceStatus === "ABSENT" && "Відсутній",
    attendanceStatus === "EXCUSED" && "Відсутній з поважної причини",
    attendanceStatus === "RELEASED" && "Звільнений",
  ].filter(Boolean).join(" · ") || "Виставити оцінку";
  return (
    <td className="h-[58px] border-b border-r border-slate-200 p-1.5 text-center">
      <button
        type="button"
        onClick={onClick}
        className={cn(
          "group/cell relative mx-auto flex h-10 w-12 items-center justify-center rounded border text-[0.82rem] font-bold transition-colors",
          !grade && "border-transparent text-slate-300 hover:border-slate-300 hover:bg-slate-50 hover:text-slate-600",
          numeric && Number(gradeValue) >= 10 && "border-sky-300 bg-sky-100 text-sky-700",
          numeric && Number(gradeValue) >= 7 && Number(gradeValue) < 10 && "border-emerald-200 bg-emerald-50 text-emerald-700",
          numeric && Number(gradeValue) >= 4 && Number(gradeValue) < 7 && "border-amber-200 bg-amber-50 text-amber-700",
          numeric && Number(gradeValue) < 4 && "border-red-200 bg-red-50 text-red-600",
          !gradeValue && attendanceStatus === "ABSENT" && "border-red-200 bg-red-50 text-red-600",
          !gradeValue && attendanceStatus === "EXCUSED" && "border-sky-200 bg-sky-50 text-sky-700",
          !gradeValue && attendanceStatus === "RELEASED" && "border-amber-200 bg-amber-50 text-amber-700",
        )}
        title={title}
      >
        {gradeValue || attendanceLabel || <Plus className="h-4 w-4 opacity-0 transition-opacity group-hover/cell:opacity-100" aria-hidden="true" />}
        {gradeValue && attendanceLabel ? (
          <span className={cn(
            "absolute -right-1 -top-1 flex min-h-4 min-w-4 items-center justify-center rounded px-1 text-[0.52rem] font-bold leading-4 shadow-xs",
            attendanceStatus === "ABSENT" && "bg-red-100 text-red-700",
            attendanceStatus === "EXCUSED" && "bg-sky-100 text-sky-700",
            attendanceStatus === "RELEASED" && "bg-amber-100 text-amber-700",
          )}>{attendanceLabel}</span>
        ) : null}
      </button>
    </td>
  );
}

function LessonDetailsDialog({ date, className, subject, initialValue, onClose, onSave }) {
  const [form, setForm] = useState(() => initialValue || { workType: "CLASSWORK", topic: "", homework: "", homeworkDeadline: "" });
  const panel = useRef(null);
  const formattedDate = new Intl.DateTimeFormat("uk-UA", { weekday: "long", day: "numeric", month: "long", year: "numeric" })
    .format(new Date(`${date}T12:00:00`));

  useEffect(() => {
    const onKeyDown = (event) => { if (event.key === "Escape") onClose(); };
    window.addEventListener("keydown", onKeyDown);
    panel.current?.focus();
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  const submit = (event) => {
    event.preventDefault();
    onSave({
      workType: form.workType,
      topic: form.topic.trim().replace(/\s+/g, " "),
      homework: form.homework.trim().replace(/\s+/g, " "),
      homeworkDeadline: form.homework.trim() ? form.homeworkDeadline || "" : "",
    });
  };

  return (
    <div className="fixed inset-0 z-[80] flex items-center justify-center bg-slate-900/45 p-4" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <section ref={panel} tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby="lesson-details-title" className="w-full max-w-[560px] rounded-lg border border-slate-200 bg-white p-5 shadow-2xl outline-hidden sm:p-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 id="lesson-details-title" className="text-lg font-bold text-slate-900">Дані уроку</h2>
            <p className="mt-1 text-[0.76rem] capitalize text-slate-500">{formattedDate} · {className} · {subject}</p>
          </div>
          <button type="button" onClick={onClose} className="flex h-8 w-8 shrink-0 items-center justify-center rounded text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label="Закрити"><X className="h-4 w-4" /></button>
        </div>
        <form onSubmit={submit} className="mt-5 space-y-4">
          <SelectField label="Тип роботи" value={form.workType} onChange={(value) => setForm((current) => ({ ...current, workType: value }))}>
            {workTypes.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
          </SelectField>
          <TextAreaField label="Тема уроку" value={form.topic} maxLength={200} rows={3} placeholder="Вкажіть тему уроку" onChange={(value) => setForm((current) => ({ ...current, topic: value }))} />
          <TextAreaField label="Домашнє завдання" value={form.homework} maxLength={500} rows={4} placeholder="Вкажіть домашнє завдання" onChange={(value) => setForm((current) => ({ ...current, homework: value }))} />
          <label className="block">
            <span className="mb-2 block text-[0.74rem] font-semibold text-slate-600">Дедлайн ДЗ <span className="font-normal text-slate-400">(необов’язково)</span></span>
            <input
              type="date"
              value={form.homeworkDeadline || ""}
              min={date}
              disabled={!form.homework.trim()}
              onChange={(event) => setForm((current) => ({ ...current, homeworkDeadline: event.target.value }))}
              className="h-10 w-full rounded-md border border-slate-300 bg-white px-3 text-[0.8rem] font-medium text-slate-700 outline-hidden transition-colors hover:border-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400"
            />
          </label>
          <div className="flex justify-end pt-1">
            <Button type="submit" className="h-10 rounded-md px-4 text-xs"><CheckCircle2 className="h-4 w-4" />Застосувати</Button>
          </div>
        </form>
      </section>
    </div>
  );
}

function TextAreaField({ label, value, onChange, ...props }) {
  return (
    <label className="block">
      <span className="mb-2 block text-[0.74rem] font-semibold text-slate-600">{label}</span>
      <textarea value={value} onChange={(event) => onChange(event.target.value)} className="w-full resize-none rounded-md border border-slate-300 bg-white px-3 py-2.5 text-[0.8rem] leading-5 text-slate-800 outline-hidden transition-colors placeholder:text-slate-400 hover:border-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200" {...props} />
    </label>
  );
}

function GradePopover({ editor, initialValue, lesson, onClose, onSave, onDelete }) {
  const [grade, setGrade] = useState(initialValue?.grade || "");
  const [attendanceStatus, setAttendanceStatus] = useState(initialValue?.attendanceStatus || "PRESENT");
  const hasHomework = Boolean(lesson?.homework?.trim());
  const [homeworkStatus, setHomeworkStatus] = useState(() => resolveHomeworkStatus(initialValue, lesson));
  const [comment, setComment] = useState(initialValue?.comment || "");
  const [error, setError] = useState("");
  const panel = useRef(null);
  const width = Math.min(320, window.innerWidth - 24);
  const left = Math.max(12, Math.min(editor.rect.left + editor.rect.width / 2 - width / 2, window.innerWidth - width - 12));
  const estimatedHeight = hasHomework ? 610 : 445;
  const top = Math.max(12, Math.min(editor.rect.bottom + 8, window.innerHeight - estimatedHeight - 12));

  useEffect(() => {
    const handlePointerDown = (event) => {
      if (!panel.current?.contains(event.target)) onClose();
    };
    const handleKeyDown = (event) => { if (event.key === "Escape") onClose(); };
    document.addEventListener("mousedown", handlePointerDown);
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [onClose]);

  const submit = (event) => {
    event.preventDefault();
    if (!grade && attendanceStatus === "PRESENT" && !hasHomework) {
      setError("Оберіть оцінку або змініть статус відвідування.");
      return;
    }
    onSave({
      grade,
      attendanceStatus,
      homeworkStatus: hasHomework ? homeworkStatus : "",
      comment: comment.trim().replace(/\s+/g, " "),
    });
  };

  return (
    <section ref={panel} role="dialog" aria-label={`Оцінка: ${editor.student.name}`} className="fixed z-[90] max-h-[calc(100vh-24px)] overflow-y-auto rounded-lg border border-slate-200 bg-white p-4 shadow-2xl" style={{ left, top, width }}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="truncate text-[0.86rem] font-bold text-slate-900">{editor.student.name}</h2>
          <p className="mt-1 text-[0.68rem] text-slate-500">{dateParts(editor.date).day}.{dateParts(editor.date).month}</p>
        </div>
        <button type="button" onClick={onClose} className="flex h-7 w-7 shrink-0 items-center justify-center rounded text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label="Закрити"><X className="h-4 w-4" /></button>
      </div>
      <form onSubmit={submit} className="mt-3">
        <p className="mb-2 text-[0.7rem] font-semibold text-slate-600">Оцінка</p>
        <div className="grid grid-cols-6 gap-1.5" aria-label="Оцінка">
          {gradeValues.map((gradeOption) => (
            <button
              key={gradeOption}
              type="button"
              onClick={() => { setGrade((current) => current === gradeOption ? "" : gradeOption); setError(""); }}
              className={cn(
                "h-8 rounded border text-[0.74rem] font-semibold transition-colors",
                grade === gradeOption ? "border-slate-800 bg-slate-800 text-white" : "border-slate-200 bg-white text-slate-600 hover:bg-slate-50",
              )}
            >
              {gradeOption}
            </button>
          ))}
        </div>
        <p className="mb-2 mt-3 text-[0.7rem] font-semibold text-slate-600">Відвідування</p>
        <div className="grid grid-cols-4 gap-1.5" aria-label="Статус відвідування">
          {attendanceOptions.map((option) => (
            <button
              key={option.value}
              type="button"
              onClick={() => { setAttendanceStatus(option.value); setError(""); }}
              className={cn(
                "h-9 rounded border px-1 text-[0.68rem] font-semibold transition-colors",
                attendanceStatus === option.value ? "border-slate-800 bg-slate-800 text-white" : "border-slate-200 bg-white text-slate-600 hover:bg-slate-50",
              )}
            >
              {option.label}
            </button>
          ))}
        </div>
        <p className="mt-2 text-[0.64rem] leading-4 text-slate-400">Оцінка та відвідування зберігаються незалежно.</p>
        {hasHomework ? (
          <div className="mt-3 border-t border-slate-200 pt-3">
            <p className="text-[0.7rem] font-semibold text-slate-600">Статус домашнього завдання</p>
            <p className="mt-1 line-clamp-2 text-[0.68rem] leading-4 text-slate-500" title={lesson.homework}>{lesson.homework}</p>
            {lesson.homeworkDeadline ? <p className="mt-1 text-[0.64rem] text-slate-400">Виконати до {formatJournalDate(lesson.homeworkDeadline)}</p> : null}
            <div className="mt-2 grid grid-cols-2 gap-1.5" aria-label="Статус домашнього завдання">
              {homeworkStatuses.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  onClick={() => { setHomeworkStatus(option.value); setError(""); }}
                  className={cn(
                    "h-9 rounded border px-2 text-[0.68rem] font-semibold transition-colors",
                    homeworkStatus === option.value ? "border-slate-800 bg-slate-800 text-white" : "border-slate-200 bg-white text-slate-600 hover:bg-slate-50",
                  )}
                >
                  {option.label}
                </button>
              ))}
            </div>
          </div>
        ) : null}
        <textarea value={comment} onChange={(event) => setComment(event.target.value)} rows={3} maxLength={200} placeholder="Коментар до оцінки" className="mt-3 w-full resize-none rounded-md border border-slate-300 bg-white px-3 py-2 text-[0.76rem] leading-5 text-slate-800 outline-hidden placeholder:text-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200" />
        {error ? <p className="mt-2 text-[0.7rem] text-red-600">{error}</p> : null}
        <div className="mt-3 flex items-center justify-end gap-2">
          {initialValue ? <button type="button" onClick={onDelete} className="inline-flex h-9 items-center gap-1.5 rounded-md border border-red-300 bg-white px-3 text-[0.72rem] font-semibold text-red-600 hover:bg-red-50"><Trash2 className="h-3.5 w-3.5" />Видалити</button> : null}
          <Button type="submit" className="h-9 min-w-[104px] rounded-md px-3 text-xs">
            {initialValue ? <Save className="h-3.5 w-3.5" /> : <Plus className="h-3.5 w-3.5" />}
            {initialValue ? "Оновити" : "Додати"}
          </Button>
        </div>
      </form>
    </section>
  );
}

function formatJournalDate(date) {
  return new Intl.DateTimeFormat("uk-UA", { day: "2-digit", month: "2-digit", year: "numeric" })
    .format(new Date(`${date}T12:00:00`));
}

function UnsavedJournalDialog({ className, subject, period, onClose, onDiscard, onSave }) {
  const panel = useRef(null);

  useEffect(() => {
    const onKeyDown = (event) => { if (event.key === "Escape") onClose(); };
    window.addEventListener("keydown", onKeyDown);
    panel.current?.focus();
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-900/45 p-4" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <section ref={panel} tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby="unsaved-journal-title" className="w-full max-w-[560px] rounded-lg border border-slate-200 bg-white p-5 shadow-2xl outline-hidden sm:p-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 id="unsaved-journal-title" className="text-lg font-bold text-slate-900">Є незбережені зміни</h2>
            <p className="mt-1 text-[0.76rem] text-slate-500">{className} · {subject} · {period}</p>
          </div>
          <button type="button" onClick={onClose} className="flex h-8 w-8 shrink-0 items-center justify-center rounded text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label="Закрити"><X className="h-4 w-4" /></button>
        </div>
        <div className="mt-5 flex gap-3 rounded-md border border-amber-200 bg-amber-50 p-4 text-amber-800">
          <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
          <div className="text-[0.8rem] leading-5">
            <p className="font-semibold">Зміни журналу ще не збережено.</p>
            <p className="mt-1">Якщо продовжити без збереження, поточну чернетку буде втрачено.</p>
          </div>
        </div>
        <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="ghost" onClick={onDiscard} className="h-10 rounded-md border border-red-300 bg-white px-4 text-xs text-red-600 hover:border-red-400 hover:bg-red-50 hover:text-red-700">Вийти без збереження</Button>
          <Button type="button" onClick={onSave} className="h-10 rounded-md px-4 text-xs"><Save className="h-4 w-4" />Зберегти й продовжити</Button>
        </div>
      </section>
    </div>
  );
}

function TeacherTopBar({ onLogout }) {
  return (
    <header className="sticky top-0 z-50 flex h-16 items-center justify-between border-b border-slate-200/80 bg-white px-4 sm:px-5 lg:px-6">
      <a href="#teacher" className="inline-flex min-w-0 items-center gap-2 text-slate-900" aria-label="Електронний щоденник - головна">
        <GraduationCap className="h-7 w-7 shrink-0 sm:h-8 sm:w-8" strokeWidth={2.4} aria-hidden="true" />
        <span className="max-w-[150px] truncate text-[0.92rem] font-bold min-[420px]:max-w-none sm:text-[1.05rem]">Електронний щоденник</span>
      </a>
      <div className="flex items-center gap-1.5 sm:gap-4">
        <NotificationButton />
        <CurrentUserBadge />
        <button type="button" onClick={onLogout} className="inline-flex h-10 items-center gap-2 rounded-md px-3 text-[0.82rem] font-semibold text-slate-500 hover:bg-slate-100 hover:text-slate-900"><LogOut className="h-4 w-4" /><span className="hidden sm:inline">Вийти</span></button>
      </div>
    </header>
  );
}

function TeacherNavigation({ compact = false }) {
  const content = teacherNavigationItems.map((item) => {
    const Icon = item.icon;
    const active = item.route === "teacher/journal";
    const classes = cn(
      compact ? "inline-flex h-10 min-w-0 items-center justify-center gap-1 rounded-md px-1" : "flex h-11 w-full items-center gap-3 rounded-md px-3 text-left",
      "text-[0.82rem] font-semibold transition-colors",
      active ? "bg-slate-100 text-slate-900" : "text-slate-500 hover:bg-slate-50 hover:text-slate-800",
      !item.route && "cursor-default hover:bg-transparent hover:text-slate-500",
    );
    const label = <><Icon className={compact ? "h-4 w-4 shrink-0" : "h-5 w-5 shrink-0"} />{compact ? <span className="hidden truncate min-[540px]:inline">{item.shortLabel || item.label}</span> : item.label}</>;
    return item.route
      ? <a key={item.label} href={`#${item.route}`} className={classes} aria-current={active ? "page" : undefined} aria-label={compact ? item.label : undefined}>{label}</a>
      : <button key={item.label} type="button" disabled className={classes} title={`${item.label}: розділ у розробці`}>{label}</button>;
  });
  if (compact) return <nav className="sticky top-16 z-40 border-b border-slate-200/80 bg-white px-3 py-2 lg:hidden" aria-label="Основна навігація"><div className="grid grid-cols-6 gap-1">{content}</div></nav>;
  return <aside className="hidden w-[210px] shrink-0 bg-white px-3 py-4 lg:block"><nav className="space-y-2" aria-label="Основна навігація">{content}</nav></aside>;
}
