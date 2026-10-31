import { useEffect, useId, useLayoutEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import {
  CalendarDays,
  CheckCircle2,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Ellipsis,
  Eraser,
  GraduationCap,
  LogOut,
  Pencil,
  Plus,
  Save,
  Trash2,
  UsersRound,
  X,
} from "lucide-react";
import { AppFooter } from "@/components/AppFooter.jsx";
import { NotificationButton } from "@/components/NotificationButton.jsx";
import { CurrentUserBadge } from "@/lib/currentUser.jsx";
import { Button } from "@/components/ui/button.jsx";
import { authApi, teacherJournalApi } from "@/lib/api.js";
import { TeacherNavigation as SharedTeacherNavigation } from "@/components/TeacherNavigation.jsx";
import { cn } from "@/lib/utils.js";
import { defaultAssessmentPolicy, gradeDisplay, resultLabels, resultOptions } from "@/lib/assessment.js";
import {
  dateParts,
  homeworkStatuses,
  normalizeLessonForm,
  workTypes,
} from "./journalModel.js";

const gradeValues = Array.from({ length: 12 }, (_, index) => String(index + 1));
const attendanceOptions = [
  { value: "", label: "—", description: "Відвідування не вказано" },
  { value: "PRESENT", label: "П", description: "Присутній" },
  { value: "ABSENT", label: "Н", description: "Відсутній" },
  { value: "EXCUSED_ABSENCE", label: "ПП", description: "Відсутній із поважної причини" },
];
const resultDescriptions = { PASSED: "Зараховано", NOT_PASSED: "Не зараховано", EXEMPTED: "Звільнено від оцінювання" };

function JournalHintButton({ hint, onClick, children, buttonRef, hintDisabled = false, showOnClick = true, ...props }) {
  const button = useRef(null);
  const tooltip = useRef(null);
  const timer = useRef(null);
  const id = useId();
  const [open, setOpen] = useState(false);
  const [position, setPosition] = useState(null);
  const cancelTimer = () => window.clearTimeout(timer.current);
  const show = () => { cancelTimer(); if (!hintDisabled) setOpen(true); };
  const dismiss = () => { cancelTimer(); setOpen(false); setPosition(null); };
  const schedule = () => {
    cancelTimer();
    if (hintDisabled) return;
    timer.current = window.setTimeout(() => setOpen(true), 300);
  };
  useEffect(() => { if (hintDisabled) dismiss(); }, [hintDisabled]);
  useLayoutEffect(() => {
    if (!open || !button.current || !tooltip.current) return;
    const anchor = button.current.getBoundingClientRect();
    const { width, height } = tooltip.current.getBoundingClientRect();
    const margin = 12, gap = 10;
    const aboveSpace = anchor.top - margin - gap;
    const belowSpace = window.innerHeight - anchor.bottom - margin - gap;
    const above = height <= aboveSpace || aboveSpace >= belowSpace;
    const left = Math.max(margin, Math.min(anchor.left + anchor.width / 2 - width / 2, window.innerWidth - width - margin));
    const top = Math.max(margin, Math.min(above ? anchor.top - height - gap : anchor.bottom + gap, window.innerHeight - height - margin));
    setPosition({ left, top, above, arrowLeft: Math.max(10, Math.min(anchor.left + anchor.width / 2 - left, width - 10)) });
  }, [open, hint]);
  useEffect(() => {
    const outside = event => { if (!button.current?.contains(event.target)) dismiss(); };
    const escape = event => { if (event.key === "Escape") dismiss(); };
    document.addEventListener("pointerdown", outside);
    document.addEventListener("scroll", dismiss, true);
    document.addEventListener("keydown", escape);
    window.addEventListener("resize", dismiss);
    return () => {
      cancelTimer();
      document.removeEventListener("pointerdown", outside);
      document.removeEventListener("scroll", dismiss, true);
      document.removeEventListener("keydown", escape);
      window.removeEventListener("resize", dismiss);
    };
  }, []);
  return <>
    <button {...props} ref={node => { button.current = node; if (buttonRef) buttonRef.current = node; }} aria-label={props["aria-label"] || hint} aria-describedby={open ? id : undefined}
      onMouseEnter={schedule} onMouseLeave={dismiss} onFocus={show} onBlur={dismiss}
      onClick={event => { if (showOnClick) show(); else dismiss(); onClick?.(event); }}>{children}</button>
    {open ? createPortal(<span ref={tooltip} id={id} role="tooltip"
      style={{ left: position?.left ?? 0, top: position?.top ?? 0, width: "max-content", maxWidth: "min(280px, calc(100vw - 24px))", visibility: position ? "visible" : "hidden", borderRadius: 6, backgroundColor: "#17212b", color: "#fff" }}
      className="pointer-events-none fixed z-[110] rounded-md px-3 py-2.5 text-center text-xs leading-4 shadow-lg">
      <span className="block wrap-anywhere">{hint}</span>
      <span aria-hidden="true" className="absolute h-2 w-2 rotate-45" style={{ left: (position?.arrowLeft ?? 10) - 4, ...(position?.above ? { bottom: -4 } : { top: -4 }), backgroundColor: "inherit" }} />
    </span>, document.body) : null}
  </>;
}

const workTypeClasses = {
  CLASSWORK: "border-sky-200 bg-sky-50 text-sky-700",
  HOMEWORK: "border-emerald-200 bg-emerald-50 text-emerald-700",
  INDEPENDENT: "border-amber-200 bg-amber-50 text-amber-700",
  CONTROL: "border-red-200 bg-red-50 text-red-600",
  PRACTICAL: "border-teal-200 bg-teal-50 text-teal-700",
  LABORATORY: "border-violet-200 bg-violet-50 text-violet-700",
  THEMATIC: "border-indigo-200 bg-indigo-50 text-indigo-700",
  SEMESTER: "border-lime-200 bg-lime-50 text-lime-800",
  YEAR: "border-fuchsia-200 bg-fuchsia-50 text-fuchsia-800",
};

function sameOptionLabel(left, right) {
  return Boolean(left && right)
    && String(left).trim().toLocaleLowerCase("uk-UA") === String(right).trim().toLocaleLowerCase("uk-UA");
}

export function TeacherJournalPage({ initialClassId = "", initialClassName = "", initialSubjectId = "", initialSubjectName = "", initialMonth = "", initialTarget = null }) {
  const [academicYear, setAcademicYear] = useState(() => {
    if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(initialMonth)) return "";
    const year = Number(initialMonth.slice(0, 4));
    const start = Number(initialMonth.slice(5)) >= 9 ? year : year - 1;
    return `${start}/${start + 1}`;
  });
  const [options, setOptions] = useState(null);
  const [classId, setClassId] = useState(initialClassId);
  const [subjectId, setSubjectId] = useState(initialSubjectId);
  const [period, setPeriod] = useState(initialMonth);
  const [journal, setJournal] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const [lessonEditor, setLessonEditor] = useState(null);
  const [gradeEditor, setGradeEditor] = useState(null);
  const [notice, setNotice] = useState("");
  const selectedClass = options?.classes.find((item) => String(item.id) === String(classId));
  const selectedSubject = selectedClass?.subjects.find((item) => String(item.id) === String(subjectId));
  const matchesLinkedClass = initialClassId
    ? String(classId) === String(initialClassId)
    : sameOptionLabel(selectedClass?.label, initialClassName);
  const matchesLinkedSubject = initialSubjectId
    ? String(subjectId) === String(initialSubjectId)
    : sameOptionLabel(selectedSubject?.label, initialSubjectName);
  const periods = useMemo(() => monthPeriods(academicYear), [academicYear]);

  useEffect(() => {
    let active = true;
    teacherJournalApi.options(academicYear).then((result) => {
      if (!active) return;
      setOptions({ ...result, academicYears: [...new Set([result.currentAcademicYear, ...(result.academicYears || [])].filter(Boolean))] });
      if (!academicYear) setAcademicYear(result.currentAcademicYear || result.academicYears?.[0] || "");
      const linkedClass = result.classes.find((item) => String(item.id) === String(initialClassId))
        || result.classes.find((item) => sameOptionLabel(item.label, initialClassName));
      setClassId((current) => result.classes.some((item) => String(item.id) === String(current))
        ? current : String(linkedClass?.id || result.classes[0]?.id || ""));
    }).catch((requestError) => { if (active) setError(requestError.message || "Не вдалося завантажити класи."); });
    return () => { active = false; };
  }, [academicYear, initialClassId, initialClassName]);

  useEffect(() => {
    setSubjectId((current) => selectedClass?.subjects.some((item) => String(item.id) === String(current))
      ? current : String(selectedClass?.subjects.find((item) => String(item.id) === String(initialSubjectId))?.id
        || selectedClass?.subjects.find((item) => sameOptionLabel(item.label, initialSubjectName))?.id
        || selectedClass?.subjects[0]?.id || ""));
  }, [selectedClass, initialSubjectId, initialSubjectName]);

  useEffect(() => {
    if (!periods.length) return;
    const now = new Date();
    const currentMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
    setPeriod((current) => periods.some((item) => item.value === current)
      ? current : periods.some((item) => item.value === currentMonth) ? currentMonth : periods[0].value);
  }, [periods]);

  useEffect(() => {
    if (!classId || !subjectId || !period) { setJournal(null); setLoading(false); return undefined; }
    let active = true;
    setLoading(true);
    setError("");
    teacherJournalApi.find({ classId, subjectId, month: period }).then((result) => {
      if (active) setJournal(result);
    }).catch((requestError) => {
      if (active) { setJournal(null); setError(requestError.message || "Не вдалося завантажити журнал."); }
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [classId, subjectId, period, reload]);

  useEffect(() => {
    if (!notice) return undefined;
    const timeout = window.setTimeout(() => setNotice(""), 2400);
    return () => window.clearTimeout(timeout);
  }, [notice]);

  const changeClass = (nextClassId) => {
    setClassId(nextClassId);
    setSubjectId("");
    setLessonEditor(null);
    setGradeEditor(null);
  };

  const saveLesson = async (lesson, value) => {
    setSaving(true);
    setError("");
    try {
      await teacherJournalApi.saveLesson(lesson.scheduleId, lesson.date, {
        ...value, workType: value.workType || null, homeworkDeadline: value.homeworkDeadline || null,
      });
      setLessonEditor(null);
      setReload((current) => current + 1);
      setNotice("Дані уроку збережено.");
    } catch (requestError) { setError(requestError.message || "Не вдалося зберегти урок."); }
    finally { setSaving(false); }
  };

  const saveGrade = async (studentId, lesson, value) => {
    setSaving(true);
    setError("");
    try {
      await teacherJournalApi.saveCell(lesson.id, studentId, {
        score: value.grade ? Number(value.grade) : null,
        result: value.result || null,
        attendanceStatus: value.attendanceStatus || null,
        homeworkStatus: lesson.homework ? value.homeworkStatus || null : null,
        comment: value.comment,
      });
      setGradeEditor(null);
      setReload((current) => current + 1);
      setNotice("Запис журналу збережено.");
    } catch (requestError) { setError(requestError.message || "Не вдалося зберегти запис."); }
    finally { setSaving(false); }
  };

  const deleteGrade = (studentId, lesson) => saveGrade(studentId, lesson, {
    grade: "",
    attendanceStatus: "", homeworkStatus: "", comment: "",
  });

  const markAllPresent = async (lesson) => {
    if (!lesson.workType) {
      setError("Спочатку вкажіть тип роботи уроку.");
      setLessonEditor(lesson);
      return;
    }
    if (!lesson.topic?.trim()) {
      setError("Спочатку вкажіть тему уроку.");
      setLessonEditor(lesson);
      return;
    }
    setSaving(true);
    setError("");
    setGradeEditor(null);
    try {
      await teacherJournalApi.markAllPresent(lesson.scheduleId, lesson.date);
      setReload((current) => current + 1);
      setNotice("Усіх учнів позначено присутніми.");
    } catch (requestError) { setError(requestError.message || "Не вдалося позначити присутність."); }
    finally { setSaving(false); }
  };

  const clearAttendance = async (lesson) => {
    if (!lesson.id) return;
    setSaving(true);
    setError("");
    setGradeEditor(null);
    try {
      await teacherJournalApi.clearAttendance(lesson.id);
      setReload((current) => current + 1);
      setNotice("Відвідування уроку очищено.");
    } catch (requestError) { setError(requestError.message || "Не вдалося очистити відвідування."); }
    finally { setSaving(false); }
  };

  const logout = async () => {
    try { await authApi.logout(); } catch { /* The session may already be expired. */ }
    window.location.hash = "login";
  };

  return (
    <div className="flex min-h-screen flex-col bg-slate-50 text-slate-800">
      <TeacherTopBar onLogout={logout} />
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

              {options ? <JournalFilters
                classes={options.classes}
                academicYears={options.academicYears}
                academicYear={academicYear}
                onAcademicYearChange={(value) => { setAcademicYear(value); setClassId(""); setSubjectId(""); setJournal(null); }}
                classId={classId}
                subjectId={subjectId}
                period={period}
                periods={periods}
                subjects={selectedClass?.subjects || []}
                onClassChange={changeClass}
                onSubjectChange={(value) => { setSubjectId(value); setLessonEditor(null); setGradeEditor(null); }}
                onPeriodChange={(value) => { setPeriod(value); setLessonEditor(null); setGradeEditor(null); }}
              /> : null}

              {error ? <p role="alert" className="mt-4 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</p> : null}
              {loading ? <p className="mt-5 text-sm text-slate-500">Завантаження журналу...</p> : null}
              {!loading && !journal && !error ? <p className="mt-5 rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-500">Немає призначених класів або предметів для вибраного періоду.</p> : null}

              {journal ? <section className="mt-5 overflow-hidden rounded-lg border border-slate-200 bg-white shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)]" aria-label="Таблиця журналу">
                <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-4 py-3.5 sm:px-5">
                  <div className="min-w-0">
                    <h2 className="truncate text-[0.95rem] font-bold text-slate-900">{journal.subjectName} · {journal.className}</h2>
                    <p className="mt-1 text-[0.72rem] text-slate-500">{journal.students.length} учнів · {journal.lessons.length} уроків</p>
                  </div>
                </div>

                <JournalTable
                  passFail={journal.assessmentPolicy?.mode === "PASS_FAIL"}
                  key={`${classId}:${subjectId}:${period}`}
                  lessons={journal.lessons}
                  students={journal.students}
                  initialTarget={matchesLinkedClass
                    && matchesLinkedSubject
                    && period === initialMonth ? initialTarget : null}
                  onLessonClick={setLessonEditor}
                  onMarkAllPresent={markAllPresent}
                  onClearAttendance={clearAttendance}
                  onPageChange={() => setGradeEditor(null)}
                  saving={saving}
                  onGradeClick={(event, student, lesson) => {
                    if (!lesson.workType) {
                      setError("Спочатку вкажіть тип роботи уроку.");
                      setLessonEditor(lesson);
                      return;
                    }
                    if (!lesson.id || !lesson.topic?.trim()) {
                      setError("Спочатку вкажіть тему уроку.");
                      setLessonEditor(lesson);
                      return;
                    }
                    setGradeEditor({
                      student,
                      lesson,
                      date: lesson.date,
                      rect: event.currentTarget.getBoundingClientRect(),
                    });
                  }}
                />
              </section> : null}
            </div>
          </main>
          <AppFooter />
        </div>
      </div>

      {lessonEditor ? (
        <LessonDetailsDialog
          date={lessonEditor.date}
          className={journal.className}
          subject={journal.subjectName}
          initialValue={lessonEditor}
          requestError={error}
          saving={saving}
          onClose={() => setLessonEditor(null)}
          onSave={(value) => saveLesson(lessonEditor, value)}
        />
      ) : null}

      {gradeEditor ? (
        <GradePopover
          key={`${gradeEditor.student.id}:${gradeEditor.lesson.scheduleId}:${gradeEditor.date}`}
          editor={gradeEditor}
          policy={journal.assessmentPolicy || defaultAssessmentPolicy}
          initialValue={gradeEditor.lesson.cells.find((cell) => cell.studentId === gradeEditor.student.id)}
          lesson={gradeEditor.lesson}
          requestError={error}
          saving={saving}
          onClose={() => setGradeEditor(null)}
          onSave={(value) => saveGrade(gradeEditor.student.id, gradeEditor.lesson, value)}
          onDelete={() => deleteGrade(gradeEditor.student.id, gradeEditor.lesson)}
        />
      ) : null}

      {notice ? (
        <div role="status" className="fixed bottom-20 right-4 z-[70] flex items-center gap-2 rounded-md border border-emerald-200 bg-white px-4 py-3 text-[0.78rem] font-semibold text-emerald-700 shadow-xl sm:right-5">
          <CheckCircle2 className="h-4 w-4" aria-hidden="true" />{notice}
        </div>
      ) : null}

    </div>
  );
}

function JournalFilters({ classes, academicYears, academicYear, onAcademicYearChange, classId, subjectId, period, periods, subjects, onClassChange, onSubjectChange, onPeriodChange }) {
  return (
    <section className="max-w-6xl rounded-lg border border-slate-200 bg-white p-4 shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)]">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-[minmax(130px,0.8fr)_minmax(150px,1fr)_minmax(150px,1fr)_minmax(210px,1fr)] lg:items-end">
        <SelectField label="Навчальний рік" value={academicYear} onChange={onAcademicYearChange}>
          {academicYears.map((item) => <option key={item} value={item}>{item}</option>)}
        </SelectField>
        <SelectField label="Клас" value={classId} onChange={onClassChange}>
          {classes.length ? classes.map((item) => <option key={item.id} value={item.id}>{item.label}</option>) : <option value="">Немає класів</option>}
        </SelectField>
        <SelectField label="Предмет" value={subjectId} onChange={onSubjectChange}>
          {subjects.length ? subjects.map((item) => <option key={item.id} value={item.id}>{item.label}</option>) : <option value="">Немає предметів</option>}
        </SelectField>
        <PeriodSwitcher value={period} onChange={onPeriodChange} periods={periods} />
      </div>
    </section>
  );
}

function PeriodSwitcher({ value, onChange, periods }) {
  const [open, setOpen] = useState(false);
  const picker = useRef(null);
  const list = useRef(null);
  const activeIndex = periods.findIndex((item) => item.value === value);
  const safeIndex = activeIndex >= 0 ? activeIndex : 0;
  const activePeriod = periods[safeIndex];

  useEffect(() => {
    if (!open) return undefined;
    const closeOutside = (event) => { if (!picker.current?.contains(event.target)) setOpen(false); };
    const closeOnEscape = (event) => { if (event.key === "Escape") setOpen(false); };
    document.addEventListener("pointerdown", closeOutside);
    document.addEventListener("keydown", closeOnEscape);
    list.current?.querySelector('[aria-selected="true"]')?.scrollIntoView({ block: "nearest" });
    return () => {
      document.removeEventListener("pointerdown", closeOutside);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, [open, value]);

  const move = (direction) => {
    const nextPeriod = periods[safeIndex + direction];
    if (nextPeriod) onChange(nextPeriod.value);
  };

  return (
    <div ref={picker} className="relative min-w-0">
      <span className="mb-2 block text-[0.74rem] font-semibold text-slate-600">Період</span>
      <div className="grid h-10 grid-cols-[40px_minmax(0,1fr)_40px] overflow-hidden rounded-md border border-slate-300 bg-white">
        <button
          type="button"
          onClick={() => move(-1)}
          disabled={!periods.length || safeIndex === 0}
          className="flex items-center justify-center text-slate-600 transition-colors hover:bg-slate-100 disabled:cursor-not-allowed disabled:text-slate-300 disabled:hover:bg-transparent"
          aria-label="Попередній період"
        >
          <ChevronLeft className="h-4 w-4" aria-hidden="true" />
        </button>
        <div className="min-w-0 border-x border-slate-200">
          <button
            type="button"
            aria-label="Оберіть місяць журналу"
            aria-haspopup="listbox"
            aria-expanded={open}
            aria-controls="journal-month-options"
            onClick={() => setOpen((current) => !current)}
            onKeyDown={(event) => {
              if (event.key === "ArrowDown" || event.key === "ArrowUp") {
                event.preventDefault();
                setOpen(true);
                move(event.key === "ArrowDown" ? 1 : -1);
              }
            }}
            disabled={!periods.length}
            className="relative flex h-full w-full items-center justify-center bg-white px-7 text-center text-[0.8rem] font-semibold text-slate-700 hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-slate-400 disabled:text-slate-400"
          >
            <span className="truncate">{activePeriod?.label || "Оберіть період"}</span>
            <ChevronDown className="absolute right-2.5 h-3.5 w-3.5 text-slate-400" aria-hidden="true" />
          </button>
        </div>
        <button
          type="button"
          onClick={() => move(1)}
          disabled={!periods.length || safeIndex === periods.length - 1}
          className="flex items-center justify-center text-slate-600 transition-colors hover:bg-slate-100 disabled:cursor-not-allowed disabled:text-slate-300 disabled:hover:bg-transparent"
          aria-label="Наступний період"
        >
          <ChevronRight className="h-4 w-4" aria-hidden="true" />
        </button>
      </div>
      {open ? (
        <div ref={list} id="journal-month-options" role="listbox" aria-label="Місяць журналу" className="absolute inset-x-0 top-full z-50 mt-1 max-h-60 overflow-y-auto rounded-md border border-slate-200 bg-white py-1 shadow-lg">
          {periods.map((item) => (
            <button
              key={item.value}
              type="button"
              role="option"
              aria-selected={item.value === value}
              onClick={() => { onChange(item.value); setOpen(false); }}
              className={cn("block h-9 w-full px-3 text-center text-[0.8rem] font-medium", item.value === value ? "bg-slate-100 text-slate-900" : "text-slate-700 hover:bg-slate-50")}
            >
              {item.label}
            </button>
          ))}
        </div>
      ) : null}
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

function JournalTable({ lessons, students, initialTarget, onLessonClick, onGradeClick, onMarkAllPresent, onClearAttendance, onPageChange, saving, passFail = false }) {
  const tableContainer = useRef(null);
  const [availableWidth, setAvailableWidth] = useState(0);
  const [page, setPage] = useState(null);
  const hasLessons = lessons.length > 0;

  useLayoutEffect(() => {
    const element = tableContainer.current;
    if (!element) return undefined;
    const updateWidth = () => setAvailableWidth(element.clientWidth);
    updateWidth();
    const observer = new ResizeObserver(updateWidth);
    observer.observe(element);
    return () => observer.disconnect();
  }, [hasLessons]);

  const studentColumnWidth = Math.min(210, Math.max(120, Math.floor(availableWidth * 0.18)));
  const estimateColumnWidth = Math.min(140, Math.max(80, Math.floor(availableWidth * 0.1)));
  const lessonColumnWidth = 140;
  const pageSize = Math.min(8, Math.max(1, Math.floor((availableWidth - studentColumnWidth - estimateColumnWidth) / lessonColumnWidth)));
  const pageCount = Math.ceil(lessons.length / pageSize);
  const targetIndex = findTargetLessonIndex(lessons, initialTarget);
  const initialPage = targetIndex >= 0 ? Math.floor(targetIndex / pageSize) : 0;
  const activePage = Math.min(page ?? initialPage, Math.max(0, pageCount - 1));
  const firstLesson = Math.min(activePage * pageSize, Math.max(0, lessons.length - pageSize));
  const visibleLessons = lessons.slice(firstLesson, firstLesson + pageSize);
  const lastLesson = firstLesson + visibleLessons.length;
  const changePage = (nextPage) => {
    onPageChange();
    setPage(nextPage);
  };

  if (!hasLessons) {
    return <div className="flex min-h-[220px] items-center justify-center px-4 text-center text-sm text-slate-500">У цьому місяці немає уроків за розкладом.</div>;
  }
  return (
    <div ref={tableContainer} className="min-w-0">
      <div className="grid grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-2 border-b border-slate-200 px-3 py-2.5 sm:px-5">
        <button type="button" onClick={() => changePage(activePage - 1)} disabled={activePage === 0} className="inline-flex h-9 items-center gap-1 rounded-md border border-slate-200 px-2 text-[0.75rem] font-semibold text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40 sm:px-3" aria-label="Попередні уроки"><ChevronLeft className="h-4 w-4" aria-hidden="true" /><span className="hidden sm:inline">Попередні уроки</span></button>
        <div className="min-w-0 text-center" aria-live="polite"><p className="text-[0.8rem] font-bold text-slate-800">Уроки {firstLesson + 1}–{lastLesson} з {lessons.length}</p><p className="truncate text-[0.7rem] text-slate-500">{formatJournalDate(visibleLessons[0].date)} — {formatJournalDate(visibleLessons.at(-1).date)}</p></div>
        <button type="button" onClick={() => changePage(activePage + 1)} disabled={activePage === pageCount - 1} className="inline-flex h-9 items-center gap-1 rounded-md border border-slate-200 px-2 text-[0.75rem] font-semibold text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40 sm:px-3" aria-label="Наступні уроки"><span className="hidden sm:inline">Наступні уроки</span><ChevronRight className="h-4 w-4" aria-hidden="true" /></button>
      </div>
      <div className="max-h-[calc(100vh-330px)] min-h-[360px] overflow-y-auto overflow-x-hidden">
      <table className="w-full table-fixed border-separate border-spacing-0 text-left">
        <colgroup>
          <col style={{ width: studentColumnWidth }} />
          {visibleLessons.map((lesson) => <col key={`${lesson.scheduleId}:${lesson.date}`} />)}
          <col style={{ width: estimateColumnWidth }} />
        </colgroup>
        <thead className="sticky top-0 z-30 bg-slate-50">
          <tr>
            <th className="sticky left-0 z-40 border-b border-r border-slate-200 bg-slate-50 px-2 py-3 text-[0.72rem] font-semibold text-slate-500 sm:px-4">Учень</th>
            {visibleLessons.map((lesson) => (
              <DateHeader
                key={`${lesson.scheduleId}:${lesson.date}`}
                date={lesson.date}
                lesson={lesson}
                onClick={() => onLessonClick(lesson)}
                onMarkAllPresent={() => onMarkAllPresent(lesson)}
                onClearAttendance={() => onClearAttendance(lesson)}
                studentCount={students.length}
                saving={saving}
              />
            ))}
            <th className="sticky right-0 z-40 border-b border-l border-slate-200 bg-slate-50 px-1 py-3 text-center text-[0.7rem] font-semibold text-slate-500" title={passFail ? "Залікова система" : "Середнє поточних оцінок після останньої виставленої тематичної до кінця вибраного місяця"}>{passFail ? "Система оцінювання" : "Орієнтовна тематична"}</th>
          </tr>
        </thead>
        <tbody>
          {students.map((student, studentIndex) => (
            <tr key={student.id} className="group/row">
              <th className="sticky left-0 z-20 border-b border-r border-slate-200 bg-white px-2 py-3.5 sm:px-4" title={student.name}>
                <div className="flex min-w-0 items-center gap-2 sm:gap-3">
                  <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded bg-slate-100 text-[0.68rem] font-bold text-slate-500">{studentIndex + 1}</span>
                  <span className="min-w-0 truncate text-left text-[0.8rem] font-semibold text-slate-800">{student.name}</span>
                </div>
              </th>
              {visibleLessons.map((lesson) => {
                const cell = lesson.cells.find((item) => item.studentId === student.id);
                return <GradeCell key={`${lesson.scheduleId}:${lesson.date}`} grade={cell} onClick={(event) => onGradeClick(event, student, lesson)} />;
              })}
              <td className="sticky right-0 z-10 border-b border-l border-slate-200 bg-slate-50 px-1 text-center text-[0.8rem] font-bold text-slate-700">
                {passFail ? "Залікова" : formatThematicEstimate(student.thematicEstimate)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      </div>
    </div>
  );
}

function findTargetLessonIndex(lessons, target) {
  if (!target?.date) return -1;
  if (target.lessonId) {
    const index = lessons.findIndex((lesson) => String(lesson.id) === String(target.lessonId));
    if (index >= 0) return index;
  }
  if (target.scheduleId) {
    const index = lessons.findIndex((lesson) => lesson.date === target.date
      && String(lesson.scheduleId) === String(target.scheduleId));
    if (index >= 0) return index;
  }
  return lessons.findIndex((lesson) => lesson.date === target.date
    && String(lesson.lessonNumber) === String(target.lessonNumber));
}

function formatThematicEstimate(value) {
  return Number.isFinite(value) ? value.toLocaleString("uk-UA", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }) : "—";
}

function monthPeriods(academicYear) {
  if (!academicYear) return [];
  const start = Number(academicYear.split("/")[0]);
  const names = ["Січень", "Лютий", "Березень", "Квітень", "Травень", "Червень", "Липень", "Серпень", "Вересень", "Жовтень", "Листопад", "Грудень"];
  return [...[9, 10, 11, 12].map((month) => [start, month]), ...[1, 2, 3, 4, 5].map((month) => [start + 1, month])]
    .map(([year, month]) => ({ value: `${year}-${String(month).padStart(2, "0")}`, label: `${names[month - 1]} ${year}` }));
}

function DateHeader({ date, lesson, onClick, onMarkAllPresent, onClearAttendance, studentCount, saving }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [menuPosition, setMenuPosition] = useState(null);
  const menuButton = useRef(null);
  const menu = useRef(null);
  const parts = dateParts(date);
  const type = lesson?.id ? workTypes.find((item) => item.value === lesson?.workType) : null;
  const today = date === new Date().toLocaleDateString("sv-SE");
  const topic = lesson?.topic || "Не вказано";
  const homework = lesson?.homework || "Не задано";
  const markedCount = lesson.cells.filter((cell) => cell.attendanceStatus).length;
  const presentCount = lesson.cells.filter((cell) => cell.attendanceStatus === "PRESENT").length;
  const homeworkDeadline = lesson?.homeworkDeadline
    ? new Intl.DateTimeFormat("uk-UA", { day: "2-digit", month: "2-digit" }).format(new Date(`${lesson.homeworkDeadline}T12:00:00`))
    : "";

  useEffect(() => {
    if (!menuOpen) return undefined;
    const closeOutside = (event) => {
      if (!menu.current?.contains(event.target) && !menuButton.current?.contains(event.target)) setMenuOpen(false);
    };
    const closeOnEscape = (event) => { if (event.key === "Escape") setMenuOpen(false); };
    const closeOnScroll = () => setMenuOpen(false);
    document.addEventListener("pointerdown", closeOutside);
    document.addEventListener("keydown", closeOnEscape);
    document.addEventListener("scroll", closeOnScroll, true);
    window.addEventListener("resize", closeOnScroll);
    return () => {
      document.removeEventListener("pointerdown", closeOutside);
      document.removeEventListener("keydown", closeOnEscape);
      document.removeEventListener("scroll", closeOnScroll, true);
      window.removeEventListener("resize", closeOnScroll);
    };
  }, [menuOpen]);

  const toggleMenu = () => {
    if (menuOpen) { setMenuOpen(false); return; }
    const rect = menuButton.current.getBoundingClientRect();
    const width = Math.min(260, window.innerWidth - 24);
    const height = 100;
    setMenuPosition({
      width,
      left: Math.max(12, Math.min(rect.right - width, window.innerWidth - width - 12)),
      top: rect.bottom + height + 6 <= window.innerHeight - 12
        ? rect.bottom + 6 : Math.max(12, rect.top - height - 6),
    });
    setMenuOpen(true);
  };

  return (
    <th className={cn("border-b border-r border-slate-200 p-0 text-center align-top", today && "bg-emerald-50")}>
      <div className="relative">
        <button type="button" onClick={onClick} className="group/date flex min-h-[154px] w-full flex-col items-center px-2 py-2.5 text-left hover:bg-slate-100" title="Редагувати дані уроку">
          <span className="max-w-full truncate text-[0.64rem] font-semibold text-slate-500">{parts.weekday}</span>
          <span className="mt-0.5 text-[0.82rem] font-bold text-slate-800">{parts.day}.{parts.month}</span>
          <span className="text-[0.61rem] font-medium text-slate-500">{lesson.lessonNumber}-й урок</span>
          <span className={cn(
            "mt-1 flex h-5 max-w-full items-center truncate rounded px-1.5 text-[0.6rem] font-bold",
            type
              ? cn("border", workTypeClasses[type.value])
              : "text-slate-300 group-hover/date:bg-white group-hover/date:text-slate-500",
          )}>
            {type?.label || (lesson?.id ? "Не вказано" : <Pencil className="h-3 w-3" aria-hidden="true" />)}
          </span>
          <span className="mt-2.5 w-full border-t border-slate-200 pt-2 text-[0.66rem] font-normal leading-4 text-slate-600" title={`Тема: ${topic}`}>
            <span className="block truncate"><span className="font-semibold">Тема:</span> {topic}</span>
            <span className="mt-0.5 block truncate" title={`Домашнє завдання: ${homework}`}><span className="font-semibold">ДЗ:</span> {homework}</span>
            {homeworkDeadline ? <span className="mt-0.5 block truncate text-slate-400"><span className="font-semibold">До:</span> {homeworkDeadline}</span> : null}
          </span>
        </button>
        <JournalHintButton
          buttonRef={menuButton}
          hint="Дії з відвідуванням"
          hintDisabled={menuOpen}
          showOnClick={false}
          type="button"
          onClick={toggleMenu}
          aria-label={`Дії з відвідуванням ${parts.day}.${parts.month}`}
          aria-haspopup="menu"
          aria-expanded={menuOpen}
          className="absolute right-2 top-2 flex h-7 w-7 items-center justify-center rounded-md border border-slate-200 bg-white text-slate-600 shadow-xs hover:border-sky-300 hover:bg-sky-50 hover:text-sky-700 focus-visible:outline-2 focus-visible:outline-sky-400"
        ><Ellipsis className="h-4 w-4" aria-hidden="true" /></JournalHintButton>
      </div>
      {menuOpen && menuPosition ? createPortal(
        <div ref={menu} role="menu" aria-label={`Відвідування ${parts.day}.${parts.month}`} className="fixed z-[100] rounded-lg border border-slate-200 bg-white p-1.5 shadow-xl" style={menuPosition}>
          <button type="button" role="menuitem" disabled={saving || studentCount === 0 || presentCount >= studentCount} onClick={() => { setMenuOpen(false); onMarkAllPresent(); }} className="flex h-9 w-full items-center gap-2 rounded-md bg-sky-50 px-2.5 text-left text-[0.76rem] font-semibold text-sky-800 hover:bg-sky-100 disabled:cursor-not-allowed disabled:opacity-50"><UsersRound className="h-4 w-4 shrink-0" aria-hidden="true" />Позначити всіх присутніми</button>
          <button type="button" role="menuitem" disabled={saving || !lesson.id || markedCount === 0} onClick={() => { setMenuOpen(false); onClearAttendance(); }} className="mt-1 flex h-9 w-full items-center gap-2 rounded-md px-2.5 text-left text-[0.76rem] font-semibold text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"><Eraser className="h-4 w-4 shrink-0" aria-hidden="true" />Очистити відвідування</button>
        </div>, document.body,
      ) : null}
    </th>
  );
}

function GradeCell({ grade, onClick }) {
  const gradeValue = String(gradeDisplay(grade));
  const attendanceStatus = grade?.attendanceStatus || "";
  const attendanceLabel = attendanceStatus === "ABSENT"
    ? "Н"
    : attendanceStatus === "EXCUSED_ABSENCE"
      ? "ПП"
      : attendanceStatus === "LATE"
        ? "Зп"
        : attendanceStatus === "EXEMPTED"
          ? "З"
          : attendanceStatus === "PRESENT"
            ? "П"
            : "";
  const numeric = /^\d+$/.test(gradeValue);
  const title = [
    resultDescriptions[grade?.result],
    grade?.comment,
    attendanceStatus === "PRESENT" && "Присутній",
    attendanceStatus === "ABSENT" && "Відсутній",
    attendanceStatus === "EXCUSED_ABSENCE" && "Відсутній з поважної причини",
    attendanceStatus === "LATE" && "Запізнився",
    attendanceStatus === "EXEMPTED" && "Звільнений від заняття",
  ].filter(Boolean).join(" · ") || "Виставити оцінку";
  return (
    <td className="h-[58px] border-b border-r border-slate-200 p-1.5 text-center">
      <JournalHintButton
        type="button"
        onClick={onClick}
        className={cn(
          "group/cell relative mx-auto flex h-10 w-12 items-center justify-center rounded border text-[0.82rem] font-bold transition-colors",
          !grade && "border-transparent text-slate-300 hover:border-slate-300 hover:bg-slate-50 hover:text-slate-600",
          numeric && Number(gradeValue) >= 10 && "border-sky-300 bg-sky-100 text-sky-700",
          numeric && Number(gradeValue) >= 7 && Number(gradeValue) < 10 && "border-emerald-200 bg-emerald-50 text-emerald-700",
          numeric && Number(gradeValue) >= 4 && Number(gradeValue) < 7 && "border-amber-200 bg-amber-50 text-amber-700",
          numeric && Number(gradeValue) < 4 && "border-red-200 bg-red-50 text-red-600",
          !numeric && gradeValue && "border-slate-300 bg-slate-100 text-slate-700",
          !gradeValue && attendanceStatus === "ABSENT" && "border-red-200 bg-red-50 text-red-600",
          !gradeValue && attendanceStatus === "EXCUSED_ABSENCE" && "border-sky-200 bg-sky-50 text-sky-700",
          !gradeValue && attendanceStatus === "LATE" && "border-amber-200 bg-amber-50 text-amber-700",
          !gradeValue && attendanceStatus === "PRESENT" && "border-emerald-200 bg-emerald-50 text-emerald-700",
          !gradeValue && attendanceStatus === "EXEMPTED" && "border-violet-200 bg-violet-50 text-violet-700",
        )}
        hint={title}
      >
        {gradeValue || attendanceLabel || <Plus className="h-4 w-4 opacity-0 transition-opacity group-hover/cell:opacity-100" aria-hidden="true" />}
        {gradeValue && attendanceLabel ? (
          <span className={cn(
            "absolute -right-1 -top-1 flex min-h-4 min-w-4 items-center justify-center rounded px-1 text-[0.52rem] font-bold leading-4 shadow-xs",
            attendanceStatus === "ABSENT" && "bg-red-100 text-red-700",
            attendanceStatus === "EXCUSED_ABSENCE" && "bg-sky-100 text-sky-700",
            attendanceStatus === "LATE" && "bg-amber-100 text-amber-700",
            attendanceStatus === "PRESENT" && "bg-emerald-100 text-emerald-700",
            attendanceStatus === "EXEMPTED" && "bg-violet-100 text-violet-700",
          )}>{attendanceLabel}</span>
        ) : null}
      </JournalHintButton>
    </td>
  );
}

function LessonDetailsDialog({ date, className, subject, initialValue, requestError, saving, onClose, onSave }) {
  const [form, setForm] = useState(() => normalizeLessonForm(initialValue));
  const panel = useRef(null);
  const datePicker = useRef(null);
  const [deadlineText, setDeadlineText] = useState(() => isoDateToUkrainian(form.homeworkDeadline));
  const [deadlineError, setDeadlineError] = useState("");
  const [topicError, setTopicError] = useState("");
  const [workTypeError, setWorkTypeError] = useState("");
  const hasPersistedJournalData = Boolean(
    initialValue?.topic?.trim()
    || initialValue?.homework?.trim()
    || initialValue?.cells?.some((cell) => cell.score != null || cell.result || cell.attendanceStatus || cell.homeworkStatus || cell.comment?.trim()),
  );
  const hasLessonData = hasPersistedJournalData || Boolean(form.topic.trim() || form.homework.trim());
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
    if (!form.topic.trim() && (date <= new Date().toLocaleDateString("sv-SE") || form.homework.trim())) {
      setTopicError("Для проведеного уроку та призначення ДЗ необхідно вказати тему.");
      return;
    }
    if (deadlineText && !ukrainianDateToIso(deadlineText)) {
      setDeadlineError("Введіть коректну дату у форматі дд.мм.рррр.");
      return;
    }
    if (!form.workType && hasLessonData) {
      setWorkTypeError("Порожній тип можна зберегти лише якщо в уроці немає теми, ДЗ, відвідування чи оцінок.");
      return;
    }
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
          {requestError ? <p role="alert" className="rounded border border-red-200 bg-red-50 p-2 text-xs text-red-700">{requestError}</p> : null}
          {topicError ? <p role="alert" className="rounded border border-red-200 bg-red-50 p-2 text-xs text-red-700">{topicError}</p> : null}
          <SelectField label="Тип роботи" value={form.workType} onChange={(value) => { setWorkTypeError(""); setForm((current) => ({ ...current, workType: value })); }}>
            <option value="" disabled={hasLessonData}>Не вказано</option>
            {workTypes.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
          </SelectField>
          {workTypeError ? <p role="alert" className="-mt-2 text-[0.68rem] text-red-600">{workTypeError}</p> : !form.workType ? <p className="-mt-2 text-[0.68rem] text-slate-500">Порожній тип доступний лише для уроку без теми, ДЗ, відвідування та оцінок.</p> : null}
          <TextAreaField label="Тема уроку" value={form.topic} maxLength={200} rows={3} placeholder="Вкажіть тему уроку" onChange={(value) => { setTopicError(""); setForm((current) => ({ ...current, topic: value })); }} />
          <TextAreaField label="Домашнє завдання" value={form.homework} maxLength={500} rows={4} placeholder="Вкажіть домашнє завдання" onChange={(value) => setForm((current) => ({ ...current, homework: value }))} />
          <label className="block">
            <span className="mb-2 block text-[0.74rem] font-semibold text-slate-600">Дедлайн ДЗ <span className="font-normal text-slate-400">(необов’язково)</span></span>
            <div className="relative">
              <input
                type="text"
                inputMode="numeric"
                placeholder="дд.мм.рррр"
                maxLength={10}
                value={deadlineText}
                disabled={!form.homework.trim()}
                onChange={(event) => {
                  const nextText = formatUkrainianDateInput(event.target.value);
                  setDeadlineText(nextText);
                  setForm((current) => ({ ...current, homeworkDeadline: ukrainianDateToIso(nextText) }));
                  setDeadlineError("");
                }}
                className="h-10 w-full rounded-md border border-slate-300 bg-white px-3 pr-11 text-[0.8rem] font-medium text-slate-700 outline-hidden transition-colors placeholder:text-slate-400 hover:border-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400"
                aria-label="Дедлайн домашнього завдання, день.місяць.рік"
              />
              <button
                type="button"
                disabled={!form.homework.trim()}
                onClick={() => {
                  const picker = datePicker.current;
                  if (typeof picker?.showPicker === "function") picker.showPicker();
                  else picker?.click();
                }}
                className="absolute inset-y-0 right-0 flex w-10 items-center justify-center text-slate-400 hover:text-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
                aria-label="Обрати дедлайн у календарі"
              ><CalendarDays className="h-4 w-4" /></button>
              <input
                ref={datePicker}
                type="date"
                value={form.homeworkDeadline || ""}
                min={date}
                disabled={!form.homework.trim()}
                onChange={(event) => {
                  setForm((current) => ({ ...current, homeworkDeadline: event.target.value }));
                  setDeadlineText(isoDateToUkrainian(event.target.value));
                  setDeadlineError("");
                }}
                tabIndex={-1}
                aria-hidden="true"
                className="pointer-events-none absolute right-2 top-1/2 h-px w-px -translate-y-1/2 opacity-0"
              />
            </div>
            {deadlineError ? <span role="alert" className="mt-1 block text-[0.68rem] text-red-600">{deadlineError}</span> : null}
          </label>
          <div className="flex justify-end pt-1">
            <Button type="submit" disabled={saving} className="h-10 rounded-md px-4 text-xs"><CheckCircle2 className="h-4 w-4" />{saving ? "Збереження..." : "Застосувати"}</Button>
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

export function GradePopover({ editor, initialValue, lesson, policy = defaultAssessmentPolicy, requestError, saving, onClose, onSave, onDelete }) {
  const [grade, setGrade] = useState(initialValue?.score ? String(initialValue.score) : "");
  const [result, setResult] = useState(initialValue?.result || "");
  const [attendanceStatus, setAttendanceStatus] = useState(initialValue?.attendanceStatus || "");
  const hasHomework = Boolean(lesson?.homework?.trim());
  const [homeworkStatus, setHomeworkStatus] = useState(initialValue?.homeworkStatus || "");
  const [comment, setComment] = useState(initialValue?.comment || "");
  const [error, setError] = useState("");
  const panel = useRef(null);
  const [position, setPosition] = useState({ left: 12, top: 12, width: 320 });

  useLayoutEffect(() => {
    let frame;
    const placePanel = () => {
      const width = Math.min(320, window.innerWidth - 24);
      const height = panel.current?.getBoundingClientRect().height || 0;
      const left = Math.max(12, Math.min(editor.rect.left + editor.rect.width / 2 - width / 2, window.innerWidth - width - 12));
      const below = editor.rect.bottom + 8;
      const above = editor.rect.top - height - 8;
      const top = below + height <= window.innerHeight - 12
        ? below
        : above >= 12 ? above : Math.max(12, window.innerHeight - height - 12);
      setPosition({ left, top, width });
    };
    const placeAfterResize = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(placePanel);
    };
    placePanel();
    window.addEventListener("resize", placeAfterResize);
    window.visualViewport?.addEventListener("resize", placeAfterResize);
    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener("resize", placeAfterResize);
      window.visualViewport?.removeEventListener("resize", placeAfterResize);
    };
  }, [editor.rect, hasHomework, error, requestError]);

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
    if (!grade && !result && !attendanceStatus && !homeworkStatus) {
      setError("Оберіть оцінку, відвідування або статус домашнього завдання.");
      return;
    }
    onSave({
      grade,
      result,
      attendanceStatus,
      homeworkStatus: hasHomework ? homeworkStatus : "",
      comment: comment.trim().replace(/\s+/g, " "),
    });
  };

  return (
    <section ref={panel} role="dialog" aria-label={`Оцінка: ${editor.student.name}`} className="fixed z-[90] flex max-h-[calc(100dvh-24px)] flex-col overflow-hidden rounded-lg border border-slate-200 bg-white shadow-2xl" style={position}>
      <div className="flex shrink-0 items-start justify-between gap-3 px-4 pt-4">
        <div className="min-w-0">
          <h2 className="truncate text-[0.86rem] font-bold text-slate-900">{editor.student.name}</h2>
          <p className="mt-1 text-[0.68rem] text-slate-500">{dateParts(editor.date).day}.{dateParts(editor.date).month}</p>
        </div>
        <button type="button" onClick={onClose} className="flex h-7 w-7 shrink-0 items-center justify-center rounded text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label="Закрити"><X className="h-4 w-4" /></button>
      </div>
      <form onSubmit={submit} className="flex min-h-0 flex-col">
        <div className="min-h-0 overflow-y-auto px-4 pt-3">
        {policy.mode !== "PASS_FAIL" || resultOptions(policy,lesson.workType).length ? <p className="mb-2 text-[0.7rem] font-semibold text-slate-600">Результат</p> : null}
        {policy.mode !== "PASS_FAIL" ? <>
        <div className="grid grid-cols-6 gap-1.5" aria-label="Оцінка">
          {gradeValues.map((gradeOption) => (
            <button
              key={gradeOption}
              type="button"
              onClick={() => { setGrade((current) => current === gradeOption ? "" : gradeOption); setResult(""); setError(""); }}
              className={cn(
                "h-8 rounded border text-[0.74rem] font-semibold transition-colors",
                grade === gradeOption ? "border-slate-800 bg-slate-800 text-white" : "border-slate-200 bg-white text-slate-600 hover:bg-slate-50",
              )}
            >
              {gradeOption}
            </button>
          ))}
        </div>
        </> : null}
        {resultOptions(policy,lesson.workType).length ? <div className="mt-2 flex justify-center gap-1.5" aria-label="Нечисловий результат">
          {resultOptions(policy,lesson.workType).map(option => <JournalHintButton key={option} type="button" hint={resultDescriptions[option]} aria-pressed={result===option} onClick={() => {setResult(current=>current===option?"":option);setGrade("");setError("");}} className={cn("h-9 w-[calc((100%-0.75rem)/3)] shrink-0 rounded border text-xs font-semibold",result===option?"border-slate-800 bg-slate-800 text-white":"border-slate-200 text-slate-700 hover:bg-slate-50")}>{resultLabels[option]}</JournalHintButton>)}
        </div> : null}
        <p className="mt-3 text-[0.7rem] text-slate-500">Тип роботи: <span className="font-semibold text-slate-700">{workTypes.find((item) => item.value === lesson.workType)?.label || "Не вказано"}</span></p>
        <textarea value={comment} onChange={(event) => setComment(event.target.value)} rows={2} maxLength={200} placeholder="Коментар до оцінки" className="mt-3 w-full resize-none rounded-md border border-slate-300 bg-white px-3 py-2 text-[0.76rem] leading-5 text-slate-800 outline-hidden placeholder:text-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200" />
        <p className="mb-2 mt-3 text-[0.7rem] font-semibold text-slate-600">Відвідування</p>
        <div className="grid grid-cols-4 gap-1.5" aria-label="Статус відвідування">
          {(initialValue?.attendanceStatus === "EXEMPTED" ? [...attendanceOptions,{value:"EXEMPTED",label:"З (архів)",description:"Звільнений від заняття (архівна позначка)"}] : attendanceOptions).map((option) => (
            <JournalHintButton
              key={option.value}
              type="button"
              hint={option.description}
              aria-pressed={attendanceStatus === option.value}
              onClick={() => { setAttendanceStatus(option.value); setError(""); }}
              className={cn(
                "h-9 rounded border px-1 text-[0.68rem] font-semibold transition-colors",
                attendanceStatus === option.value ? "border-slate-800 bg-slate-800 text-white" : "border-slate-200 bg-white text-slate-600 hover:bg-slate-50",
              )}
            >
              {option.label}
            </JournalHintButton>
          ))}
        </div>
        <p className="mt-2 text-[0.64rem] leading-4 text-slate-400">Оцінка та відвідування зберігаються незалежно.</p>
        {hasHomework ? (
          <div className="mt-3 border-t border-slate-200 pb-3 pt-3">
            <p className="text-[0.7rem] font-semibold text-slate-600">Статус домашнього завдання</p>
            <p className="mt-1 line-clamp-2 text-[0.68rem] leading-4 text-slate-500" title={lesson.homework}>{lesson.homework}</p>
            {lesson.homeworkDeadline ? <p className="mt-1 text-[0.64rem] text-slate-400">Виконати до {formatJournalDate(lesson.homeworkDeadline)}</p> : null}
            <div className="relative mt-2">
              <select value={homeworkStatus} onChange={(event) => { setHomeworkStatus(event.target.value); setError(""); }} aria-label="Статус домашнього завдання" className="h-10 w-full appearance-none rounded-md border border-slate-300 bg-white px-3 pr-9 text-[0.76rem] font-medium text-slate-700 outline-hidden focus:border-slate-500 focus:ring-2 focus:ring-slate-200">
                {homeworkStatuses.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
              </select>
              <ChevronDown className="pointer-events-none absolute right-3 top-3 h-4 w-4 text-slate-500" aria-hidden="true" />
            </div>
          </div>
        ) : null}
        {error || requestError ? <p role="alert" className="mt-2 text-[0.7rem] text-red-600">{error || requestError}</p> : null}
        </div>
        <div className="flex shrink-0 items-center justify-end gap-2 border-t border-slate-200 bg-white px-4 py-3">
          {initialValue ? <button type="button" disabled={saving} onClick={onDelete} className="inline-flex h-9 items-center gap-1.5 rounded-md border border-red-300 bg-white px-3 text-[0.72rem] font-semibold text-red-600 hover:bg-red-50 disabled:opacity-50"><Trash2 className="h-3.5 w-3.5" />Видалити</button> : null}
          <Button type="submit" disabled={saving} className="h-9 min-w-[104px] rounded-md px-3 text-xs">
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

function formatUkrainianDateInput(value) {
  const digits = value.replace(/\D/g, "").slice(0, 8);
  return [digits.slice(0, 2), digits.slice(2, 4), digits.slice(4, 8)].filter(Boolean).join(".");
}

function ukrainianDateToIso(value) {
  const match = /^(\d{2})\.(\d{2})\.(\d{4})$/.exec(value);
  if (!match) return "";
  const [, day, month, year] = match;
  const parsed = new Date(Number(year), Number(month) - 1, Number(day));
  if (parsed.getFullYear() !== Number(year) || parsed.getMonth() !== Number(month) - 1 || parsed.getDate() !== Number(day)) return "";
  return `${year}-${month}-${day}`;
}

function isoDateToUkrainian(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value || "");
  return match ? `${match[3]}.${match[2]}.${match[1]}` : "";
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
  return <SharedTeacherNavigation activeRoute="teacher/journal" compact={compact} />;
}
