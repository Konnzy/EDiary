import { useState } from "react";
import { KeyRound, Loader2, Pencil, X } from "lucide-react";
import { Alert } from "@/components/ui/alert.jsx";
import { Button } from "@/components/ui/button.jsx";
import { Input } from "@/components/ui/input.jsx";
import { ApiError, adminUsersApi } from "@/lib/api.js";

const namePattern = /^[А-Яа-яІіЇїЄєҐґ]+(?:[ '’-][А-Яа-яІіЇїЄєҐґ]+)*$/;

function validate(form) {
  const errors = {};
  if (!/^[a-z0-9](?:[a-z0-9._-]{1,48}[a-z0-9])$/.test(form.login)) {
    errors.login = "3–50 символів; початок і кінець — літера або цифра";
  }
  if (!namePattern.test(form.firstName.trim())) errors.firstName = "Введіть ім’я українськими літерами";
  if (!namePattern.test(form.lastName.trim())) errors.lastName = "Введіть прізвище українськими літерами";
  if (form.patronymic.trim() && !namePattern.test(form.patronymic.trim())) errors.patronymic = "Введіть по батькові українськими літерами";
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) errors.email = "Некоректний формат пошти";
  return errors;
}

function Field({ label, error, children }) {
  return <label className="block text-[0.75rem] font-semibold text-slate-700">
    {label}
    <span className="mt-2 block">{children}</span>
    {error ? <span className="mt-1 block text-[0.7rem] text-red-600">{error}</span> : null}
  </label>;
}

export function AdminSelfDialog({ account, onClose, onSaved }) {
  const [form, setForm] = useState({
    login: account.login,
    firstName: account.firstName || "",
    lastName: account.lastName || "",
    patronymic: account.patronymic || "",
    email: account.email,
  });
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState("");
  const [saving, setSaving] = useState(false);
  const [passwordMode, setPasswordMode] = useState(false);
  const [passwordChanged, setPasswordChanged] = useState(false);

  const update = (field, value) => {
    setForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: "" }));
    setServerError("");
  };

  const submit = async (event) => {
    event.preventDefault();
    const nextErrors = validate(form);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) return;
    setSaving(true);
    setServerError("");
    try {
      const result = await adminUsersApi.update(account.id, {
        login: form.login,
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        patronymic: form.patronymic.trim() || null,
        email: form.email.trim(),
        phone: null,
        role: "ADMIN",
      });
      onSaved(result, account.login.toLowerCase() !== result.login.toLowerCase());
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors((current) => ({ ...current, ...error.fieldErrors }));
        setServerError(error.message);
      } else {
        setServerError("Не вдалося оновити дані адміністратора.");
      }
      setSaving(false);
    }
  };

  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && !saving && onClose()}>
    <section role="dialog" aria-modal="true" aria-labelledby="admin-self-title" onKeyDown={(event) => { if (event.key === "Escape" && !saving) onClose(); }} className="max-h-[calc(100dvh-2rem)] w-full max-w-[600px] overflow-y-auto rounded-lg border border-slate-200 bg-white p-5 shadow-2xl sm:p-6">
      <div className="flex items-start justify-between gap-4">
        <div><h2 id="admin-self-title" className="text-[1.05rem] font-bold text-slate-950">Дані адміністратора</h2><p className="mt-1 text-[0.8rem] text-slate-500">Редагування власного облікового запису</p></div>
        <button type="button" onClick={onClose} disabled={saving} className="flex h-8 w-8 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label="Закрити"><X className="h-4 w-4" /></button>
      </div>
      {passwordMode ? <AdminPasswordForm saving={saving} setSaving={setSaving}
        onBack={() => setPasswordMode(false)} onChanged={() => { setPasswordChanged(true); setPasswordMode(false); }} /> : <>
      {passwordChanged ? <p role="status" className="mt-4 rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">Пароль змінено.</p> : null}
      {serverError ? <div className="mt-4"><Alert>{serverError}</Alert></div> : null}
      <form onSubmit={submit} className="mt-5" noValidate>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Логін" error={errors.login}><Input autoFocus value={form.login} onChange={(event) => update("login", event.target.value.toLowerCase().replace(/[^a-z0-9._-]/g, "").slice(0, 50))} maxLength={50} autoComplete="off" /></Field>
          <Field label="Роль"><Input value="Адміністратор" disabled /></Field>
          <Field label="Ім’я" error={errors.firstName}><Input value={form.firstName} onChange={(event) => update("firstName", event.target.value.slice(0, 50))} maxLength={50} autoComplete="given-name" /></Field>
          <Field label="Прізвище" error={errors.lastName}><Input value={form.lastName} onChange={(event) => update("lastName", event.target.value.slice(0, 50))} maxLength={50} autoComplete="family-name" /></Field>
          <Field label="По батькові (необов’язково)" error={errors.patronymic}><Input value={form.patronymic} onChange={(event) => update("patronymic", event.target.value.slice(0, 50))} maxLength={50} autoComplete="off" /></Field>
          <Field label="Електронна пошта" error={errors.email}><Input type="email" value={form.email} onChange={(event) => update("email", event.target.value.slice(0, 100))} maxLength={100} autoComplete="email" /></Field>
        </div>
        {form.login.toLowerCase() !== account.login.toLowerCase() ? <p className="mt-5 rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-[0.77rem] text-amber-800">Після зміни логіна потрібно буде увійти знову.</p> : null}
        <div className="mt-6 flex flex-wrap justify-between gap-2">
          <Button type="button" variant="ghost" disabled={saving} onClick={() => { setPasswordChanged(false); setPasswordMode(true); }} className="h-10 rounded-md px-3">
            <KeyRound className="h-4 w-4" />Змінити пароль
          </Button>
          <div className="ml-auto flex flex-wrap justify-end gap-2">
            <Button type="button" variant="ghost" onClick={onClose} disabled={saving} className="h-10 rounded-md border border-slate-300 bg-white px-4">Скасувати</Button>
            <Button type="submit" disabled={saving} className="h-10 rounded-md px-4">{saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Pencil className="h-4 w-4" />}Зберегти</Button>
          </div>
        </div>
      </form>
      </>}
    </section>
  </div>;
}

