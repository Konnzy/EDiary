import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ArrowRightLeft, BookOpen, CalendarDays, CheckCircle2, ChevronDown, ChevronRight, Loader2, Pencil, Plus, Search, Trash2, UserMinus, Users, X } from "lucide-react";
import { Alert } from "@/components/ui/alert.jsx";
import { Button } from "@/components/ui/button.jsx";
import { Input } from "@/components/ui/input.jsx";
import { ApiError, adminClassesApi } from "@/lib/api.js";
import { AdminLayout } from "@/pages/AdminHome/AdminLayout.jsx";
import { ClassDialog } from "./AdminClassesPage.jsx";

const CLASS_CAPACITY = 30;
const statusLabels = { STUDYING: "Навчається", GRADUATED: "Випускник", EXPELLED: "Відрахований" };
const statusStyles = {
  STUDYING: "border-emerald-200 bg-emerald-50 text-emerald-700",
  GRADUATED: "border-slate-200 bg-slate-100 text-slate-600",
  EXPELLED: "border-red-200 bg-red-50 text-red-700",
};
const outlinedButton = "h-9 rounded-md border border-slate-200 bg-white px-3 text-xs text-slate-600";

function requestMessage(error, fallback) {
  return error instanceof ApiError ? error.message : fallback;
}

export function AdminClassDetailsPage({ classId }) {
  const [schoolClass, setSchoolClass] = useState(null);
  const [classes, setClasses] = useState([]);
  const [students, setStudents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [query, setQuery] = useState("");
  const [dialog, setDialog] = useState(null);
  const [editing, setEditing] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [journalPreviewStudent, setJournalPreviewStudent] = useState(null);
  const [checkingDeletion, setCheckingDeletion] = useState(false);
  const [actionError, setActionError] = useState("");

  const className = schoolClass ? `${schoolClass.number}-${schoolClass.letter}` : "Клас";
  const visibleStudents = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase("uk");
    if (!normalizedQuery) return students;
    return students.filter((student) =>
      `${student.fullName} ${student.login} ${student.email}`.toLocaleLowerCase("uk").includes(normalizedQuery),
    );
  }, [query, students]);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const classData = await adminClassesApi.findById(classId);
      const [allClasses, classStudents] = await Promise.all([
        adminClassesApi.findAll({ academicYear: classData.academicYear }),
        adminClassesApi.findStudents(classId),
      ]);
      setSchoolClass(classData);
      setClasses(allClasses);
      setStudents(classStudents);
    } catch (requestError) {
      setError(requestMessage(requestError, "Не вдалося завантажити клас."));
    } finally {
      setLoading(false);
    }
  }, [classId]);

  useEffect(() => { load(); }, [load]);

  const applyMembershipChange = async (type, studentId, targetClassId) => {
    if (type === "add") {
      await adminClassesApi.addStudent(classId, studentId);
      setNotice("Учня додано до класу.");
    } else if (type === "transfer") {
      await adminClassesApi.transferStudent(classId, studentId, targetClassId);
      setNotice("Учня переведено до іншого класу.");
    } else {
      await adminClassesApi.removeStudent(classId, studentId);
      setNotice("Учня вилучено зі складу класу. Обліковий запис і навчальні дані збережено.");
    }
    setDialog(null);
    await load();
  };

  const requestDeletion = async () => {
    setCheckingDeletion(true);
    setActionError("");
    try {
      const eligibility = await adminClassesApi.getDeletionEligibility(classId);
      if (!eligibility.deletable) {
        setActionError(`Клас неможливо видалити: ${eligibility.blockers.join(", ")}.`);
        return;
      }
      setDeleteDialogOpen(true);
    } catch (requestError) {
      setActionError(requestMessage(requestError, "Не вдалося перевірити можливість видалення класу."));
    } finally {
      setCheckingDeletion(false);
    }
  };

  return (
    <AdminLayout
      activeItem="Класи"
      title={className}
      subtitle="Керування класом і складом учнів."
      breadcrumb={<ClassBreadcrumb classId={classId} className={className} />}
      actions={!loading && schoolClass?.editable ? (
        <div className="flex flex-wrap gap-2">
          <Button variant="ghost" onClick={requestDeletion} disabled={checkingDeletion} className="h-10 rounded-md border border-red-600 bg-red-600 px-4 text-white hover:bg-red-500 hover:text-white">
            {checkingDeletion ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
            Видалити клас
          </Button>
          <Button onClick={() => setEditing(true)} className="h-10 rounded-md"><Pencil className="h-4 w-4" />Редагувати клас</Button>
        </div>
      ) : null}
    >
      {loading ? (
        <div className="flex justify-center py-20"><Loader2 className="h-6 w-6 animate-spin text-slate-400" aria-label="Завантаження" /></div>
      ) : error ? (
        <div className="space-y-4"><Alert>{error}</Alert><Button onClick={load} className="rounded-md">Спробувати ще раз</Button></div>
      ) : schoolClass ? (
        <>
          <ClassSummary schoolClass={schoolClass} studentCount={students.length} />
          {actionError ? <div className="mt-5"><Alert>{actionError}</Alert></div> : null}
          {notice ? <SuccessNotice>{notice}</SuccessNotice> : null}
          <StudentsSection
            students={students}
            visibleStudents={visibleStudents}
            query={query}
            onQueryChange={setQuery}
            onAdd={() => setDialog({ type: "add" })}
            onTransfer={(student) => setDialog({ type: "transfer", student })}
            onRemove={(student) => setDialog({ type: "remove", student })}
            onViewLearningData={setJournalPreviewStudent}
            editable={schoolClass.editable}
            draft={schoolClass.status === "DRAFT"}
          />
        </>
      ) : null}

      {editing ? (
        <ClassDialog schoolClass={schoolClass} onClose={() => setEditing(false)} onSaved={async (message) => {
          setEditing(false);
          setNotice(message);
          await load();
        }} />
      ) : null}

      {dialog ? (
        <MembershipDialog dialog={dialog} schoolClass={schoolClass} classes={classes} onClose={() => setDialog(null)} onConfirm={applyMembershipChange} />
      ) : null}

      {deleteDialogOpen ? (
        <DeleteClassDialog schoolClass={schoolClass} onClose={() => setDeleteDialogOpen(false)} />
      ) : null}

      {journalPreviewStudent ? (
        <JournalPendingDialog
          student={journalPreviewStudent}
          onClose={() => setJournalPreviewStudent(null)}
        />
      ) : null}
    </AdminLayout>
  );
}

