import { useCallback, useEffect, useState } from "react";
import {
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Loader2,
  Mail,
  RefreshCw,
  X,
} from "lucide-react";
import { Alert } from "@/components/ui/alert.jsx";
import { Button } from "@/components/ui/button.jsx";
import { Card } from "@/components/ui/card.jsx";
import { Input } from "@/components/ui/input.jsx";
import { ApiError, adminRegistrationApi } from "@/lib/api.js";
import { cn } from "@/lib/utils.js";
import { AdminLayout } from "@/pages/AdminHome/AdminLayout.jsx";

const statusOptions = [
  { value: "", label: "Усі статуси" },
  { value: "PENDING", label: "На розгляді" },
  { value: "APPROVED", label: "Схвалено" },
  { value: "REJECTED", label: "Відхилено" },
];

const roleOptions = [
  { value: "", label: "Усі ролі" },
  { value: "STUDENT", label: "Учень" },
  { value: "PARENT", label: "Батьки" },
  { value: "TEACHER", label: "Вчитель" },
];
const roleLabels = { STUDENT: "Учень", PARENT: "Батьки", TEACHER: "Вчитель" };
const statusLabels = { PENDING: "На розгляді", APPROVED: "Схвалено", REJECTED: "Відхилено" };
const requestFilterKey = "admin-request-status-filter";

function initialStatus() {
  const savedStatus = window.sessionStorage.getItem(requestFilterKey);
  window.sessionStorage.removeItem(requestFilterKey);
  return statusOptions.some((option) => option.value === savedStatus) ? savedStatus : "";
}

function requestContext(request) {
  if (request.role === "PARENT" && request.requestedStudent) {
    const className = request.requestedStudent.className ? ` · ${request.requestedStudent.className}` : "";
    return `${request.requestedStudent.fullName}${className}`;
  }
  return request.requestedClass?.label || request.requestedSubject?.label || "—";
}

export function AdminRequestsPage() {
  const [status, setStatus] = useState(initialStatus);
  const [role, setRole] = useState("");
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const [dialog, setDialog] = useState(null);
  const [notice, setNotice] = useState("");

  const loadRequests = useCallback(async () => {
    setIsLoading(true);
    setError("");
    try {
      const result = await adminRegistrationApi.findAll({ status, role, page, size: 15 });
      setData(result);
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : "Не вдалося завантажити заявки.");
    } finally {
      setIsLoading(false);
    }
  }, [page, role, status]);

  useEffect(() => {
    loadRequests();
  }, [loadRequests]);

  const changeStatus = (nextStatus) => {
    setStatus(nextStatus);
    setPage(0);
    setNotice("");
  };

  useEffect(() => {
    const handleFilter = (event) => changeStatus(event.detail?.status || "");
    window.addEventListener("admin-request-filter", handleFilter);
    return () => window.removeEventListener("admin-request-filter", handleFilter);
  }, []);

  const handleDecisionCompleted = async (message) => {
    setDialog(null);
    setNotice(message);
    await loadRequests();
    window.dispatchEvent(new Event("registration-requests-updated"));
  };

  return (
    <AdminLayout
      activeItem="Заявки"
      title="Заявки на реєстрацію"
      subtitle="Перевірка даних і створення облікових записів користувачів."
    >
      <Card className="mb-4 grid w-full max-w-[820px] grid-cols-1 gap-3 rounded-lg p-4 sm:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] sm:items-end sm:p-5">
        <FilterSelect label="Статус заявки" value={status} options={statusOptions} onChange={(value) => changeStatus(value)} />
        <FilterSelect label="Роль" value={role} options={roleOptions} onChange={(value) => { setRole(value); setPage(0); }} />
        <div className="flex items-center justify-between gap-3 sm:justify-end">
          {data ? <span className="whitespace-nowrap text-[0.75rem] text-slate-500">{data.totalElements} заявок</span> : null}
          <Button type="button" variant="ghost" onClick={loadRequests} disabled={isLoading} title="Оновити заявки" aria-label="Оновити заявки" className="h-10 w-10 shrink-0 rounded-md border border-slate-200 p-0 text-slate-600">
            <RefreshCw className={cn("h-4 w-4", isLoading && "animate-spin")} aria-hidden="true" />
          </Button>
        </div>
      </Card>

      {notice ? <div className="mb-4 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-[0.8rem] font-medium text-emerald-700">{notice}</div> : null}

      <Card className="overflow-hidden rounded-lg">
        {error ? (
          <div className="p-5">
            <Alert>{error}</Alert>
            <a href="#admin" className="mt-4 inline-flex text-[0.8rem] font-bold text-slate-700 hover:text-slate-950">Увійти до панелі адміністратора</a>
          </div>
        ) : null}

        {!error && isLoading ? (
          <div className="flex min-h-[240px] items-center justify-center text-slate-400"><Loader2 className="h-6 w-6 animate-spin" aria-label="Завантаження" /></div>
        ) : null}

        {!error && !isLoading && data?.content?.length === 0 ? <EmptyState /> : null}
        {!error && !isLoading && data?.content?.length ? (
          <>
            <DesktopTable requests={data.content} onDecision={setDialog} />
            <MobileList requests={data.content} onDecision={setDialog} />
            <Pagination data={data} page={page} onPageChange={setPage} />
          </>
        ) : null}
      </Card>

      {dialog ? (
        <DecisionDialog
          type={dialog.type}
          request={dialog.request}
          onClose={() => setDialog(null)}
          onCompleted={handleDecisionCompleted}
        />
      ) : null}
    </AdminLayout>
  );
}

