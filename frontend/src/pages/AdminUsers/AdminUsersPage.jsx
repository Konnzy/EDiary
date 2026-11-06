import { useCallback, useContext, useEffect, useRef, useState } from "react";
import {
  Ban,
  CheckCircle2,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  FileUp,
  Loader2,
  KeyRound,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  Trash2,
  UserRoundCheck,
  Users,
  X,
} from "lucide-react";
import { Alert } from "@/components/ui/alert.jsx";
import { BirthDateInput, getBirthDateBounds } from "@/components/ui/birth-date-input.jsx";
import { Button } from "@/components/ui/button.jsx";
import { Card } from "@/components/ui/card.jsx";
import { Input } from "@/components/ui/input.jsx";
import { ApiError, adminUsersApi, registrationApi } from "@/lib/api.js";
import { CurrentUserContext } from "@/lib/currentUser.jsx";
import { cn } from "@/lib/utils.js";
import { AdminLayout } from "@/pages/AdminHome/AdminLayout.jsx";
import { UserImportDialog } from "./UserImportDialog.jsx";
import { AdminStudentPicker } from "./AdminStudentPicker.jsx";

const roleLabels = {
  ADMIN: "Адміністратор",
  TEACHER: "Вчитель",
  PARENT: "Батьки",
  STUDENT: "Учень",
};

const statusLabels = {
  ACTIVE: "Активний",
  BLOCKED: "Заблокований",
  DELETED: "Деактивований",
};

const roleOptions = [
  { value: "", label: "Усі ролі" },
  ...Object.entries(roleLabels).map(([value, label]) => ({ value, label })),
];

const statusOptions = [
  { value: "", label: "Усі статуси" },
  { value: "ACTIVE", label: statusLabels.ACTIVE },
  { value: "BLOCKED", label: statusLabels.BLOCKED },
];

const createInitialForm = {
  login: "",
  firstName: "",
  lastName: "",
  patronymic: "",
  email: "",
  phone: "",
  birthDate: "",
  role: "STUDENT",
  classId: "",
  subjectId: "",
  student: null,
};