function ClassBreadcrumb({ classId, className }) {
  return <><a href="#admin/classes" className="hover:text-slate-900">Класи</a><ChevronRight className="h-3.5 w-3.5" /><a href={`#admin/classes/${classId}`} className="font-medium text-slate-700" aria-current="page">{className}</a></>;
}

function ClassSummary({ schoolClass, studentCount }) {
  const scheduleHref = `#admin/schedule?classId=${schoolClass.id}&academicYear=${encodeURIComponent(schoolClass.academicYear)}`;
  const statuses = {
    ACTIVE: { label: "Активний", className: "border-emerald-200 bg-emerald-50 text-emerald-700" },
    DRAFT: { label: "Чернетка", className: "border-amber-200 bg-amber-50 text-amber-800" },
    ARCHIVED: { label: "Архівний", className: "border-slate-200 bg-slate-50 text-slate-600" },
  };
  const status = statuses[schoolClass.status] || statuses.ARCHIVED;
  return (
    <section aria-label="Інформація про клас" className="rounded-lg border border-slate-200 bg-white p-5 sm:p-6">
      <dl className="grid gap-6 sm:grid-cols-2 xl:grid-cols-4">
        <Info label="Класний керівник" value={schoolClass.classTeacherName || "Не призначено"} />
        <Info label="Кількість учнів" value={`${studentCount} / ${CLASS_CAPACITY}`} />
        <Info label="Максимальна місткість" value={`${CLASS_CAPACITY} учнів`} />
        <Info label="Статус класу" value={<span className={`inline-flex rounded-full border px-3 py-1 text-xs ${status.className}`}>{status.label}</span>} />
      </dl>
      {schoolClass.status === "DRAFT" ? (
        <p className="mt-5 rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
          Призначте класного керівника, щоб клас став активним і до нього можна було додавати учнів та розклад.
        </p>
      ) : (
        <a href={scheduleHref} className="mt-6 inline-flex items-center gap-2 text-sm font-semibold text-slate-700 hover:text-slate-950">
          <CalendarDays className="h-4 w-4" />Переглянути розклад класу<ChevronRight className="h-4 w-4" />
        </a>
      )}
    </section>
  );
}

