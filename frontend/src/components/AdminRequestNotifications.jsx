import { useCallback, useEffect, useRef, useState } from "react";
import { Bell, CheckCircle2, Loader2, MailPlus, RefreshCw, XCircle, X, Trash2 } from "lucide-react";
import { adminRegistrationApi } from "@/lib/api.js";
import { cn } from "@/lib/utils.js";
import { NotificationClearDialog } from "@/components/NotificationClearDialog.jsx";

const statusContent = {
  PENDING: {
    label: "Нова заявка",
    Icon: MailPlus,
    iconClass: "bg-amber-50 text-amber-700 ring-amber-200",
  },
  APPROVED: {
    label: "Заявку схвалено",
    Icon: CheckCircle2,
    iconClass: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  },
  REJECTED: {
    label: "Заявку відхилено",
    Icon: XCircle,
    iconClass: "bg-rose-50 text-rose-700 ring-rose-200",
  },
};

const roleLabels = { STUDENT: "Учень", PARENT: "Батьки", TEACHER: "Вчитель" };
const requestFilterKey = "admin-request-status-filter";

function navigateToRequests(status) {
  window.sessionStorage.setItem(requestFilterKey, status);
  window.dispatchEvent(new CustomEvent("admin-request-filter", { detail: { status } }));
}

