import { useCallback, useEffect, useMemo, useState } from "react";
import { BookOpen, CheckCircle2, ChevronDown, Loader2, Pencil, Plus, RefreshCw, Search, Trash2, UserRound, Users, X } from "lucide-react";
import { Alert } from "@/components/ui/alert.jsx";
import { Button } from "@/components/ui/button.jsx";
import { Card } from "@/components/ui/card.jsx";
import { Input } from "@/components/ui/input.jsx";
import { ApiError, adminSubjectsApi } from "@/lib/api.js";
import { cn } from "@/lib/utils.js";
import { AdminLayout } from "@/pages/AdminHome/AdminLayout.jsx";

const teacherStatusLabels = { ACTIVE: "Активний", BLOCKED: "Заблокований", DELETED: "Видалений" };

function errorMessage(error, fallback) {
  return error instanceof ApiError ? error.message : fallback;
}

function normalizeSearch(value) {
  return value.trim().toLocaleLowerCase("uk-UA");
}

export function AdminSubjectsPage() {
  const [subjects, setSubjects] = useState([]);
  const [searchInput, setSearchInput] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [editingSubject, setEditingSubject] = useState(null);
  const [deletingSubject, setDeletingSubject] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setSubjects(await adminSubjectsApi.findAll());
    } catch (requestError) {
      setError(errorMessage(requestError, "Не вдалося завантажити предмети."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const visibleSubjects = useMemo(() => {
    const query = normalizeSearch(searchInput);
    return subjects.filter((subject) => normalizeSearch(subject.title).includes(query));
  }, [searchInput, subjects]);

  const completeAction = async (message) => {
    setEditingSubject(null);
    setDeletingSubject(null);
    setNotice(message);
    await load();
  };

  return (
    <AdminLayout
      activeItem="Предмети"
      title="Предмети"
      subtitle="Створення навчальних предметів і призначення вчителів."
      actions={<Button onClick={() => setEditingSubject({})} className="h-10 w-full rounded-md px-4 sm:w-auto"><Plus className="h-4 w-4" />Створити предмет</Button>}
    >
      <Card className="w-full max-w-2xl rounded-lg p-4 sm:p-5">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <label className="block min-w-0 flex-1">
            <span className="mb-2 block text-[0.75rem] font-semibold text-slate-600">Пошук предмета</span>
            <span className="relative block">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
              <Input value={searchInput} onChange={(event) => setSearchInput(event.target.value)} placeholder="Назва предмета" aria-label="Пошук предметів" className="h-10 pl-10" />
            </span>
          </label>
          <Button type="button" variant="ghost" size="icon" onClick={load} disabled={loading} className="h-10 w-10 shrink-0 rounded-md border border-slate-200 bg-white" title="Оновити список">
            <RefreshCw className={cn("h-4 w-4", loading && "animate-spin")} aria-hidden="true" />
          </Button>
        </div>
      </Card>

      {notice ? <div role="status" className="mt-5 flex items-center gap-2 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700"><CheckCircle2 className="h-4 w-4 shrink-0" />{notice}</div> : null}
      {error ? <div className="mt-5"><Alert>{error}</Alert></div> : null}

      {!error && loading ? <div className="flex min-h-[320px] items-center justify-center"><Loader2 className="h-6 w-6 animate-spin text-slate-400" aria-label="Завантаження" /></div> : null}
      {!error && !loading && !visibleSubjects.length ? <EmptyState query={searchInput.trim()} onCreate={() => setEditingSubject({})} /> : null}
      {!error && !loading && visibleSubjects.length ? <SubjectsList subjects={visibleSubjects} onEdit={setEditingSubject} onDelete={setDeletingSubject} /> : null}

      {editingSubject ? <SubjectDialog subject={editingSubject.id ? editingSubject : null} onClose={() => setEditingSubject(null)} onCompleted={completeAction} /> : null}
      {deletingSubject ? <DeleteSubjectDialog subject={deletingSubject} onClose={() => setDeletingSubject(null)} onCompleted={completeAction} /> : null}
    </AdminLayout>
  );
}

function SubjectsList({ subjects, onEdit, onDelete }) {
  return <Card className="mt-5 overflow-hidden rounded-lg p-0"><div className="hidden overflow-x-auto md:block"><table className="w-full min-w-[760px] border-collapse text-left"><thead className="bg-slate-50 text-xs font-bold uppercase text-slate-400"><tr><th className="px-5 py-3">Предмет</th><th className="px-5 py-3">Вчителі</th><th className="px-5 py-3 text-right">Дії</th></tr></thead><tbody className="divide-y divide-slate-100">{subjects.map((subject) => <SubjectRow key={subject.id} subject={subject} onEdit={onEdit} onDelete={onDelete} />)}</tbody></table></div><div className="divide-y divide-slate-100 md:hidden">{subjects.map((subject) => <SubjectCard key={subject.id} subject={subject} onEdit={onEdit} onDelete={onDelete} />)}</div><div className="border-t border-slate-200 px-5 py-3 text-xs text-slate-500">{subjects.length} {subjectWord(subjects.length)}</div></Card>;
}

function SubjectRow({ subject, onEdit, onDelete }) {
  return <tr className="text-sm transition-colors hover:bg-slate-50/70"><td className="px-5 py-4"><div className="flex items-center gap-3"><span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-slate-100 text-slate-600"><BookOpen className="h-4 w-4" /></span><span className="font-bold text-slate-900">{subject.title}</span></div></td><td className="px-5 py-4"><TeacherList teachers={subject.teachers} /></td><td className="px-5 py-4"><SubjectActions subject={subject} onEdit={onEdit} onDelete={onDelete} alignRight /></td></tr>;
}

function SubjectCard({ subject, onEdit, onDelete }) {
  return <article className="p-4"><div className="flex items-center gap-3"><span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-slate-100 text-slate-600"><BookOpen className="h-4 w-4" /></span><div className="min-w-0"><h2 className="truncate text-sm font-bold text-slate-900">{subject.title}</h2><p className="mt-0.5 text-xs text-slate-500">{subject.teachers.length} {teacherWord(subject.teachers.length)}</p></div></div><div className="mt-4"><TeacherList teachers={subject.teachers} /></div><div className="mt-4"><SubjectActions subject={subject} onEdit={onEdit} onDelete={onDelete} /></div></article>;
}

function TeacherList({ teachers }) {
  if (!teachers.length) return <span className="text-xs text-slate-400">Не призначено</span>;
  return <div className="flex flex-wrap gap-1.5">{teachers.map((teacher) => <span key={teacher.id} className={cn("inline-flex rounded-md border px-2 py-1 text-xs", teacher.status === "ACTIVE" ? "border-slate-200 bg-white text-slate-600" : "border-amber-200 bg-amber-50 text-amber-700")} title={teacherStatusLabels[teacher.status]}>{teacher.fullName}</span>)}</div>;
}

function SubjectActions({ subject, onEdit, onDelete, alignRight = false }) {
  const actionClass = "inline-flex h-8 items-center gap-1.5 rounded-md border bg-white px-2.5 text-xs font-bold transition-colors focus-visible:outline-hidden focus-visible:ring-2";
  return <div className={cn("flex flex-wrap gap-2", alignRight && "justify-end")}><button type="button" onClick={() => onEdit(subject)} className={cn(actionClass, "border-slate-300 bg-white text-slate-700 hover:bg-slate-100 focus-visible:ring-slate-400")}><Pencil className="h-3.5 w-3.5" />Редагувати</button><button type="button" onClick={() => onDelete(subject)} className={cn(actionClass, "border-red-300 bg-red-50 text-red-700 hover:bg-red-100 focus-visible:ring-red-400")}><Trash2 className="h-3.5 w-3.5" />Видалити</button></div>;
}

function EmptyState({ query, onCreate }) {
  return <Card className="mt-5 flex min-h-[300px] flex-col items-center justify-center rounded-lg px-5 text-center"><span className="flex h-12 w-12 items-center justify-center rounded-md bg-slate-100 text-slate-500"><BookOpen className="h-5 w-5" /></span><h2 className="mt-4 text-base font-bold text-slate-900">{query ? "Предметів не знайдено" : "Предметів ще немає"}</h2><p className="mt-1 text-sm text-slate-500">{query ? "Змініть пошуковий запит." : "Створіть перший навчальний предмет."}</p>{!query ? <Button onClick={onCreate} className="mt-5 h-9 rounded-md"><Plus className="h-4 w-4" />Створити предмет</Button> : null}</Card>;
}

export function SubjectDialog({ subject, onClose, onCompleted }) {
  const editing = Boolean(subject);
  const [title, setTitle] = useState(subject?.title || "");
  const [assessmentPolicy, setAssessmentPolicy] = useState(subject?.assessmentPolicy || { mode: "NUMERIC", allowPassed: false, allowExempted: false });
  const [teacherIds, setTeacherIds] = useState(() => subject?.teachers.map((teacher) => teacher.id) || []);
  const [teacherQuery, setTeacherQuery] = useState("");
  const [teachers, setTeachers] = useState([]);
  const [loadingOptions, setLoadingOptions] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [fieldError, setFieldError] = useState("");

  useEffect(() => {
    let active = true;
    adminSubjectsApi.getOptions()
      .then((result) => active && setTeachers(result.teachers || []))
      .catch((requestError) => active && setError(errorMessage(requestError, "Не вдалося завантажити вчителів.")))
      .finally(() => active && setLoadingOptions(false));
    return () => { active = false; };
  }, []);

  const visibleTeachers = useMemo(() => {
    const normalized = teacherQuery.trim().toLocaleLowerCase("uk");
    return normalized ? teachers.filter((teacher) => teacher.fullName.toLocaleLowerCase("uk").includes(normalized)) : teachers;
  }, [teacherQuery, teachers]);

  const toggleTeacher = (teacherId) => setTeacherIds((current) => current.includes(teacherId) ? current.filter((id) => id !== teacherId) : [...current, teacherId]);

  const submit = async (event) => {
    event.preventDefault();
    const normalizedTitle = title.trim().replace(/\s+/g, " ");
    if (normalizedTitle.length < 2 || normalizedTitle.length > 50) {
      setFieldError("Назва предмета має містити від 2 до 50 символів");
      return;
    }
    setSubmitting(true);
    setError("");
    setFieldError("");
    try {
      const payload = { title: normalizedTitle, teacherIds, assessmentPolicy };
      if (editing) await adminSubjectsApi.update(subject.id, payload);
      else await adminSubjectsApi.create(payload);
      await onCompleted(editing ? `Предмет «${normalizedTitle}» оновлено.` : `Предмет «${normalizedTitle}» створено.`);
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.fieldErrors?.title) setFieldError(requestError.fieldErrors.title);
      setError(errorMessage(requestError, "Не вдалося зберегти предмет."));
      setSubmitting(false);
    }
  };

  return (
    <ModalFrame
      title={editing ? "Редагувати предмет" : "Створити предмет"}
      subtitle="Вкажіть назву та оберіть одного або кількох учителів."
      onClose={onClose}
      busy={submitting}
    >
      <form onSubmit={submit} className="mt-5 space-y-5">
        <label className="block text-sm font-semibold text-slate-700">
          Назва предмета
          <Input
            value={title}
            onChange={(event) => { setTitle(event.target.value); setFieldError(""); }}
            maxLength={50}
            autoFocus
            placeholder="Наприклад, Математика"
            className={cn("mt-2", fieldError && "border-red-400")}
          />
          {fieldError ? <span className="mt-1.5 block text-xs font-normal text-red-500">{fieldError}</span> : null}
        </label>

        <fieldset disabled={submitting} className="space-y-3 border-y border-slate-200 py-4">
          <label className="block text-sm font-semibold text-slate-700">Система оцінювання
            <select value={assessmentPolicy.mode} onChange={event => setAssessmentPolicy(current => ({ ...current, mode: event.target.value }))} disabled={Boolean(subject?.hasResults)} title={subject?.hasResults ? "Є внесені результати цього предмета" : undefined} className="mt-2 h-10 w-full rounded-md border border-slate-300 bg-white px-3 text-sm font-normal text-slate-700 disabled:bg-slate-50">
              <option value="NUMERIC">Бальна: 1–12</option><option value="PASS_FAIL">Зараховано / Не зараховано</option>
            </select>
          </label>
          <label className="flex items-start gap-2 text-sm text-slate-700"><input className="mt-1 shrink-0" type="checkbox" checked={assessmentPolicy.mode === "PASS_FAIL" || assessmentPolicy.allowPassed} disabled={assessmentPolicy.mode === "PASS_FAIL"} onChange={event => setAssessmentPolicy(current => ({ ...current, allowPassed: event.target.checked }))} /><span>Дозволити «Зараховано» замість підсумкової оцінки окремим учням</span></label>
          <label className="flex items-start gap-2 text-sm text-slate-700"><input className="mt-1 shrink-0" type="checkbox" checked={assessmentPolicy.allowExempted} onChange={event => setAssessmentPolicy(current => ({ ...current, allowExempted: event.target.checked }))} /><span>Дозволити «Звільнено» у підсумкових результатах окремих учнів</span></label>
        </fieldset>
        <div>
          <div className="mb-2 flex items-end justify-between gap-3">
            <div>
              <h3 className="text-sm font-semibold text-slate-700">Вчителі</h3>
              <p className="mt-1 text-xs text-slate-500">Обрано: {teacherIds.length}</p>
            </div>
          </div>
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-slate-400" />
            <Input value={teacherQuery} onChange={(event) => setTeacherQuery(event.target.value)} placeholder="Пошук учителя" aria-label="Пошук учителя" className="pl-10" />
          </div>
          <div className="mt-2 max-h-72 divide-y divide-slate-100 overflow-y-auto rounded-md border border-slate-200">
            {loadingOptions ? (
              <div className="flex justify-center p-6"><Loader2 className="h-5 w-5 animate-spin text-slate-400" /></div>
            ) : visibleTeachers.length ? visibleTeachers.map((teacher) => {
              const selected = teacherIds.includes(teacher.id);
              const unavailable = teacher.status !== "ACTIVE" && !selected;
              const alreadyAssigned = Boolean(subject?.id && teacher.subjectIds?.includes(subject.id));
              return (
                <TeacherOption
                  key={teacher.id}
                  teacher={teacher}
                  selected={selected}
                  unavailable={unavailable}
                  alreadyAssigned={alreadyAssigned}
                  onToggle={() => toggleTeacher(teacher.id)}
                />
              );
            }) : <p className="p-5 text-sm text-slate-500">Учителів не знайдено.</p>}
          </div>
        </div>

        {error ? <Alert>{error}</Alert> : null}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose} disabled={submitting} className="h-10 rounded-md border border-slate-300 bg-white px-4">Скасувати</Button>
          <Button type="submit" disabled={submitting || loadingOptions} className="h-10 min-w-[120px] rounded-md px-4">
            {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            {editing ? "Зберегти" : "Створити"}
          </Button>
        </div>
      </form>
    </ModalFrame>
  );
}

function TeacherOption({ teacher, selected, unavailable, alreadyAssigned, onToggle }) {
  const subjectTitles = teacher.subjectTitles || [];
  const classNames = teacher.classNames || [];
  return (
    <div className={cn("p-3 text-sm", unavailable ? "bg-slate-50 text-slate-400" : "hover:bg-slate-50")}>
      <div className="flex items-start gap-3">
        <label className={cn("flex min-w-0 flex-1 items-center gap-3", unavailable ? "cursor-not-allowed" : "cursor-pointer")}>
          <input type="checkbox" checked={selected} disabled={unavailable} onChange={onToggle} />
          <UserRound className="h-4 w-4 shrink-0" />
          <span className="min-w-0 truncate font-medium">{teacher.fullName}</span>
        </label>
        <div className="flex shrink-0 flex-col items-end gap-1.5">
          {alreadyAssigned ? <span className="rounded-md border border-emerald-200 bg-emerald-50 px-2 py-0.5 text-[0.68rem] font-semibold text-emerald-700">Вже викладає</span> : null}
          {teacher.status !== "ACTIVE" ? <span className="text-xs font-medium text-amber-600">{teacherStatusLabels[teacher.status]}</span> : null}
        </div>
      </div>
      <details className="group mt-2 pl-7">
        <summary className="inline-flex cursor-pointer list-none items-center gap-1 text-xs font-semibold text-slate-500 hover:text-slate-800">
          {subjectTitles.length} {subjectWord(subjectTitles.length)} · {classNames.length} {classWord(classNames.length)}
          <ChevronDown className="h-3.5 w-3.5 transition-transform group-open:rotate-180" />
        </summary>
        <div className="mt-2 grid gap-2 rounded-md border border-slate-200 bg-white p-3 text-xs leading-5 text-slate-600 sm:grid-cols-2">
          <LoadDetail label="Предмети" values={subjectTitles} empty="Ще не призначено" />
          <LoadDetail label="Класи" values={classNames} empty="У розкладі немає класів" />
        </div>
      </details>
    </div>
  );
}

function LoadDetail({ label, values, empty }) {
  return <div><div className="font-bold text-slate-700">{label}</div><div className="mt-0.5">{values.length ? values.join(", ") : empty}</div></div>;
}

function DeleteSubjectDialog({ subject, onClose, onCompleted }) {
  const [eligibility, setEligibility] = useState(null);
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let active = true;
    adminSubjectsApi.getDeletionEligibility(subject.id)
      .then((result) => active && setEligibility(result))
      .catch((requestError) => active && setError(errorMessage(requestError, "Не вдалося перевірити можливість видалення.")));
    return () => { active = false; };
  }, [subject.id]);

  const submit = async () => {
    setSubmitting(true);
    setError("");
    try {
      await adminSubjectsApi.delete(subject.id);
      await onCompleted(`Предмет «${subject.title}» видалено.`);
    } catch (requestError) {
      setError(errorMessage(requestError, "Не вдалося видалити предмет."));
      setSubmitting(false);
    }
  };

  return <ModalFrame title="Видалити предмет" subtitle={subject.title} onClose={onClose} busy={submitting}>{!eligibility && !error ? <div className="flex min-h-28 items-center justify-center"><Loader2 className="h-5 w-5 animate-spin text-slate-400" /></div> : null}{eligibility && !eligibility.deletable ? <div className="mt-5 rounded-md border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-800">Предмет не можна видалити: {eligibility.blockers.join(", ")}.</div> : null}{eligibility?.deletable ? <div className="mt-5 rounded-md border border-red-200 bg-red-50 p-4 text-sm leading-6 text-red-700">Предмет не має пов'язаних учителів або інших даних. Після підтвердження його буде видалено без можливості відновлення.</div> : null}{error ? <div className="mt-4"><Alert>{error}</Alert></div> : null}<div className="mt-6 flex justify-end gap-2"><Button type="button" variant="ghost" onClick={onClose} disabled={submitting} className="h-10 rounded-md border border-slate-300 bg-white px-4">{eligibility?.deletable ? "Скасувати" : "Закрити"}</Button>{eligibility?.deletable ? <Button type="button" onClick={submit} disabled={submitting} className="h-10 rounded-md border border-red-700 bg-red-700 px-4 text-white hover:bg-red-600">{submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}Видалити</Button> : null}</div></ModalFrame>;
}