function Info({ label, value }) {
  return <div><dt className="text-xs text-slate-500">{label}</dt><dd className="mt-2 break-words text-sm font-semibold text-slate-800">{value}</dd></div>;
}

function SuccessNotice({ children }) {
  return <div role="status" className="mt-5 flex items-center gap-2 rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-700"><CheckCircle2 className="h-4 w-4 shrink-0" />{children}</div>;
}

function StudentsSection({ students, visibleStudents, query, onQueryChange, onAdd, onTransfer, onRemove, onViewLearningData, editable, draft }) {
  const full = students.length >= CLASS_CAPACITY;
  return (
    <section className="mt-6 overflow-hidden rounded-lg border border-slate-200 bg-white">
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-200 p-5">
        <h2 className="text-base font-bold">Учні класу <span className="ml-2 font-normal text-slate-400">{students.length}</span></h2>
        {editable && !draft ? (
          <Button onClick={onAdd} disabled={full} title={full ? "У класі немає вільних місць" : "Додати учня"} className="h-10 rounded-md">
            <Plus className="h-4 w-4" />Додати учня
          </Button>
        ) : <span className="text-xs text-slate-500">{draft ? "Спочатку призначте класного керівника" : "Архівний склад, лише перегляд"}</span>}
      </div>
      <div className="border-b border-slate-100 p-5">
        <div className="relative max-w-lg">
          <Search className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-slate-400" />
          <Input aria-label="Пошук учнів за ПІБ або логіном" value={query} onChange={(event) => onQueryChange(event.target.value)} placeholder="Пошук за ПІБ або логіном" className="pl-10" />
        </div>
        {full ? <p className="mt-3 text-xs text-amber-700">У класі немає вільних місць.</p> : null}
      </div>
      {visibleStudents.length ? (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs text-slate-500">
              <tr><th className="px-5 py-3 font-medium">Учень</th><th className="px-5 py-3 font-medium">Статус учня</th><th className="px-5 py-3 text-right font-medium">Дії</th></tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {visibleStudents.map((student) => <StudentRow key={student.id} student={student} editable={editable} onViewLearningData={() => onViewLearningData(student)} onTransfer={() => onTransfer(student)} onRemove={() => onRemove(student)} />)}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="py-12 text-center text-sm text-slate-500">
          <Users className="mx-auto mb-3 h-7 w-7 text-slate-300" />
          {query ? "Учнів за цим запитом не знайдено." : "У класі поки немає учнів."}
        </div>
      )}
    </section>
  );
}