const UKRAINIAN_NAME_PATTERN = /^[А-Яа-яІіЇїЄєҐґ]+(?:[ '’-][А-Яа-яІіЇїЄєҐґ]+)*$/;

function formatLogin(value) {
  return value.toLowerCase().replace(/[^a-z0-9._-]/g, "").replace(/^[._-]+/, "").slice(0, 50);
}

function formatName(value) {
  return value.replace(/[^А-Яа-яІіЇїЄєҐґ '’-]/g, "").slice(0, 50);
}

function formatPhone(value) {
  const digits = value.replace(/\D/g, "").slice(0, 9);
  return [digits.slice(0, 2), digits.slice(2, 5), digits.slice(5, 7), digits.slice(7, 9)]
    .filter(Boolean)
    .join(" ");
}

function phoneForForm(value) {
  if (!value) return "";
  const digits = value.replace(/\D/g, "").replace(/^380/, "");
  return formatPhone(digits);
}

function validateEditForm(form) {
  const errors = {};
  if (!/^[a-z0-9](?:[a-z0-9._-]{1,48}[a-z0-9])$/.test(form.login)) {
    errors.login = "3–50 символів; початок і кінець — літера або цифра";
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    errors.email = "Некоректний формат пошти";
  }
  if (!UKRAINIAN_NAME_PATTERN.test(form.firstName.trim())) errors.firstName = "Введіть ім’я українськими літерами";
  if (!UKRAINIAN_NAME_PATTERN.test(form.lastName.trim())) errors.lastName = "Введіть прізвище українськими літерами";
  if (form.patronymic.trim() && !UKRAINIAN_NAME_PATTERN.test(form.patronymic.trim())) errors.patronymic = "Введіть по батькові українськими літерами";
  if (["TEACHER", "PARENT"].includes(form.role) && form.phone.replace(/\D/g, "").length !== 9) {
    errors.phone = "Введіть номер у форматі XX XXX XX XX";
  }
  return errors;
}

function parseBirthDate(value) {
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value);
  if (!match) return null;
  const [, dayText, monthText, yearText] = match;
  const date = new Date(Number(yearText), Number(monthText) - 1, Number(dayText));
  if (
    date.getFullYear() !== Number(yearText)
    || date.getMonth() !== Number(monthText) - 1
    || date.getDate() !== Number(dayText)
  ) return null;
  return { date, iso: `${yearText}-${monthText}-${dayText}` };
}

function calculateAge(date, today = new Date()) {
  let age = today.getFullYear() - date.getFullYear();
  if (
    today.getMonth() < date.getMonth()
    || (today.getMonth() === date.getMonth() && today.getDate() < date.getDate())
  ) age -= 1;
  return age;
}

function validateCreateForm(form) {
  const errors = {};
  if (!/^[a-z0-9](?:[a-z0-9._-]{1,48}[a-z0-9])$/.test(form.login)) {
    errors.login = "3–50 символів; початок і кінець — літера або цифра";
  }
  if (!UKRAINIAN_NAME_PATTERN.test(form.firstName.trim())) errors.firstName = "Введіть ім’я українськими літерами";
  if (!UKRAINIAN_NAME_PATTERN.test(form.lastName.trim())) errors.lastName = "Введіть прізвище українськими літерами";
  if (form.patronymic.trim() && !UKRAINIAN_NAME_PATTERN.test(form.patronymic.trim())) errors.patronymic = "Введіть по батькові українськими літерами";
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) errors.email = "Некоректний формат пошти";
  if (form.role !== "STUDENT" && form.phone.replace(/\D/g, "").length !== 9) errors.phone = "Введіть номер у форматі XX XXX XX XX";

  const parsedDate = parseBirthDate(form.birthDate);
  if (!parsedDate) {
    errors.birthDate = "Введіть дату у форматі дд/мм/рррр";
  } else {
    const age = calculateAge(parsedDate.date);
    if (form.role === "STUDENT" && (age < 5 || age > 20)) errors.birthDate = "Вік учня має бути від 5 до 20 років";
    if ((form.role === "TEACHER" || form.role === "PARENT") && (age < 18 || age > 100)) errors.birthDate = "Вік має бути від 18 до 100 років";
  }
  if (form.role === "STUDENT" && !form.classId) errors.classId = "Оберіть клас";
  if (form.role === "TEACHER" && !form.subjectId) errors.subjectId = "Оберіть предмет";
  if (form.role === "PARENT" && !form.student) errors.student = "Оберіть учня зі списку";
  return errors;
}

export function AdminUsersPage() {
  const [searchInput, setSearchInput] = useState("");
  const [query, setQuery] = useState("");
  const [role, setRole] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [dialog, setDialog] = useState(null);
  const [deleteDialog, setDeleteDialog] = useState(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [editDialog, setEditDialog] = useState(null);
  const [resetDialog, setResetDialog] = useState(null);
  const requestSequence = useRef(0);

  const loadUsers = useCallback(async () => {
    const requestId = ++requestSequence.current;
    setIsLoading(true);
    setError("");
    try {
      const result = await adminUsersApi.findAll({ query, role, status, page, size: 15 });
      if (requestId === requestSequence.current) setData(result);
    } catch (requestError) {
      if (requestId === requestSequence.current) {
        setError(requestError instanceof ApiError ? requestError.message : "Не вдалося завантажити користувачів.");
      }
    } finally {
      if (requestId === requestSequence.current) setIsLoading(false);
    }
  }, [page, query, role, status]);

  useEffect(() => {
    loadUsers();
  }, [loadUsers]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setQuery(searchInput.trim());
      setPage(0);
    }, 250);
    return () => window.clearTimeout(timer);
  }, [searchInput]);

  const resetFilters = () => {
    setSearchInput("");
    setQuery("");
    setRole("");
    setStatus("");
    setPage(0);
  };

  const handleStatusChanged = async (message) => {
    setDialog(null);
    setNotice(message);
    await loadUsers();
  };

  const handleCreated = async (message) => {
    setCreateOpen(false);
    setNotice(message);
    if (page === 0) {
      await loadUsers();
    } else {
      setPage(0);
    }
  };

  const handleDeleted = async (message) => {
    setDeleteDialog(null);
    setNotice(message);
    await loadUsers();
  };

  const handleEdited = async (message) => {
    setEditDialog(null);
    setNotice(message);
    await loadUsers();
  };

  const handlePasswordReset = async (message) => {
    setResetDialog(null);
    setNotice(message);
    await loadUsers();
  };

  return (
    <AdminLayout
      activeItem="Користувачі"
      title="Користувачі"
      subtitle="Пошук користувачів за ПІБ і керування доступом до системи."
      actions={(
        <div className="flex flex-col gap-2 sm:flex-row">
          <Button
            type="button"
            variant="ghost"
            onClick={() => setImportOpen(true)}
            title="Імпорт користувачів із CSV або XLSX"
            className="user-import-trigger h-10 w-full rounded-md border border-sky-700 bg-sky-700 px-4 font-semibold text-white hover:border-sky-800 hover:bg-sky-800 hover:text-white focus-visible:ring-sky-300 sm:w-auto"
          >
            <FileUp className="h-4 w-4" aria-hidden="true" />
            Імпортувати користувачів
          </Button>
          <Button type="button" onClick={() => setCreateOpen(true)} className="h-10 w-full rounded-md px-4 sm:w-auto">
            <Plus className="h-4 w-4" aria-hidden="true" />
            Створити користувача
          </Button>
        </div>
      )}
    >
      <Card className="mb-5 w-full max-w-5xl rounded-lg p-4 sm:p-5">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-[minmax(0,1fr)_180px_180px_40px] lg:items-end">
          <label className="block min-w-0 sm:col-span-2 lg:col-span-1">
            <span className="mb-2 block text-[0.75rem] font-semibold text-slate-600">Пошук за ПІБ</span>
            <span className="relative block">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
              <Input
                value={searchInput}
                onChange={(event) => setSearchInput(event.target.value)}
                placeholder="Прізвище, ім’я або по батькові"
                className="h-10 pl-10"
                aria-label="Пошук користувачів за ПІБ"
              />
            </span>
          </label>
          <FilterSelect label="Роль" value={role} options={roleOptions} onChange={(value) => { setRole(value); setPage(0); }} />
          <FilterSelect label="Статус" value={status} options={statusOptions} onChange={(value) => { setStatus(value); setPage(0); }} />
          <Button type="button" variant="ghost" onClick={loadUsers} disabled={isLoading} className="h-10 w-10 justify-self-end rounded-md border border-slate-200 p-0 text-slate-600" title="Оновити список" aria-label="Оновити список користувачів">
            <RefreshCw className={cn("h-4 w-4", isLoading && "animate-spin")} aria-hidden="true" />
          </Button>
        </div>
        {query || role || status ? (
          <button type="button" onClick={resetFilters} className="mt-3 text-[0.75rem] font-semibold text-slate-500 hover:text-slate-900">
            Очистити фільтри
          </button>
        ) : null}
      </Card>

      <Card className="overflow-hidden rounded-lg">
        {notice ? <div className="border-b border-emerald-100 bg-emerald-50 px-5 py-3 text-[0.8rem] font-medium text-emerald-700">{notice}</div> : null}
        {error ? (
          <div className="p-5">
            <Alert>{error}</Alert>
            {error.toLowerCase().includes("автор") || error.toLowerCase().includes("доступ") ? (
              <a href="#admin" className="mt-4 inline-flex text-[0.8rem] font-bold text-slate-700 hover:text-slate-950">Увійти до панелі адміністратора</a>
            ) : null}
          </div>
        ) : null}

        {!error && isLoading ? (
          <div className="flex min-h-[340px] items-center justify-center text-slate-400"><Loader2 className="h-6 w-6 animate-spin" aria-label="Завантаження" /></div>
        ) : null}
        {!error && !isLoading && data?.content?.length === 0 ? <EmptyState query={query} /> : null}
        {!error && !isLoading && data?.content?.length ? (
          <>
            <DesktopTable users={data.content} onChangeStatus={setDialog} onEdit={setEditDialog} onResetPassword={setResetDialog} onDelete={setDeleteDialog} />
            <MobileList users={data.content} onChangeStatus={setDialog} onEdit={setEditDialog} onResetPassword={setResetDialog} onDelete={setDeleteDialog} />
            <Pagination data={data} page={page} onPageChange={setPage} />
          </>
        ) : null}
      </Card>

      {dialog ? (
        <StatusDialog
          user={dialog.user}
          nextStatus={dialog.nextStatus}
          onClose={() => setDialog(null)}
          onCompleted={handleStatusChanged}
        />
      ) : null}

      {createOpen ? (
        <CreateUserDialog
          onClose={() => setCreateOpen(false)}
          onCompleted={handleCreated}
        />
      ) : null}

      {importOpen ? <UserImportDialog onClose={() => setImportOpen(false)} onImported={async (message) => {
        setNotice(message);
        if (page === 0) await loadUsers(); else setPage(0);
      }} /> : null}

      {deleteDialog ? (
        <DeleteUserDialog
          user={deleteDialog}
          onClose={() => setDeleteDialog(null)}
          onCompleted={handleDeleted}
        />
      ) : null}

      {editDialog ? (
        <EditUserDialog
          user={editDialog}
          onClose={() => setEditDialog(null)}
          onCompleted={handleEdited}
        />
      ) : null}

      {resetDialog ? (
        <ResetPasswordDialog
          user={resetDialog}
          onClose={() => setResetDialog(null)}
          onCompleted={handlePasswordReset}
        />
      ) : null}
    </AdminLayout>
  );
}