function ModalFrame({ title, subtitle, onClose, busy, children }) {
  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4" onMouseDown={(event) => event.target === event.currentTarget && !busy && onClose()}><section role="dialog" aria-modal="true" aria-labelledby="subject-dialog-title" onKeyDown={(event) => event.key === "Escape" && !busy && onClose()} className="max-h-[calc(100dvh-2rem)] w-full max-w-xl overflow-y-auto rounded-lg border border-slate-200 bg-white p-5 shadow-2xl sm:p-6"><div className="flex items-start justify-between gap-4"><div><h2 id="subject-dialog-title" className="text-base font-bold text-slate-950">{title}</h2><p className="mt-1 text-xs text-slate-500">{subtitle}</p></div><button type="button" onClick={onClose} disabled={busy} aria-label="Закрити" className="flex h-8 w-8 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-50"><X className="h-4 w-4" /></button></div>{children}</section></div>;
}

function teacherWord(value) {
  if (value % 10 === 1 && value % 100 !== 11) return "вчитель";
  if ([2, 3, 4].includes(value % 10) && ![12, 13, 14].includes(value % 100)) return "вчителі";
  return "вчителів";
}

function subjectWord(value) {
  if (value % 10 === 1 && value % 100 !== 11) return "предмет";
  if ([2, 3, 4].includes(value % 10) && ![12, 13, 14].includes(value % 100)) return "предмети";
  return "предметів";
}

function classWord(value) {
  if (value % 10 === 1 && value % 100 !== 11) return "клас";
  if ([2, 3, 4].includes(value % 10) && ![12, 13, 14].includes(value % 100)) return "класи";
  return "класів";
}