export function AdminRequestNotifications() {
  const containerRef = useRef(null);
  const [open, setOpen] = useState(false);
  const [feed, setFeed] = useState({ pendingCount: 0, items: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [confirmClear, setConfirmClear] = useState(false);
  const hide = async (id) => {
    setBusy(true);setError("");
    try {
      if(id==null)await adminRegistrationApi.clearNotifications();else await adminRegistrationApi.hideNotification(id);
      setFeed(current=>({...current,items:current.items.filter(item=>id==null?item.status==="PENDING":item.id!==id)}));
      await loadFeed({quiet:true});
    }
    catch(e) { if(id==null)throw e;setError(e.message || "Не вдалося приховати події."); }
    finally { setBusy(false); }
  };

  const loadFeed = useCallback(async ({ quiet = false } = {}) => {
    if (!quiet) setLoading(true);
    setError("");
    try {
      setFeed(await adminRegistrationApi.findNotifications());
    } catch (requestError) {
      if (!quiet) setError(requestError.message || "Не вдалося завантажити події заявок.");
    } finally {
      if (!quiet) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadFeed();
    const intervalId = window.setInterval(() => void loadFeed({ quiet: true }), 60_000);
    const handleUpdated = () => void loadFeed({ quiet: true });
    window.addEventListener("registration-requests-updated", handleUpdated);
    return () => {
      window.clearInterval(intervalId);
      window.removeEventListener("registration-requests-updated", handleUpdated);
    };
  }, [loadFeed]);

  useEffect(() => {
    if (!open || confirmClear) return undefined;
    const handlePointerDown = (event) => {
      if (!containerRef.current?.contains(event.target)) setOpen(false);
    };
    const handleKeyDown = (event) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open, confirmClear]);

  const toggle = () => {
    const nextOpen = !open;
    setOpen(nextOpen);
    if (nextOpen) void loadFeed();
  };

  const pendingLabel = feed.pendingCount > 99 ? "99+" : String(feed.pendingCount);

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={toggle}
        className="relative flex h-9 w-9 items-center justify-center rounded-md text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-900 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 sm:h-10 sm:w-10"
        aria-label={feed.pendingCount ? `Заявки: ${feed.pendingCount} очікують рішення` : "Події заявок"}
        aria-haspopup="dialog"
        aria-expanded={open}
      >
        <Bell className="h-5 w-5 text-slate-600" strokeWidth={2.1} aria-hidden="true" />
        {feed.pendingCount > 0 ? (
          <span className="absolute -right-0.5 -top-0.5 flex min-h-4 min-w-4 items-center justify-center rounded-full bg-rose-600 px-1 text-[0.6rem] font-bold leading-4 text-white ring-2 ring-white">
            {pendingLabel}
          </span>
        ) : null}
      </button>

      {open ? (
        <section
          role="dialog"
          aria-label="Останні події заявок"
          className="fixed inset-x-3 top-[4.5rem] z-50 overflow-hidden rounded-lg border border-slate-200 bg-white shadow-xl sm:absolute sm:inset-x-auto sm:right-0 sm:top-[calc(100%+0.65rem)] sm:w-[390px]"
        >
          <header className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
            <div>
              <h2 className="text-[0.88rem] font-bold text-slate-950">Події заявок</h2>
              <p className="mt-0.5 text-[0.7rem] text-slate-500">
                {feed.pendingCount ? `${feed.pendingCount} очікують рішення` : "Нових заявок немає"}
              </p>
            </div>
            <button
              type="button"
              onClick={() => void loadFeed()}
              disabled={loading}
              className="flex h-8 w-8 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-50"
              aria-label="Оновити події"
            >
              <RefreshCw className={cn("h-4 w-4", loading && "animate-spin")} aria-hidden="true" />
            </button>
          </header>

          <div className="max-h-[min(410px,calc(100dvh-15rem))] overflow-y-auto">
            {loading ? (
              <div className="flex min-h-44 items-center justify-center text-slate-400">
                <Loader2 className="h-5 w-5 animate-spin" aria-label="Завантаження подій" />
              </div>
            ) : null}
            {!loading && error ? (
              <div className="px-5 py-7 text-center">
                <p className="text-[0.78rem] text-rose-700">{error}</p>
                <button type="button" onClick={() => void loadFeed()} className="mt-3 text-[0.75rem] font-bold text-slate-700 hover:text-slate-950">Спробувати ще раз</button>
              </div>
            ) : null}
            {!loading && !error && feed.items.length === 0 ? (
              <div className="px-5 py-8 text-center">
                <Bell className="mx-auto h-6 w-6 text-slate-300" aria-hidden="true" />
                <p className="mt-3 text-[0.8rem] font-semibold text-slate-700">Подій поки немає</p>
                <p className="mt-1 text-[0.72rem] text-slate-400">Тут з’являться нові заявки та останні рішення.</p>
              </div>
            ) : null}
            {!loading && !error ? feed.items.map((item) => (
              <NotificationItem key={item.id} item={item} busy={busy} onHide={() => hide(item.id)} onNavigate={() => {
                navigateToRequests(item.status);
                setOpen(false);
              }} />
            )) : null}
          </div>

          <button type="button" disabled={busy||loading||!feed.items.some(item=>item.status!=="PENDING")} onClick={() => setConfirmClear(true)} className="flex h-10 w-full items-center justify-center gap-2 border-t border-slate-200 text-[0.75rem] font-semibold text-slate-500 hover:bg-slate-50 disabled:opacity-50"><Trash2 className="h-3.5 w-3.5" aria-hidden="true" />Очистити сповіщення</button>
          <a
            href="#admin/requests"
            onClick={() => {
              navigateToRequests("");
              setOpen(false);
            }}
            className="flex h-11 items-center justify-center border-t border-slate-200 text-[0.76rem] font-bold text-slate-700 transition-colors hover:bg-slate-50 hover:text-slate-950"
          >
            Переглянути всі заявки
          </a>
        </section>
      ) : null}
      {confirmClear ? <NotificationClearDialog admin onConfirm={() => hide(null)} onClose={() => setConfirmClear(false)} /> : null}
    </div>
  );
}

function NotificationItem({ item, onNavigate, onHide, busy }) {
  const content = statusContent[item.status] || statusContent.PENDING;
  const Icon = content.Icon;
  const eventTime = item.status === "PENDING" ? item.submittedAt : item.reviewedAt;
  const fullName = [item.lastName, item.firstName, item.patronymic].filter(Boolean).join(" ");

  return (
    <div className="relative border-b border-slate-100 last:border-b-0"><a href="#admin/requests" onClick={onNavigate} className={cn("flex gap-3 px-4 py-3 transition-colors hover:bg-slate-50 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-slate-400",item.status!=="PENDING"&&"pr-12")}>
      <span className={cn("mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-md ring-1 ring-inset", content.iconClass)}>
        <Icon className="h-4 w-4" aria-hidden="true" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex items-start justify-between gap-3">
          <span className="text-[0.76rem] font-bold text-slate-900">{content.label}</span>
          <time dateTime={eventTime || undefined} className="shrink-0 text-[0.65rem] text-slate-400">{formatRelativeTime(eventTime)}</time>
        </span>
        <span className="mt-1 block truncate text-[0.75rem] font-semibold text-slate-700">{fullName}</span>
        <span className="mt-0.5 block text-[0.68rem] text-slate-400">{roleLabels[item.role] || item.role}</span>
      </span>
    </a>{item.status!=="PENDING"?<button type="button" onClick={onHide} disabled={busy} title="Приховати подію" aria-label={`Приховати подію: ${fullName}`} className="absolute right-2 top-3 flex h-7 w-7 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-40"><X className="h-3.5 w-3.5" aria-hidden="true" /></button>:null}</div>
  );
}

function formatRelativeTime(value) {
  if (!value) return "";
  const date = new Date(value);
  const difference = date.getTime() - Date.now();
  const absolute = Math.abs(difference);
  const formatter = new Intl.RelativeTimeFormat("uk-UA", { numeric: "auto" });
  if (absolute < 60 * 60 * 1000) return formatter.format(Math.round(difference / 60_000), "minute");
  if (absolute < 24 * 60 * 60 * 1000) return formatter.format(Math.round(difference / 3_600_000), "hour");
  if (absolute < 7 * 24 * 60 * 60 * 1000) return formatter.format(Math.round(difference / 86_400_000), "day");
  return new Intl.DateTimeFormat("uk-UA", { day: "2-digit", month: "2-digit" }).format(date);
}