function CreateUserDialog({ onClose, onCompleted }) {
  const [form, setForm] = useState(createInitialForm);
  const [options, setOptions] = useState({ classes: [], subjects: [] });
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState("");
  const [optionsError, setOptionsError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const birthDateBounds = getBirthDateBounds(form.role);

  useEffect(() => {
    let active = true;
    registrationApi.getOptions()
      .then((value) => active && setOptions(value))
      .catch(() => active && setOptionsError("Не вдалося завантажити класи та предмети."));
    return () => { active = false; };
  }, []);

  const update = (field, value) => {
    setForm((current) => ({
      ...current,
      [field]: value,
      ...(field === "role" ? { classId: "", subjectId: "", student: null } : null),
    }));
    setErrors((current) => {
      if (field === "role") return {};
      if (!current[field]) return current;
      const next = { ...current };
      delete next[field];
      return next;
    });
    setServerError("");
  };

  const submit = async (event) => {
    event.preventDefault();
    const validationErrors = validateCreateForm(form);
    setErrors(validationErrors);
    if (Object.keys(validationErrors).length > 0) return;

    setIsSubmitting(true);
    setServerError("");
    try {
      const result = await adminUsersApi.create({
        login: form.login,
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        patronymic: form.patronymic.trim() || null,
        email: form.email.trim(),
        phone: form.role === "STUDENT" ? null : `+380${form.phone.replace(/\D/g, "")}`,
        birthDate: parseBirthDate(form.birthDate).iso,
        role: form.role,
        classId: form.role === "STUDENT" ? Number(form.classId) : null,
        subjectId: form.role === "TEACHER" ? Number(form.subjectId) : null,
        studentId: form.role === "PARENT" ? form.student.id : null,
      });
      await onCompleted(result.message);
    } catch (requestError) {
      if (requestError instanceof ApiError) {
        setServerError(requestError.message);
        setErrors((current) => ({ ...current, ...(requestError.fieldErrors || {}) }));
      } else {
        setServerError("Не вдалося створити користувача.");
      }
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section role="dialog" aria-modal="true" aria-labelledby="create-user-title" className="max-h-[calc(100vh-2rem)] w-full max-w-[720px] overflow-y-auto rounded-lg border border-slate-200 bg-white p-5 shadow-2xl sm:p-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 id="create-user-title" className="text-[1.05rem] font-bold text-slate-950">Створити користувача</h2>
            <p className="mt-1 text-[0.8rem] text-slate-500">Тимчасовий пароль буде створено автоматично.</p>
          </div>
          <button type="button" onClick={onClose} disabled={isSubmitting} className="flex h-8 w-8 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label="Закрити"><X className="h-4 w-4" /></button>
        </div>

        {serverError || optionsError ? <div className="mt-4"><Alert>{serverError || optionsError}</Alert></div> : null}

        <form onSubmit={submit} className="mt-5">
          <div className="grid gap-4 sm:grid-cols-2">
            <CreateField label="Роль" error={errors.role}>
              <select value={form.role} onChange={(event) => update("role", event.target.value)} className="h-10 w-full rounded-md border border-slate-300 bg-white px-3 text-[0.82rem] text-slate-700 outline-hidden focus:border-slate-500">
                <option value="STUDENT">Учень</option>
                <option value="TEACHER">Вчитель</option>
                <option value="PARENT">Батьки</option>
              </select>
            </CreateField>
            <CreateField label="Логін" error={errors.login}>
              <Input value={form.login} onChange={(event) => update("login", formatLogin(event.target.value))} maxLength={50} autoComplete="off" autoCapitalize="none" spellCheck={false} placeholder="student.petrenko" />
            </CreateField>
            <CreateField label="Ім’я" error={errors.firstName}>
              <Input value={form.firstName} onChange={(event) => update("firstName", formatName(event.target.value))} maxLength={50} autoComplete="off" placeholder="Олександр" />
            </CreateField>
            <CreateField label="Прізвище" error={errors.lastName}>
              <Input value={form.lastName} onChange={(event) => update("lastName", formatName(event.target.value))} maxLength={50} autoComplete="off" placeholder="Коваленко" />
            </CreateField>
            <CreateField label="По батькові (необов’язково)" error={errors.patronymic}>
              <Input value={form.patronymic} onChange={(event) => update("patronymic", formatName(event.target.value))} maxLength={50} autoComplete="off" placeholder="Олександрович" />
            </CreateField>
            <CreateField label="Електронна пошта" error={errors.email}>
              <Input type="email" value={form.email} onChange={(event) => update("email", event.target.value)} maxLength={100} autoComplete="off" placeholder="name@example.com" />
            </CreateField>
            {form.role !== "STUDENT" ? <CreateField label="Номер телефону" error={errors.phone}>
              <div className="relative">
                <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[0.82rem] text-slate-600">+380</span>
                <Input type="tel" inputMode="numeric" value={form.phone} onChange={(event) => update("phone", formatPhone(event.target.value))} className="pl-[3.8rem]" placeholder="XX XXX XX XX" />
              </div>
            </CreateField> : null}
            <CreateField label="Дата народження" error={errors.birthDate}>
              <BirthDateInput
                value={form.birthDate}
                onChange={(value) => update("birthDate", value)}
                min={birthDateBounds.min}
                max={birthDateBounds.max}
                aria-invalid={Boolean(errors.birthDate)}
              />
            </CreateField>
            {form.role === "STUDENT" ? (
              <CreateField label="Клас" error={errors.classId}>
                <select value={form.classId} onChange={(event) => update("classId", event.target.value)} className="h-10 w-full rounded-md border border-slate-300 bg-white px-3 text-[0.82rem] text-slate-700 outline-hidden focus:border-slate-500">
                  <option value="">Оберіть клас</option>
                  {options.classes.map((option) => <option key={option.id} value={option.id}>{option.label}</option>)}
                </select>
              </CreateField>
            ) : null}
            {form.role === "TEACHER" ? (
              <CreateField label="Предмет" error={errors.subjectId}>
                <select value={form.subjectId} onChange={(event) => update("subjectId", event.target.value)} className="h-10 w-full rounded-md border border-slate-300 bg-white px-3 text-[0.82rem] text-slate-700 outline-hidden focus:border-slate-500">
                  <option value="">Оберіть предмет</option>
                  {options.subjects.map((option) => <option key={option.id} value={option.id}>{option.label}</option>)}
                </select>
              </CreateField>
            ) : null}
            {form.role === "PARENT" ? (
              <AdminStudentPicker student={form.student} onChange={(student) => update("student", student)} error={errors.student} />
            ) : null}
          </div>

          <div className="mt-5 rounded-md border border-slate-200 bg-slate-50 px-4 py-3 text-[0.78rem] leading-5 text-slate-600">
            Після створення логін і тимчасовий пароль буде надіслано на вказану електронну пошту. Під час першого входу користувач повинен змінити пароль.
          </div>

          <div className="mt-6 flex justify-end gap-2">
            <Button type="button" variant="ghost" onClick={onClose} disabled={isSubmitting} className="h-10 rounded-md border border-slate-300 bg-white px-4">Скасувати</Button>
            <Button type="submit" disabled={isSubmitting} className="h-10 min-w-[152px] rounded-md px-4">
              {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
              Створити
            </Button>
          </div>
        </form>
      </section>
    </div>
  );
}

function CreateField({ label, error, children }) {
  return (
    <label className="block text-[0.8rem] font-semibold text-slate-700">
      {label}
      <div className={cn("mt-2", error && "[&_input]:border-red-400 [&_select]:border-red-400")}>{children}</div>
      {error ? <span className="mt-1.5 block text-[0.72rem] font-normal text-red-500">{error}</span> : null}
    </label>
  );
}

function FilterSelect({ label, value, options, onChange }) {
  return (
    <label className="block min-w-0">
      <span className="mb-2 block text-[0.75rem] font-semibold text-slate-600">{label}</span>
      <span className="relative block">
        <select value={value} onChange={(event) => onChange(event.target.value)} className="h-10 w-full appearance-none rounded-md border border-slate-200 bg-white px-3 pr-9 text-[0.78rem] font-semibold text-slate-600 outline-hidden transition-colors hover:border-slate-300 focus:border-slate-500">
          {options.map((option) => <option key={option.value || "all"} value={option.value}>{option.label}</option>)}
        </select>
        <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
      </span>
    </label>
  );
}

function DesktopTable({ users, onChangeStatus, onEdit, onResetPassword, onDelete }) {
  return (
    <div className="hidden overflow-x-auto xl:block">
      <table className="w-full min-w-[1120px] border-collapse text-left">
        <thead className="bg-slate-50 text-[0.7rem] font-bold uppercase text-slate-400">
          <tr><th className="px-5 py-3">Користувач</th><th className="px-4 py-3">Роль</th><th className="px-4 py-3">Статус</th><th className="px-4 py-3">Створено</th><th className="px-5 py-3 text-right">Дії</th></tr>
        </thead>
        <tbody>
          {users.map((user) => (
            <tr key={user.id} className="border-t border-slate-100 text-[0.8rem] text-slate-600 transition-colors hover:bg-slate-50/80">
              <td className="px-5 py-4"><div className="font-bold text-slate-900">{formatFullName(user) || user.login}</div><div className="mt-1 text-[0.72rem] text-slate-400">{user.login} · {user.email}</div></td>
              <td className="px-4 py-4 font-semibold">{roleLabels[user.role]}</td>
              <td className="px-4 py-4"><StatusBadge status={user.status} /></td>
              <td className="px-4 py-4 text-slate-400">{formatDate(user.createdAt)}</td>
              <td className="px-5 py-4"><UserActions user={user} onChangeStatus={onChangeStatus} onEdit={onEdit} onResetPassword={onResetPassword} onDelete={onDelete} alignRight /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function MobileList({ users, onChangeStatus, onEdit, onResetPassword, onDelete }) {
  return (
    <div className="divide-y divide-slate-100 xl:hidden">
      {users.map((user) => (
        <article key={user.id} className="px-4 py-4">
          <div className="flex items-start justify-between gap-3"><div className="min-w-0"><h2 className="truncate text-[0.88rem] font-bold text-slate-900">{formatFullName(user) || user.login}</h2><p className="mt-1 truncate text-[0.74rem] text-slate-400">{user.login} · {user.email}</p></div><StatusBadge status={user.status} /></div>
          <div className="mt-3 text-[0.75rem] font-semibold text-slate-500">{roleLabels[user.role]} · створено {formatDate(user.createdAt)}</div>
          <div className="mt-4"><UserActions user={user} onChangeStatus={onChangeStatus} onEdit={onEdit} onResetPassword={onResetPassword} onDelete={onDelete} /></div>
        </article>
      ))}
    </div>
  );
}

function UserActions({ user, onChangeStatus, onEdit, onResetPassword, onDelete, alignRight = false }) {
  const actionClass = "inline-flex h-8 items-center gap-1.5 rounded-md px-2.5 text-[0.71rem] font-bold transition-colors focus-visible:outline-hidden focus-visible:ring-2";
  return (
    <div className={cn("flex flex-wrap gap-2", alignRight && "justify-end")}>
      <button type="button" onClick={() => onEdit(user)} className={cn(actionClass, "border border-slate-300 bg-white text-slate-700 hover:bg-slate-100 focus-visible:ring-slate-400")}><Pencil className="h-3.5 w-3.5" />Редагувати</button>
      {user.role !== "ADMIN" ? <>
      <button type="button" onClick={() => onResetPassword(user)} className={cn(actionClass, "border border-sky-300 bg-sky-50 text-sky-700 hover:bg-sky-100 focus-visible:ring-sky-400")}><KeyRound className="h-3.5 w-3.5" />Скинути пароль</button>
      {user.status === "BLOCKED" ? <button type="button" onClick={() => onChangeStatus({ user, nextStatus: "ACTIVE" })} className={cn(actionClass, "border border-emerald-300 bg-emerald-50 text-emerald-700 hover:bg-emerald-100 focus-visible:ring-emerald-400")}><CheckCircle2 className="h-3.5 w-3.5" />Розблокувати</button> : null}
      {user.status === "ACTIVE" ? <button type="button" onClick={() => onChangeStatus({ user, nextStatus: "BLOCKED" })} className={cn(actionClass, "border border-amber-300 bg-amber-50 text-amber-700 hover:bg-amber-100 focus-visible:ring-amber-400")}><Ban className="h-3.5 w-3.5" />Блокувати</button> : null}
      <button type="button" onClick={() => onDelete(user)} className={cn(actionClass, "border border-red-300 bg-red-50 text-red-700 hover:bg-red-100 focus-visible:ring-red-400")}><Trash2 className="h-3.5 w-3.5" />Видалити</button>
      </> : null}
    </div>
  );
}

function EditUserDialog({ user, onClose, onCompleted }) {
  const currentUser = useContext(CurrentUserContext);
  const credentialsProtected = user.role === "ADMIN" && user.id !== currentUser?.session?.userId;
  const [form, setForm] = useState({
    login: user.login,
    firstName: user.firstName || "",
    lastName: user.lastName || "",
    patronymic: user.patronymic || "",
    email: user.email,
    phone: phoneForForm(user.phone),
    role: user.role,
  });
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const phoneEnabled = ["TEACHER", "PARENT"].includes(form.role);

  const update = (field, value) => {
    setForm((current) => ({
      ...current,
      [field]: value,
      ...(field === "role" && !["TEACHER", "PARENT"].includes(value) ? { phone: "" } : null),
    }));
    setErrors((current) => ({ ...current, [field]: "" }));
    setServerError("");
  };

  const submit = async (event) => {
    event.preventDefault();
    const validationErrors = validateEditForm(form);
    setErrors(validationErrors);
    if (Object.keys(validationErrors).length) return;

    setIsSubmitting(true);
    setServerError("");
    try {
      await adminUsersApi.update(user.id, {
        login: form.login,
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        patronymic: form.patronymic.trim() || null,
        email: form.email.trim(),
        phone: phoneEnabled ? `+380${form.phone.replace(/\D/g, "")}` : null,
        role: form.role,
      });
      await onCompleted(`Дані користувача ${form.login} оновлено.`);
    } catch (requestError) {
      if (requestError instanceof ApiError) {
        const fieldErrors = { ...(requestError.fieldErrors || {}) };
        if (requestError.code === "LOGIN_ALREADY_EXISTS") fieldErrors.login = "Цей логін уже використовується";
        if (requestError.code === "EMAIL_ALREADY_REGISTERED") fieldErrors.email = "Ця електронна пошта вже використовується";
        setErrors((current) => ({ ...current, ...fieldErrors }));
        setServerError(requestError.message);
      } else {
        setServerError("Не вдалося оновити дані користувача.");
      }
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section role="dialog" aria-modal="true" aria-labelledby="edit-user-title" className="max-h-[calc(100dvh-2rem)] w-full max-w-[640px] overflow-y-auto rounded-lg border border-slate-200 bg-white p-5 shadow-2xl sm:p-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 id="edit-user-title" className="text-[1.05rem] font-bold text-slate-950">Редагувати користувача</h2>
            <p className="mt-1 text-[0.8rem] text-slate-500">Зміна даних облікового запису та ролі</p>
          </div>
          <button type="button" onClick={onClose} disabled={isSubmitting} className="flex h-8 w-8 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label="Закрити"><X className="h-4 w-4" /></button>
        </div>

        {serverError ? <div className="mt-4"><Alert>{serverError}</Alert></div> : null}

        <form onSubmit={submit} className="mt-5">
          <div className="grid gap-4 sm:grid-cols-2">
            <CreateField label="Логін" error={errors.login}>
              <Input value={form.login} onChange={(event) => update("login", formatLogin(event.target.value))} maxLength={50} autoComplete="off" autoCapitalize="none" spellCheck={false} disabled={credentialsProtected} />
            </CreateField>
            <CreateField label="Роль" error={errors.role}>
              <select value={form.role} onChange={(event) => update("role", event.target.value)} disabled={user.role === "ADMIN"} className="h-10 w-full rounded-md border border-slate-300 bg-white px-3 text-[0.82rem] text-slate-700 outline-hidden focus:border-slate-500 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-500">
                {user.role === "ADMIN" ? <option value="ADMIN">Адміністратор</option> : null}
                <option value="STUDENT">Учень</option>
                <option value="TEACHER">Вчитель</option>
                <option value="PARENT">Батьки</option>
              </select>
            </CreateField>
            <CreateField label="Ім’я" error={errors.firstName}>
              <Input value={form.firstName} onChange={(event) => update("firstName", formatName(event.target.value))} maxLength={50} autoComplete="off" />
            </CreateField>
            <CreateField label="Прізвище" error={errors.lastName}>
              <Input value={form.lastName} onChange={(event) => update("lastName", formatName(event.target.value))} maxLength={50} autoComplete="off" />
            </CreateField>
            <CreateField label="По батькові (необов’язково)" error={errors.patronymic}>
              <Input value={form.patronymic} onChange={(event) => update("patronymic", formatName(event.target.value))} maxLength={50} autoComplete="off" />
            </CreateField>
            <CreateField label="Електронна пошта" error={errors.email}>
              <Input type="email" value={form.email} onChange={(event) => update("email", event.target.value)} maxLength={100} autoComplete="off" disabled={credentialsProtected} />
            </CreateField>
            {phoneEnabled ? <CreateField label="Номер телефону" error={errors.phone}>
              <div className="relative">
                <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[0.82rem] text-slate-600">+380</span>
                <Input type="tel" inputMode="numeric" value={form.phone} onChange={(event) => update("phone", formatPhone(event.target.value))} className="pl-[3.8rem]" placeholder="XX XXX XX XX" />
              </div>
            </CreateField> : null}
          </div>

          {user.role !== "ADMIN" && form.role !== user.role ? (
            <div className="mt-5 rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-[0.77rem] leading-5 text-amber-800">
              Роль буде змінено лише за відсутності пов’язаних оцінок, занять, класів або дітей. Після зміни активні сесії користувача завершаться.
            </div>
          ) : null}
          {user.role === "ADMIN" ? (
            <div className="mt-5 rounded-md border border-slate-200 bg-slate-50 px-4 py-3 text-[0.77rem] leading-5 text-slate-600">
              Роль адміністратора захищена та не змінюється в цьому вікні.
            </div>
          ) : null}

          <div className="mt-6 flex justify-end gap-2">
            <Button type="button" variant="ghost" onClick={onClose} disabled={isSubmitting} className="h-10 rounded-md border border-slate-300 bg-white px-4">Скасувати</Button>
            <Button type="submit" disabled={isSubmitting} className="h-10 min-w-[124px] rounded-md px-4">
              {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Pencil className="h-4 w-4" />}
              Зберегти
            </Button>
          </div>
        </form>
      </section>
    </div>
  );
}

function ResetPasswordDialog({ user, onClose, onCompleted }) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState("");

  const submit = async () => {
    setIsSubmitting(true);
    setError("");
    try {
      const response = await adminUsersApi.resetPassword(user.id);
      await onCompleted(response.message || `Тимчасовий пароль надіслано користувачу ${user.login}.`);
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : "Не вдалося скинути пароль користувача.");
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section role="dialog" aria-modal="true" aria-labelledby="reset-user-password-title" className="w-full max-w-[480px] rounded-lg border border-slate-200 bg-white p-5 shadow-2xl sm:p-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 id="reset-user-password-title" className="text-[1.05rem] font-bold text-slate-950">Скинути пароль</h2>
            <p className="mt-1 text-[0.8rem] text-slate-500">{user.login} · {roleLabels[user.role]}</p>
          </div>
          <button type="button" onClick={onClose} disabled={isSubmitting} className="flex h-8 w-8 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label="Закрити"><X className="h-4 w-4" /></button>
        </div>

        <div className="mt-5 rounded-md border border-sky-200 bg-sky-50 px-4 py-3 text-[0.8rem] leading-5 text-sky-900">
          Новий тимчасовий пароль буде згенеровано на сервері та надіслано на <strong>{user.email}</strong>. Адміністратор не побачить пароль. Усі активні сесії користувача завершаться.
        </div>
        {error ? <div className="mt-4"><Alert>{error}</Alert></div> : null}

        <div className="mt-6 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose} disabled={isSubmitting} className="h-10 rounded-md border border-slate-300 bg-white px-4">Скасувати</Button>
          <Button type="button" onClick={submit} disabled={isSubmitting} className="h-10 min-w-[152px] rounded-md bg-sky-700 px-4 text-white hover:bg-sky-600">
            {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <KeyRound className="h-4 w-4" />}
            Скинути пароль
          </Button>
        </div>
      </section>
    </div>
  );
}

function StatusDialog({ user, nextStatus, onClose, onCompleted }) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState("");
  const action = {
    ACTIVE: { title: "Розблокувати користувача", description: "Користувач знову зможе входити до системи. Усі його дані залишаються без змін.", button: "Розблокувати", icon: UserRoundCheck, color: "bg-emerald-700 hover:bg-emerald-600" },
    BLOCKED: { title: "Заблокувати користувача", description: "Вхід буде заборонений, але дані облікового запису збережуться.", button: "Блокувати", icon: Ban, color: "bg-amber-600 hover:bg-amber-500" },
  }[nextStatus];
  const Icon = action.icon;

  const submit = async () => {
    setIsSubmitting(true);
    setError("");
    try {
      await adminUsersApi.changeStatus(user.id, nextStatus);
      await onCompleted(`Статус користувача ${user.login} змінено на «${statusLabels[nextStatus]}».`);
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : "Не вдалося змінити статус користувача.");
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section role="dialog" aria-modal="true" aria-labelledby="user-status-title" className="w-full max-w-[460px] rounded-lg border border-slate-200 bg-white p-5 shadow-2xl sm:p-6">
        <div className="flex items-start justify-between gap-4"><div><h2 id="user-status-title" className="text-[1.05rem] font-bold text-slate-950">{action.title}</h2><p className="mt-1 text-[0.8rem] text-slate-500">{user.login} · {roleLabels[user.role]}</p></div><button type="button" onClick={onClose} className="flex h-8 w-8 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label="Закрити"><X className="h-4 w-4" /></button></div>
        <p className="mt-5 rounded-md border border-slate-200 bg-slate-50 px-4 py-3 text-[0.8rem] leading-5 text-slate-600">{action.description}</p>
        {error ? <div className="mt-4"><Alert>{error}</Alert></div> : null}
        <div className="mt-6 flex justify-end gap-2"><Button type="button" variant="ghost" onClick={onClose} disabled={isSubmitting} className="h-10 rounded-md border border-slate-300 bg-white px-4">Скасувати</Button><Button type="button" onClick={submit} disabled={isSubmitting} className={cn("h-10 rounded-md px-4", action.color)}>{isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Icon className="h-4 w-4" />}{action.button}</Button></div>
      </section>
    </div>
  );
}

function DeleteUserDialog({ user, onClose, onCompleted }) {
  const [eligibility, setEligibility] = useState(null);
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    let active = true;
    adminUsersApi.getDeletionEligibility(user.id)
      .then((result) => active && setEligibility(result))
      .catch((requestError) => {
        if (active) setError(requestError instanceof ApiError ? requestError.message : "Не вдалося перевірити можливість видалення.");
      });
    return () => { active = false; };
  }, [user.id]);

  const submit = async () => {
    setIsSubmitting(true);
    setError("");
    try {
      await adminUsersApi.delete(user.id);
      await onCompleted(`Користувача ${user.login} видалено.`);
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : "Не вдалося видалити користувача.");
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section role="dialog" aria-modal="true" aria-labelledby="delete-user-title" className="w-full max-w-[480px] rounded-lg border border-slate-200 bg-white p-5 shadow-2xl sm:p-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 id="delete-user-title" className="text-[1.05rem] font-bold text-slate-950">Видалити користувача</h2>
            <p className="mt-1 text-[0.8rem] text-slate-500">{user.login} · {roleLabels[user.role]}</p>
          </div>
          <button type="button" onClick={onClose} disabled={isSubmitting} className="flex h-8 w-8 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label="Закрити"><X className="h-4 w-4" /></button>
        </div>

        {!eligibility && !error ? <div className="flex min-h-28 items-center justify-center text-slate-400"><Loader2 className="h-5 w-5 animate-spin" aria-label="Перевірка" /></div> : null}
        {error ? <div className="mt-5"><Alert>{error}</Alert></div> : null}
        {eligibility && !eligibility.deletable ? (
          <div className="mt-5 rounded-md border border-amber-300 bg-amber-50 px-4 py-3 text-[0.8rem] leading-5 text-amber-800">
            {eligibility.reason}
          </div>
        ) : null}
        {eligibility?.deletable ? (
          <div className="mt-5 rounded-md border border-rose-300 bg-rose-50 px-4 py-3 text-[0.8rem] leading-5 text-rose-800">
            Обліковий запис не має пов’язаної навчальної історії. Після підтвердження його буде видалено без можливості відновлення.
          </div>
        ) : null}

        <div className="mt-6 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose} disabled={isSubmitting} className="h-10 rounded-md border border-slate-300 bg-white px-4">{eligibility?.deletable ? "Скасувати" : "Закрити"}</Button>
          {eligibility?.deletable ? (
            <Button type="button" onClick={submit} disabled={isSubmitting} className="h-10 min-w-[116px] rounded-md border border-rose-700 bg-rose-700 px-4 text-white hover:bg-rose-600">
              {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
              Видалити
            </Button>
          ) : null}
        </div>
      </section>
    </div>
  );
}

function StatusBadge({ status }) {
  const classes = { ACTIVE: "bg-emerald-50 text-emerald-700 ring-emerald-200", BLOCKED: "bg-amber-50 text-amber-700 ring-amber-200", DELETED: "bg-rose-50 text-rose-700 ring-rose-200" };
  return <span className={cn("inline-flex shrink-0 rounded-md px-2 py-1 text-[0.68rem] font-bold ring-1", classes[status])}>{statusLabels[status]}</span>;
}

function Pagination({ data, page, onPageChange }) {
  return (
    <div className="flex items-center justify-between border-t border-slate-200 px-4 py-3 text-[0.75rem] text-slate-500 sm:px-5">
      <span>{data.totalElements} користувачів</span>
      <div className="flex items-center gap-2"><button type="button" onClick={() => onPageChange(page - 1)} disabled={data.first} className="flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 disabled:opacity-40" aria-label="Попередня сторінка"><ChevronLeft className="h-4 w-4" /></button><span className="font-semibold">{page + 1} / {Math.max(data.totalPages, 1)}</span><button type="button" onClick={() => onPageChange(page + 1)} disabled={data.last} className="flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 disabled:opacity-40" aria-label="Наступна сторінка"><ChevronRight className="h-4 w-4" /></button></div>
    </div>
  );
}

function EmptyState({ query }) {
  return <div className="flex min-h-[340px] flex-col items-center justify-center px-5 text-center"><span className="flex h-12 w-12 items-center justify-center rounded-md bg-slate-100 text-slate-500"><Users className="h-5 w-5" /></span><h2 className="mt-4 text-[0.95rem] font-bold text-slate-900">{query ? "Користувачів не знайдено" : "Користувачів поки немає"}</h2><p className="mt-1 text-[0.78rem] text-slate-500">{query ? "Перевірте ПІБ або змініть пошуковий запит." : "Створіть обліковий запис користувача."}</p></div>;
}

function formatFullName(user) {
  return [user.lastName, user.firstName, user.patronymic].filter(Boolean).join(" ");
}

function formatDate(value) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("uk-UA", { day: "2-digit", month: "2-digit", year: "numeric" }).format(new Date(value));
}
