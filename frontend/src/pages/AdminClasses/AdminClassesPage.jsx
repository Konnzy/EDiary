import { Link } from "react-router-dom";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  GraduationCap,
  Loader2,
  Plus,
  RefreshCw,
  Search,
  UserRound,
  Users,
  X,
} from "lucide-react";
import { Alert } from "@/components/ui/alert.jsx";
import { Button } from "@/components/ui/button.jsx";
import { Card } from "@/components/ui/card.jsx";
import { Input } from "@/components/ui/input.jsx";
import { ApiError, adminClassesApi, adminScheduleApi } from "@/lib/api.js";
import { cn } from "@/lib/utils.js";
import { AdminLayout } from "@/pages/AdminHome/AdminLayout.jsx";

const emptyForm = {
  number: "",
  letter: "",
  classTeacherId: "",
};

const classStatus = {
  ACTIVE: { label: "Активний", className: "border-emerald-200 bg-emerald-50 text-emerald-700" },
  DRAFT: { label: "Чернетка", className: "border-amber-200 bg-amber-50 text-amber-800" },
  ARCHIVED: { label: "Архівний", className: "border-slate-200 bg-slate-50 text-slate-600" },
};

function normalizeClassLetter(value) {
  return value.toUpperCase().replace(/[^А-ЯІЇЄҐ]/g, "").slice(0, 1);
}

function validateClass(form) {
  const errors = {};
  const number = Number(form.number);
  if (!Number.isInteger(number) || number < 1 || number > 12) {
    errors.number = "Номер класу має бути від 1 до 12";
  }
  if (!/^[А-ЯІЇЄҐ]$/.test(form.letter)) {
    errors.letter = "Введіть одну велику українську літеру";
  }
  return errors;
}