function FilterSelect({ label, value, options, onChange }) {
  return (
    <label className="block min-w-0">
      <span className="mb-2 block text-[0.75rem] font-semibold text-slate-600">{label}</span>
      <span className="relative block">
        <select value={value} onChange={(event) => onChange(event.target.value)} className="h-10 w-full appearance-none rounded-md border border-slate-300 bg-white px-3 pr-9 text-[0.82rem] text-slate-700 outline-hidden transition-colors hover:border-slate-400 focus:border-slate-500">
          {options.map((option) => <option key={option.value || "all"} value={option.value}>{option.label}</option>)}
        </select>
        <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
      </span>
    </label>
  );
}

function DesktopTable({ requests, onDecision }) {
  return (
    <div className="hidden overflow-x-auto md:block">
      <table className="w-full min-w-[900px] border-collapse text-left">
        <thead className="bg-slate-50 text-[0.7rem] font-bold uppercase text-slate-400">
          <tr><th className="px-5 py-3">Заявник</th><th className="px-4 py-3">Роль</th><th className="px-4 py-3">Клас / предмет / дитина</th><th className="px-4 py-3">Дата</th><th className="px-4 py-3 text-center">Статус</th><th className="px-5 py-3 text-right">Дії</th></tr>
        </thead>
        <tbody>
          {requests.map((request) => (
            <tr key={request.id} className="border-t border-slate-100 text-[0.8rem] text-slate-600 transition-colors hover:bg-slate-50/80">
              <td className="px-5 py-4"><div className="font-bold text-slate-900">{formatFullName(request)}</div><div className="mt-1 text-[0.72rem] text-slate-400">{request.email} · {request.phone}</div></td>
              <td className="px-4 py-4 font-semibold">{roleLabels[request.role]}</td>
              <td className="px-4 py-4">{requestContext(request)}</td>
              <td className="px-4 py-4 text-slate-400">{formatDate(request.submittedAt)}</td>
              <td className="px-4 py-4 text-center"><StatusBadge status={request.status} /></td>
              <td className="px-5 py-4"><DecisionButtons request={request} onDecision={onDecision} alignRight /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function MobileList({ requests, onDecision }) {
  return (
    <div className="divide-y divide-slate-100 md:hidden">
      {requests.map((request) => (
        <article key={request.id} className="px-4 py-4">
          <div className="flex items-start justify-between gap-3"><div className="min-w-0"><h2 className="truncate text-[0.88rem] font-bold text-slate-900">{formatFullName(request)}</h2><p className="mt-1 truncate text-[0.74rem] text-slate-400">{request.email}</p></div><StatusBadge status={request.status} /></div>
          <div className="mt-3 flex items-center justify-between gap-3 text-[0.75rem] text-slate-500"><span>{roleLabels[request.role]} · {requestContext(request)}</span><span>{formatDate(request.submittedAt)}</span></div>
          <div className="mt-4"><DecisionButtons request={request} onDecision={onDecision} /></div>
        </article>
      ))}
    </div>
  );
}

function DecisionButtons({ request, onDecision, alignRight = false }) {
  if (request.status !== "PENDING") {
    return <div className={cn("text-[0.72rem] text-slate-400", alignRight && "text-right")}>{request.createdUserLogin ? `Логін: ${request.createdUserLogin}` : request.rejectionReason || "Розглянуто"}</div>;
  }
  return (
    <div className={cn("flex gap-2", alignRight && "justify-end")}>
      <button type="button" onClick={() => onDecision({ type: "approve", request })} className="inline-flex h-8 items-center gap-1.5 rounded-md bg-emerald-50 px-2.5 text-[0.72rem] font-bold text-emerald-700 ring-1 ring-inset ring-emerald-200 transition-colors hover:bg-emerald-100 hover:ring-emerald-300 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-500" title="Схвалити заявку"><Check className="h-3.5 w-3.5" aria-hidden="true" />Схвалити</button>
      <button type="button" onClick={() => onDecision({ type: "reject", request })} className="inline-flex h-8 items-center gap-1.5 rounded-md bg-rose-50 px-2.5 text-[0.72rem] font-bold text-rose-700 ring-1 ring-inset ring-rose-200 transition-colors hover:bg-rose-100 hover:ring-rose-300 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-rose-500" title="Відхилити заявку"><X className="h-3.5 w-3.5" aria-hidden="true" />Відхилити</button>
    </div>
  );
}

function DecisionDialog({ type, request, onClose, onCompleted }) {
  const [login, setLogin] = useState("");
  const [reason, setReason] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState("");
  const isApproval = type === "approve";

  const updateLogin = (value) => {
    setLogin(
      value
        .toLowerCase()
        .replace(/[^a-z0-9._-]/g, "")
        .replace(/^[._-]+/, "")
        .slice(0, 50),
    );
    setError("");
  };

  const submit = async () => {
    const normalizedLogin = login.trim().toLowerCase();
    if (isApproval && !/^[a-z0-9](?:[a-z0-9._-]{1,48}[a-z0-9])$/.test(normalizedLogin)) {
      setError("Логін має містити 3–50 символів, лише латинські літери, цифри, крапку, дефіс або підкреслення. Починайте та завершуйте логін літерою або цифрою.");
      return;
    }
    if (!isApproval && reason.trim().length < 5) {
      setError("Вкажіть причину відхилення щонайменше з 5 символів.");
      return;
    }
    setIsSubmitting(true);
    setError("");
    try {
      const result = isApproval
        ? await adminRegistrationApi.approve(request.id, normalizedLogin)
        : await adminRegistrationApi.reject(request.id, reason.trim());
      await onCompleted(result.message);
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : "Не вдалося обробити заявку.");
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section role="dialog" aria-modal="true" aria-labelledby="decision-title" className="max-h-[calc(100vh-2rem)] w-full max-w-[520px] overflow-y-auto rounded-lg border border-slate-200 bg-white p-5 shadow-2xl sm:p-6">
        <div className="flex items-start justify-between gap-4"><div><h2 id="decision-title" className="text-[1.05rem] font-bold text-slate-950">{isApproval ? "Схвалити заявку" : "Відхилити заявку"}</h2><p className="mt-1 text-[0.8rem] text-slate-500">{formatFullName(request)} · {roleLabels[request.role]}</p></div><button type="button" onClick={onClose} className="flex h-8 w-8 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label="Закрити"><X className="h-4 w-4" /></button></div>
        <dl className="mt-5 grid grid-cols-1 gap-x-5 gap-y-3 border-y border-slate-100 py-4 text-[0.78rem] sm:grid-cols-2">
          <RequestDetail label="Електронна пошта" value={request.email} />
          <RequestDetail label="Телефон" value={request.phone} />
          <RequestDetail label="Дата народження" value={formatDate(request.birthDate)} />
          <RequestDetail label={request.role === "TEACHER" ? "Предмет" : request.role === "PARENT" ? "Дитина" : "Клас"} value={requestContext(request)} />
        </dl>
        {isApproval ? (
          <div className="mt-5">
            <label htmlFor="approval-login" className="text-[0.8rem] font-semibold text-slate-700">Логін користувача</label>
            <Input
              id="approval-login"
              value={login}
              onChange={(event) => updateLogin(event.target.value)}
              minLength={3}
              maxLength={50}
              pattern="[a-z0-9](?:[a-z0-9._-]{1,48}[a-z0-9])"
              autoComplete="off"
              autoCapitalize="none"
              spellCheck={false}
              className="mt-2"
              placeholder="Наприклад: student.petrenko"
              aria-describedby="approval-login-help"
            />
            <p id="approval-login-help" className="mt-2 text-[0.72rem] leading-5 text-slate-500">Латинські літери, цифри та символи `.`, `-`, `_`. Після схвалення логін і тимчасовий пароль буде надіслано заявнику електронною поштою.</p>
          </div>
        ) : (
          <div className="mt-5 rounded-md border border-slate-200 bg-slate-50 px-4 py-3 text-[0.78rem] leading-5 text-slate-600">Заявник отримає повідомлення з указаною причиною відхилення.</div>
        )}
        {!isApproval ? <div className="mt-5"><label htmlFor="rejection-reason" className="text-[0.8rem] font-semibold text-slate-700">Причина відхилення</label><textarea id="rejection-reason" value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} rows={4} className="mt-2 w-full resize-none rounded-md border border-slate-300 bg-white px-3 py-2 text-[0.82rem] text-slate-800 outline-hidden transition-colors focus:border-slate-500" placeholder="Вкажіть, які дані потрібно уточнити" /></div> : null}
        {error ? <div className="mt-4"><Alert>{error}</Alert></div> : null}
        <div className="mt-6 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose} disabled={isSubmitting} className="h-10 rounded-md border border-slate-300 bg-white px-4 text-slate-600 hover:border-slate-400 hover:bg-slate-50">Скасувати</Button>
          <Button type="button" variant="ghost" onClick={submit} disabled={isSubmitting} className={cn("h-10 min-w-[116px] rounded-md border px-4", isApproval ? "border-emerald-200 bg-emerald-50 text-emerald-700 hover:border-emerald-300 hover:bg-emerald-100" : "border-rose-200 bg-rose-50 text-rose-700 hover:border-rose-300 hover:bg-rose-100")}>
            {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : isApproval ? <Check className="h-4 w-4" /> : <X className="h-4 w-4" />}{isApproval ? "Схвалити" : "Відхилити"}
          </Button>
        </div>
      </section>
    </div>
  );
}

function RequestDetail({ label, value }) {
  return (
    <div>
      <dt className="text-[0.68rem] font-bold uppercase text-slate-400">{label}</dt>
      <dd className="mt-1 break-words font-semibold text-slate-700">{value}</dd>
    </div>
  );
}

function StatusBadge({ status }) {
  const classes = { APPROVED: "bg-emerald-50 text-emerald-700 ring-emerald-200", REJECTED: "bg-rose-50 text-rose-700 ring-rose-200", PENDING: "bg-amber-50 text-amber-700 ring-amber-200" };
  return <span className={cn("inline-flex shrink-0 rounded-md px-2 py-1 text-[0.68rem] font-bold ring-1", classes[status])}>{statusLabels[status]}</span>;
}

function Pagination({ data, page, onPageChange }) {
  return (
    <div className="flex items-center justify-between border-t border-slate-200 px-4 py-3 text-[0.75rem] text-slate-500 sm:px-5">
      <span>{data.totalElements} заявок</span>
      <div className="flex items-center gap-2"><button type="button" onClick={() => onPageChange(page - 1)} disabled={data.first} className="flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 disabled:opacity-40" aria-label="Попередня сторінка"><ChevronLeft className="h-4 w-4" /></button><span className="font-semibold">{page + 1} / {Math.max(data.totalPages, 1)}</span><button type="button" onClick={() => onPageChange(page + 1)} disabled={data.last} className="flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 disabled:opacity-40" aria-label="Наступна сторінка"><ChevronRight className="h-4 w-4" /></button></div>
    </div>
  );
}

function EmptyState() {
  return <div className="flex min-h-[240px] flex-col items-center justify-center px-5 py-8 text-center"><span className="flex h-11 w-11 items-center justify-center rounded-md bg-slate-100 text-slate-500"><Mail className="h-5 w-5" /></span><h2 className="mt-3 text-[0.95rem] font-bold text-slate-900">Заявок не знайдено</h2><p className="mt-1 text-[0.78rem] text-slate-500">Для вибраного статусу поки немає записів.</p></div>;
}

function formatDate(value) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("uk-UA", { day: "2-digit", month: "2-digit", year: "numeric" }).format(new Date(value));
}

function formatFullName(request) {
  return [request.lastName, request.firstName, request.patronymic].filter(Boolean).join(" ");
}