function StudentRow({ student, onViewLearningData, onTransfer, onRemove, editable }) {
  return <tr className="hover:bg-slate-50/60"><td className="px-5 py-4"><div className="font-semibold text-slate-800">{student.fullName}</div><div className="mt-1 text-xs text-slate-500">{student.login}</div><div className="mt-1 break-all text-xs text-slate-400">{student.email}</div></td><td className="px-5 py-4"><span className={`inline-flex whitespace-nowrap rounded-full border px-2.5 py-1 text-xs ${statusStyles[student.status] || statusStyles.STUDYING}`}>{statusLabels[student.status] || student.status}</span></td><td className="px-5 py-4"><div className="flex flex-wrap justify-end gap-2"><Button className="h-9 rounded-md border border-sky-300 bg-sky-50 px-3 text-xs text-sky-700 hover:bg-sky-100" onClick={onViewLearningData} title="Навчальні дані"><BookOpen className="h-4 w-4" /><span className="hidden 2xl:inline">Навчальні дані</span><span className="sr-only 2xl:hidden">Навчальні дані</span></Button>{editable ? <><Button className="h-9 rounded-md border border-slate-300 bg-white px-3 text-xs text-slate-700 hover:bg-slate-100" onClick={onTransfer} title="Перевести до іншого класу"><ArrowRightLeft className="h-4 w-4" /><span className="hidden 2xl:inline">Перевести до іншого класу</span><span className="sr-only 2xl:hidden">Перевести до іншого класу</span></Button><Button className="h-9 rounded-md border border-red-300 bg-red-50 px-3 text-xs text-red-700 hover:bg-red-100" onClick={onRemove} title="Вилучити з класу"><UserMinus className="h-4 w-4" /><span className="hidden 2xl:inline">Вилучити з класу</span><span className="sr-only 2xl:hidden">Вилучити з класу</span></Button></> : null}</div></td></tr>;
}

function JournalPendingDialog({ student, onClose }) {
  const panel = useRef(null);

  useEffect(() => {
    const previous = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    panel.current?.focus();
    return () => { document.body.style.overflow = previousOverflow; previous?.focus(); };
  }, []);

  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><section ref={panel} tabIndex={-1} onKeyDown={(event) => event.key === "Escape" && onClose()} role="dialog" aria-modal="true" aria-labelledby="journal-pending-title" className="w-full max-w-md rounded-lg border border-slate-200 bg-white p-5 shadow-2xl outline-hidden sm:p-6"><div className="flex items-start justify-between gap-4"><div><h2 id="journal-pending-title" className="text-base font-bold text-slate-950">Навчальні дані</h2><p className="mt-1 text-xs text-slate-500">{student.fullName}</p></div><button type="button" onClick={onClose} aria-label="Закрити" className="rounded p-1 text-slate-500 hover:bg-slate-100"><X className="h-5 w-5" /></button></div><div className="mt-5 flex gap-4 rounded-md border border-sky-200 bg-sky-50 p-4"><span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md border border-sky-200 bg-white text-sky-700"><BookOpen className="h-5 w-5" /></span><div><h3 className="text-sm font-bold text-slate-900">Очікується реалізація модуля журналу</h3><p className="mt-1 text-sm leading-6 text-slate-600">Тут буде доступний перегляд оцінок, відвідування та домашніх завдань учня в режимі лише для читання.</p></div></div><div className="mt-6 flex justify-end"><Button type="button" onClick={onClose} className="h-9 rounded-md px-4 text-xs">Зрозуміло</Button></div></section></div>;
}