export function AdminClassesPage() {
  const [classes, setClasses] = useState([]);
  const [searchInput, setSearchInput] = useState("");
  const [number, setNumber] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [editingClass, setEditingClass] = useState(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [academicYears, setAcademicYears] = useState([]);
  const [selectedYear, setSelectedYear] = useState("");
  const [isLoadingYears, setIsLoadingYears] = useState(true);
  const [yearDialogOpen, setYearDialogOpen] = useState(false);

  const loadClasses = useCallback(async () => {
    setIsLoading(true);
    setError("");
    try {
      if (!selectedYear) {
        setClasses([]);
        return;
      }
      setClasses(await adminClassesApi.findAll({ academicYear: selectedYear }));
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : "Не вдалося завантажити класи.");
    } finally {
      setIsLoading(false);
    }
  }, [selectedYear]);

  useEffect(() => {
    if (selectedYear) loadClasses();
  }, [loadClasses, selectedYear]);

  useEffect(() => {
    let active = true;
    adminScheduleApi.findAcademicYears()
      .then((years) => {
        if (!active) return;
        const currentYear = getCurrentAcademicYear();
        setAcademicYears(years);
        setSelectedYear(years.includes(currentYear) ? currentYear : (years.at(-1) || ""));
      })
      .catch((requestError) => {
        if (!active) return;
        setAcademicYears([]);
        setSelectedYear("");
        setError(requestError instanceof ApiError ? requestError.message : "Не вдалося завантажити навчальні роки.");
      })
      .finally(() => active && setIsLoadingYears(false));
    return () => { active = false; };
  }, []);

  const openCreate = () => {
    setEditingClass(null);
    setDialogOpen(true);
  };

  const handleSaved = async (message) => {
    setDialogOpen(false);
    setEditingClass(null);
    setNotice(message);
    await loadClasses();
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
    setAcademicYears((current) => [...current, response.academicYear].sort((first, second) => Number(first.slice(0, 4)) - Number(second.slice(0, 4))));
    setSelectedYear(response.academicYear);
    setYearDialogOpen(false);
  };

  const visibleClasses = useMemo(() => {
    const search = normalizeClassSearch(searchInput);
    return classes.filter((schoolClass) => {
      const matchesNumber = !number || String(schoolClass.number) === number;
      const searchableText = normalizeClassSearch(`${schoolClass.number}-${schoolClass.letter} ${schoolClass.classTeacherName || ""}`);
      return matchesNumber && (!search || searchableText.includes(search));
    });
  }, [classes, number, searchInput]);

  return (
    <AdminLayout
      activeItem="Класи"
      title="Класи"
      subtitle="Керування класами та класними керівниками."
      actions={(
        <Button type="button" onClick={openCreate} disabled={!selectedYear || selectedYear !== getCurrentAcademicYear()} title={selectedYear !== getCurrentAcademicYear() ? "Архівні класи доступні лише для перегляду" : "Створити клас"} className="h-10 w-full rounded-md px-4 sm:w-auto">
          <Plus className="h-4 w-4" aria-hidden="true" />
          Створити клас
        </Button>
      )}
    >
      <Card className="w-full max-w-5xl rounded-lg p-4 sm:p-5">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-end">
          <label className="block min-w-0 flex-1">
            <span className="mb-2 block text-[0.75rem] font-semibold text-slate-600">Пошук класу</span>
            <span className="relative block">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
              <Input
                value={searchInput}
                onChange={(event) => setSearchInput(event.target.value)}
                placeholder="Назва класу або класний керівник"
                className="h-10 pl-10"
                aria-label="Пошук класів"
              />
            </span>
          </label>

          <div className="flex flex-col gap-2 sm:flex-row lg:shrink-0">
            <label className="block min-w-0 flex-1 sm:min-w-[190px]">
              <span className="mb-2 block text-[0.75rem] font-semibold text-slate-600">Паралель</span>
              <span className="relative block">
              <select
                value={number}
                onChange={(event) => setNumber(event.target.value)}
                className="h-10 w-full appearance-none rounded-md border border-slate-300 bg-white px-3 pr-9 text-[0.82rem] text-slate-700 outline-hidden transition-colors hover:border-slate-400 focus:border-slate-500"
                aria-label="Фільтр за паралеллю класу"
              >
                <option value="">Усі паралелі</option>
                {Array.from({ length: 12 }, (_, index) => index + 1).map((item) => <option key={item} value={item}>{item} класи</option>)}
              </select>
              <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
              </span>
            </label>
            <label className="block min-w-0 flex-1 sm:min-w-[220px]">
              <span className="mb-2 block text-[0.75rem] font-semibold text-slate-600">Навчальний рік</span>
              <span className="relative block">
              <select
                value={selectedYear}
                onChange={(event) => changeAcademicYear(event.target.value)}
                disabled={isLoadingYears}
                className="h-10 w-full appearance-none rounded-md border border-slate-300 bg-white px-3 pr-9 text-[0.82rem] text-slate-700 outline-hidden transition-colors hover:border-slate-400 focus:border-slate-500"
                aria-label="Навчальний рік"
              >
                {!selectedYear ? <option value="">{isLoadingYears ? "Завантаження..." : "Навчальний рік не налаштовано"}</option> : null}
                {academicYears.map((year) => <option key={year} value={year}>{year} н. р.</option>)}
                <option value="__add_year__">+ Додати навчальний рік</option>
              </select>
              <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
              </span>
            </label>
            <Button type="button" variant="ghost" size="icon" onClick={loadClasses} disabled={isLoading} className="h-10 w-10 self-end rounded-md border border-slate-200 bg-white" title="Оновити список">
              <RefreshCw className={cn("h-4 w-4", isLoading && "animate-spin")} aria-hidden="true" />
            </Button>
          </div>
        </div>
        {!isLoading && !error ? (
          <p className="mt-3 text-[0.72rem] text-slate-500" aria-live="polite">
            Показано {visibleClasses.length} із {classes.length} класів
          </p>
        ) : null}
      </Card>

      {notice ? (
        <div className="mt-5 flex items-center gap-2 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-[0.8rem] font-medium text-emerald-700">
          <CheckCircle2 className="h-4 w-4 shrink-0" aria-hidden="true" />
          {notice}
        </div>
      ) : null}

      {error ? <div className="mt-5"><Alert>{error}</Alert></div> : null}

      {!error && isLoading ? (
        <div className="flex min-h-[320px] items-center justify-center text-slate-400">
          <Loader2 className="h-6 w-6 animate-spin" aria-label="Завантаження" />
        </div>
      ) : null}

      {!error && !isLoading && visibleClasses.length === 0 ? (
        <Card className="mt-5 flex min-h-[260px] flex-col items-center justify-center rounded-lg px-5 text-center">
          <GraduationCap className="h-9 w-9 text-slate-300" aria-hidden="true" />
          <h2 className="mt-4 text-[0.95rem] font-bold text-slate-800">Класів не знайдено</h2>
          <p className="mt-1 text-[0.8rem] text-slate-500">
            {classes.length > 0
              ? "Змініть текст пошуку або фільтр паралелі."
              : selectedYear === getCurrentAcademicYear() ? "Створіть новий клас або оберіть інший навчальний рік." : "У вибраному навчальному році класів немає."}
          </p>
        </Card>
      ) : null}

      {!error && !isLoading && visibleClasses.length > 0 ? (
        <div className="mt-5 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {visibleClasses.map((schoolClass) => (
            <ClassCard key={schoolClass.id} schoolClass={schoolClass} />
          ))}
        </div>
      ) : null}

      {dialogOpen ? (
        <ClassDialog
          schoolClass={editingClass}
          academicYear={selectedYear}
          onClose={() => setDialogOpen(false)}
          onSaved={handleSaved}
        />
      ) : null}
      {yearDialogOpen ? (
        <AcademicYearDialog
          years={academicYears}
          onClose={() => setYearDialogOpen(false)}
          onCreate={addAcademicYear}
        />
      ) : null}
    </AdminLayout>
  );
}