function AdminPasswordForm({ saving, setSaving, onBack, onChanged }) {
  const [current, setCurrent] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [error, setError] = useState("");

  const submit = async (event) => {
    event.preventDefault();
    if (saving) return;
    if (!current.trim()) return setError("Введіть поточний пароль.");
    if (password.length < 8 || !/\p{L}/u.test(password) || !/[0-9]/.test(password)) {
      return setError("Новий пароль має містити щонайменше 8 символів, літери та цифри.");
    }
    if (new TextEncoder().encode(password).length > 72) return setError("Новий пароль не може перевищувати 72 байти у UTF-8.");
    if (password === current) return setError("Новий пароль має відрізнятися від поточного.");
    if (password !== confirmation) return setError("Паролі не збігаються.");

    setError("");
    setSaving(true);
    try {
      await adminUsersApi.changeOwnPassword(current, password);
      onChanged();
    } catch (requestError) {
      setError(requestError.fieldErrors?.newPassword || requestError.message || "Не вдалося змінити пароль.");
    } finally {
      setSaving(false);
    }
  };

  return <form onSubmit={submit} className="mt-5 space-y-4" noValidate>
    <h3 className="text-sm font-bold text-slate-900">Зміна пароля</h3>
    {error ? <Alert>{error}</Alert> : null}
    <Field label="Поточний пароль">
      <Input autoFocus required type="password" name="currentPassword" autoComplete="current-password" value={current} onChange={(event) => setCurrent(event.target.value)} disabled={saving} />
    </Field>
    <Field label="Новий пароль">
      <Input required type="password" name="newPassword" autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} disabled={saving} />
    </Field>
    <Field label="Підтвердити новий пароль">
      <Input required type="password" name="confirmPassword" autoComplete="new-password" value={confirmation} onChange={(event) => setConfirmation(event.target.value)} disabled={saving} />
    </Field>
    <div className="flex flex-wrap justify-end gap-2 pt-2">
      <Button type="button" variant="ghost" onClick={onBack} disabled={saving} className="h-10 rounded-md border border-slate-300 bg-white px-4">Назад</Button>
      <Button type="submit" disabled={saving} className="h-10 rounded-md px-4">
        {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <KeyRound className="h-4 w-4" />}Змінити пароль
      </Button>
    </div>
  </form>;
}