function MembershipDialog({ dialog, schoolClass, classes, onClose, onConfirm }) {
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState("");
  const [candidates, setCandidates] = useState([]);
  const [loadingCandidates, setLoadingCandidates] = useState(dialog.type === "add");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const panel = useRef(null);
  const adding = dialog.type === "add";
  const removing = dialog.type === "remove";

  useEffect(() => {
    const previous = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    panel.current?.focus();
    return () => { document.body.style.overflow = previousOverflow; previous?.focus(); };
  }, []);

  useEffect(() => {
    if (!adding) return undefined;
    let active = true;
    const timeout = window.setTimeout(() => {
      setLoadingCandidates(true);
      setError("");
      adminClassesApi.findUnassignedStudents(query.trim(), schoolClass.academicYear)
        .then((result) => active && setCandidates(result))
        .catch((requestError) => active && setError(requestMessage(requestError, "Не вдалося завантажити учнів без класу.")))
        .finally(() => active && setLoadingCandidates(false));
    }, query ? 250 : 0);
    return () => { active = false; window.clearTimeout(timeout); };
  }, [adding, query, schoolClass.academicYear]);

  const submit = async (event) => {
    event.preventDefault();
    if (!removing && !selected) return;
    setSubmitting(true);
    setError("");
    try {
      await onConfirm(dialog.type, adding ? Number(selected) : dialog.student.id, dialog.type === "transfer" ? Number(selected) : undefined);
    } catch (requestError) {
      setError(requestMessage(requestError, "Не вдалося змінити склад класу."));
      setSubmitting(false);
    }
  };

  const onKeyDown = (event) => {
    if (event.key === "Escape" && !submitting) onClose();
    if (event.key !== "Tab") return;
    const items = [...panel.current.querySelectorAll('button:not(:disabled), input:not(:disabled), select:not(:disabled), [tabindex="0"]')];
    const first = items[0], last = items.at(-1);
    if (event.shiftKey && (document.activeElement === first || document.activeElement === panel.current)) { event.preventDefault(); last?.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
  };

  const otherClasses = classes.filter((item) => item.id !== schoolClass.id && item.status === "ACTIVE");

  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4" onMouseDown={(event) => event.target === event.currentTarget && !submitting && onClose()}><section ref={panel} tabIndex={-1} onKeyDown={onKeyDown} role="dialog" aria-modal="true" aria-labelledby="membership-title" className="max-h-[calc(100dvh-2rem)] w-full max-w-xl overflow-y-auto rounded-lg border border-slate-200 bg-white p-5 shadow-2xl outline-hidden sm:p-6"><div className="flex items-start justify-between gap-4"><div><h2 id="membership-title" className="text-base font-bold">{adding ? "Додати учня" : removing ? "Вилучити з класу" : "Перевести до іншого класу"}</h2><p className="mt-1 text-xs leading-5 text-slate-500">Клас {schoolClass.number}-{schoolClass.letter}, місткість {CLASS_CAPACITY} учнів.</p></div><button type="button" onClick={onClose} disabled={submitting} aria-label="Закрити" className="rounded p-1 text-slate-500 hover:bg-slate-100 disabled:opacity-50"><X className="h-5 w-5" /></button></div><form onSubmit={submit} className="mt-5 space-y-4">{!adding ? <p className="text-sm leading-6 text-slate-600">{dialog.student.fullName}</p> : null}{removing ? <p className="rounded-md border border-red-200 bg-red-50 p-4 text-sm leading-6 text-red-700">Підтвердьте вилучення учня зі складу класу. Обліковий запис, статус та навчальні дані учня залишаться в системі.</p> : adding ? <AddStudentChooser query={query} onQueryChange={setQuery} selected={selected} onSelect={setSelected} students={candidates} loading={loadingCandidates} /> : <ClassChooser classes={otherClasses} selected={selected} onSelect={setSelected} />}{!adding && !removing ? <p className="text-xs leading-5 text-slate-500">Після підтвердження зміниться лише клас учня. Обліковий запис, статус та навчальні дані збережуться.</p> : null}{error ? <Alert>{error}</Alert> : null}<div className="flex flex-wrap justify-end gap-2 pt-2"><Button type="button" variant="ghost" onClick={onClose} disabled={submitting} className={outlinedButton}>Скасувати</Button><Button type="submit" disabled={submitting || (!removing && !selected)} className={`h-9 rounded-md text-xs ${removing ? "border border-red-600 bg-red-600 text-white hover:bg-red-500" : ""}`}>{submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}{adding ? "Додати учня" : removing ? "Підтвердити вилучення" : "Підтвердити переведення"}</Button></div></form></section></div>;
}

function AddStudentChooser({ query, onQueryChange, selected, onSelect, students, loading }) {
  return <><div className="relative"><Search className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-slate-400" /><Input aria-label="Пошук учня без класу" placeholder="ПІБ або логін" value={query} onChange={(event) => onQueryChange(event.target.value)} className="pl-10" /></div><div className="max-h-64 divide-y divide-slate-100 overflow-y-auto rounded-md border border-slate-200">{loading ? <div className="flex justify-center p-6"><Loader2 className="h-5 w-5 animate-spin text-slate-400" aria-label="Завантаження" /></div> : students.length ? students.map((student) => <label key={student.id} className={`flex cursor-pointer items-center gap-3 p-3 text-sm hover:bg-slate-50 ${Number(selected) === student.id ? "bg-slate-50" : ""}`}><input type="radio" name="student" value={student.id} checked={Number(selected) === student.id} onChange={(event) => onSelect(event.target.value)} /><span className="min-w-0"><span className="block font-semibold">{student.fullName}</span><span className="mt-1 block break-all text-xs text-slate-500">{student.login} · {student.email}</span></span></label>) : <p className="p-5 text-sm text-slate-500">Активних учнів без класу не знайдено.</p>}</div></>;
}

function ClassChooser({ classes, selected, onSelect }) {
  return <label className="block text-sm font-semibold text-slate-700">Новий клас<div className="relative mt-2"><select required value={selected} onChange={(event) => onSelect(event.target.value)} className="h-10 w-full appearance-none rounded-md border border-slate-300 bg-white px-3 pr-9 text-sm outline-hidden focus:border-slate-500"><option value="">Оберіть клас</option>{classes.map((item) => { const full = item.studentCount >= CLASS_CAPACITY; return <option key={item.id} value={item.id} disabled={full}>{item.number}-{item.letter} · {item.studentCount} / {CLASS_CAPACITY}{full ? " · Немає місць" : ""}</option>; })}</select><ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" /></div></label>;
}

function DeleteClassDialog({ schoolClass, onClose }) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const panel = useRef(null);
  const className = `${schoolClass.number}-${schoolClass.letter}`;

  useEffect(() => {
    const previous = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    panel.current?.focus();
    return () => { document.body.style.overflow = previousOverflow; previous?.focus(); };
  }, []);

  const confirmDeletion = async () => {
    setSubmitting(true);
    setError("");
    try {
      await adminClassesApi.delete(schoolClass.id);
      window.location.hash = "admin/classes";
    } catch (requestError) {
      setError(requestMessage(requestError, "Не вдалося видалити клас."));
      setSubmitting(false);
    }
  };

  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4" onMouseDown={(event) => event.target === event.currentTarget && !submitting && onClose()}><section ref={panel} tabIndex={-1} role="alertdialog" aria-modal="true" aria-labelledby="delete-class-title" aria-describedby="delete-class-description" onKeyDown={(event) => event.key === "Escape" && !submitting && onClose()} className="w-full max-w-md rounded-lg border border-slate-200 bg-white p-5 shadow-2xl outline-hidden sm:p-6"><div className="flex items-start justify-between gap-4"><div><h2 id="delete-class-title" className="text-base font-bold text-slate-950">Видалити клас {className}?</h2><p id="delete-class-description" className="mt-2 text-sm leading-6 text-slate-600">Цю дію неможливо скасувати. Буде видалено лише порожній клас без керівника, учнів, розкладу та інших зв'язків.</p></div><button type="button" onClick={onClose} disabled={submitting} aria-label="Закрити" className="rounded p-1 text-slate-500 hover:bg-slate-100 disabled:opacity-50"><X className="h-5 w-5" /></button></div>{error ? <div className="mt-4"><Alert>{error}</Alert></div> : null}<div className="mt-6 flex justify-end gap-2"><Button type="button" variant="ghost" onClick={onClose} disabled={submitting} className={outlinedButton}>Скасувати</Button><Button type="button" onClick={confirmDeletion} disabled={submitting} className="h-9 rounded-md border border-red-600 bg-red-600 px-4 text-xs text-white hover:bg-red-500">{submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}Видалити</Button></div></section></div>;
}