function normalizeClassSearch(value) {
  return value.toLocaleLowerCase("uk-UA").replace(/[\s.-]/g, "");
}

function ClassCard({ schoolClass }) {
  const status = classStatus[schoolClass.status] || classStatus.ARCHIVED;
  return (
    <Link
      to={`/admin/classes/${schoolClass.id}`}
      className="group min-h-[170px] rounded-lg border border-slate-200 bg-white p-5 text-left shadow-[0_10px_35px_-30px_rgba(15,23,42,0.55)] transition-all duration-200 hover:-translate-y-0.5 hover:border-slate-400 hover:shadow-[0_18px_42px_-28px_rgba(15,23,42,0.35)] focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-slate-500 focus-visible:ring-offset-2"
      aria-label={`Відкрити клас ${schoolClass.number}-${schoolClass.letter}`}
    >
      <div className="flex items-start justify-between gap-3">
        <span className="text-[1.45rem] font-bold text-slate-950">{schoolClass.number}-{schoolClass.letter}</span>
        <span className={cn(
          "rounded-full border px-2.5 py-1 text-[0.68rem] font-bold",
          status.className,
        )}>
          {status.label}
        </span>
      </div>

      <div className="mt-3 flex items-center gap-2 text-[0.82rem] text-slate-600">
        <Users className="h-4 w-4 text-slate-500" aria-hidden="true" />
        <span>{schoolClass.studentCount} / {schoolClass.limit} учнів</span>
      </div>

      <div className="mt-3 flex items-end justify-between gap-4">
        <div className="min-w-0">
          <div className="text-[0.72rem] text-slate-400">Класний керівник</div>
          <div className="mt-1 truncate text-[0.82rem] font-medium text-slate-700">
            {schoolClass.classTeacherName || "Не призначено"}
          </div>
        </div>
        <span title="Відкрити клас"><ChevronRight className="h-5 w-5 shrink-0 text-slate-400 transition-transform group-hover:translate-x-0.5 group-hover:text-slate-700" aria-hidden="true" /></span>
      </div>
    </Link>
  );
}

export function ClassDialog({ schoolClass, academicYear, onClose, onSaved }) {
  const isEditing = Boolean(schoolClass);
  const [form, setForm] = useState(() => schoolClass ? {
    number: String(schoolClass.number),
    letter: schoolClass.letter,
    classTeacherId: schoolClass.classTeacherId ? String(schoolClass.classTeacherId) : "",
  } : emptyForm);
  const [options, setOptions] = useState({ teachers: [] });
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState("");
  const [isLoadingOptions, setIsLoadingOptions] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    let active = true;
    adminClassesApi.getOptions(schoolClass?.academicYear || academicYear)
      .then((value) => active && setOptions(value))
      .catch((requestError) => active && setServerError(requestError instanceof ApiError ? requestError.message : "Не вдалося завантажити список учителів."))
      .finally(() => active && setIsLoadingOptions(false));
    return () => { active = false; };
  }, [academicYear, schoolClass?.academicYear]);

  const visibleTeachers = useMemo(() => options.teachers || [], [options.teachers]);

  const update = (field, value) => {
    setForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => {
      if (!current[field]) return current;
      const next = { ...current };
      delete next[field];
      return next;
    });
    setServerError("");
  };

  const submit = async (event) => {
    event.preventDefault();
    const validationErrors = validateClass(form);
    setErrors(validationErrors);
    if (Object.keys(validationErrors).length > 0) return;

    const payload = {
      number: Number(form.number),
      letter: form.letter,
      classTeacherId: form.classTeacherId ? Number(form.classTeacherId) : null,
      academicYear: schoolClass?.academicYear || academicYear,
    };

    setIsSubmitting(true);
    setServerError("");
    try {
      if (isEditing) {
        await adminClassesApi.update(schoolClass.id, payload);
        await onSaved(`Клас ${payload.number}-${payload.letter} оновлено`);
      } else {
        await adminClassesApi.create(payload);
        await onSaved(`Клас ${payload.number}-${payload.letter} створено${payload.classTeacherId ? "" : " як чернетку"}`);
      }
    } catch (requestError) {
      if (requestError instanceof ApiError) {
        setErrors(requestError.fieldErrors || {});
        setServerError(requestError.message);
      } else {
        setServerError("Не вдалося зберегти клас.");
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section role="dialog" aria-modal="true" aria-labelledby="class-dialog-title" className="w-full max-w-[560px] rounded-lg border border-slate-200 bg-white p-5 shadow-2xl sm:p-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 id="class-dialog-title" className="text-[1.05rem] font-bold text-slate-950">{isEditing ? "Редагувати клас" : "Створити клас"}</h2>
            <p className="mt-1 text-[0.8rem] text-slate-500">Вкажіть паралель, літеру та класного керівника.</p>
          </div>
          <button type="button" onClick={onClose} disabled={isSubmitting} className="flex h-8 w-8 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label="Закрити">
            <X className="h-4 w-4" />
          </button>
        </div>

        {serverError ? <div className="mt-4"><Alert>{serverError}</Alert></div> : null}

        <form onSubmit={submit} className="mt-5 space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <DialogField label="Номер класу" error={errors.number}>
              <Input type="number" min="1" max="12" step="1" value={form.number} onChange={(event) => update("number", event.target.value)} placeholder="1–12" />
            </DialogField>
            <DialogField label="Літера класу" error={errors.letter}>
              <Input value={form.letter} onChange={(event) => update("letter", normalizeClassLetter(event.target.value))} maxLength={1} placeholder="А–Я" autoComplete="off" />
            </DialogField>
          </div>

          <DialogField label="Класний керівник" error={errors.classTeacherId}>
            <div className="relative">
              <select
                value={form.classTeacherId}
                onChange={(event) => update("classTeacherId", event.target.value)}
                disabled={isLoadingOptions}
                className="h-10 w-full appearance-none rounded-md border border-slate-300 bg-white px-3 pr-9 text-[0.82rem] text-slate-700 outline-hidden disabled:opacity-60"
              >
                <option value="">Не призначено (чернетка)</option>
                {visibleTeachers.map((teacher) => {
                  const assignedElsewhere = teacher.assignedClassId && teacher.assignedClassId !== schoolClass?.id;
                  return (
                    <option key={teacher.id} value={teacher.id} disabled={assignedElsewhere}>
                      {teacher.fullName}{assignedElsewhere ? ` — ${teacher.assignedClassName}` : ""}
                    </option>
                  );
                })}
              </select>
              {isLoadingOptions ? <Loader2 className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 animate-spin text-slate-400" /> : <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />}
            </div>
          </DialogField>

          <div className="flex items-start gap-3 rounded-md border border-slate-200 bg-slate-50 px-4 py-3 text-[0.77rem] leading-5 text-slate-600">
            <UserRound className="mt-0.5 h-4 w-4 shrink-0 text-slate-500" aria-hidden="true" />
            {form.classTeacherId
              ? "Після призначення керівника клас стане активним. Один учитель може вести лише один клас."
              : "Клас залишиться чернеткою. Додати учнів і розклад можна після призначення керівника."}
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="ghost" onClick={onClose} disabled={isSubmitting} className="h-10 rounded-md border border-slate-300 bg-white px-4">Скасувати</Button>
            <Button type="submit" disabled={isSubmitting || isLoadingOptions} className="h-10 min-w-[128px] rounded-md px-4">
              {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
              {isEditing ? "Зберегти" : "Створити"}
            </Button>
          </div>
        </form>
      </section>
    </div>
  );
}

function DialogField({ label, error, children }) {
  return (
    <label className="block text-[0.8rem] font-semibold text-slate-700">
      {label}
      <div className={cn("mt-2", error && "[&_input]:border-red-400 [&_select]:border-red-400")}>{children}</div>
      {error ? <span className="mt-1.5 block text-[0.72rem] font-normal text-red-500">{error}</span> : null}
    </label>
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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section role="dialog" aria-modal="true" aria-labelledby="class-year-dialog-title" onKeyDown={(event) => event.key === "Escape" && onClose()} className="w-full max-w-[440px] rounded-lg border border-slate-200 bg-white p-5 shadow-2xl outline-hidden sm:p-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 id="class-year-dialog-title" className="text-[1.05rem] font-bold text-slate-950">Додати навчальний рік</h2>
            <p className="mt-1 text-[0.78rem] text-slate-500">Створення наступного навчального періоду</p>
          </div>
          <button type="button" onClick={onClose} className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label="Закрити">
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </div>

        <div className="mt-5 rounded-md border border-slate-200 bg-slate-50 p-4">
          <p className="text-[0.72rem] font-medium text-slate-500">Новий навчальний рік</p>
          <p className="mt-1 text-lg font-bold text-slate-900">{nextYear}</p>
        </div>

        {hasNotStarted ? (
          <Alert className="mt-3">Навчальний рік ще не настав. Його можна створити не раніше 1 вересня {nextStartYear} року.</Alert>
        ) : null}
        {error ? <Alert className="mt-3">{error}</Alert> : null}

        <div className="mt-6 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose} disabled={saving} className="h-10 rounded-md border border-slate-300 bg-white px-4 text-xs">Скасувати</Button>
          <Button type="button" onClick={create} disabled={saving || hasNotStarted} className="h-10 rounded-md px-4 text-xs"><Plus className="h-4 w-4" aria-hidden="true" />{saving ? "Створення..." : "Створити"}</Button>
        </div>
      </section>
    </div>
  );
}

function getCurrentAcademicYear() {
  const today = new Date();
  const startYear = today.getMonth() >= 8 ? today.getFullYear() : today.getFullYear() - 1;
  return `${startYear}/${startYear + 1}`;
}
