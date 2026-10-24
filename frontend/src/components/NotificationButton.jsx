import { useCallback, useEffect, useRef, useState } from "react";
import { Bell, CheckCheck, Loader2, RefreshCw, Settings2, X } from "lucide-react";
import { userNotificationApi } from "@/lib/api.js";

function relativeTime(value) {
  if (!value) return "";
  const difference = new Date(value).getTime() - Date.now();
  const absolute = Math.abs(difference);
  const formatter = new Intl.RelativeTimeFormat("uk-UA", { numeric: "auto" });
  if (absolute < 3_600_000) return formatter.format(Math.round(difference / 60_000), "minute");
  if (absolute < 86_400_000) return formatter.format(Math.round(difference / 3_600_000), "hour");
  if (absolute < 604_800_000) return formatter.format(Math.round(difference / 86_400_000), "day");
  return new Intl.DateTimeFormat("uk-UA", { day: "2-digit", month: "2-digit" }).format(new Date(value));
}

export function NotificationButton() {
  const container = useRef(null);
  const [open, setOpen] = useState(false);
  const [feed, setFeed] = useState({ unreadCount: 0, items: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [dismissing, setDismissing] = useState([]);
  const role = window.location.hash.slice(1).split("/")[0];

  const loadFeed = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true);
    try {
      setFeed(await userNotificationApi.feed());
      setError("");
    } catch (requestError) {
      if (!quiet) setError(requestError.message || "Не вдалося завантажити сповіщення.");
    } finally {
      if (!quiet) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadFeed();
    const interval = window.setInterval(() => void loadFeed(true), 60_000);
    return () => window.clearInterval(interval);
  }, [loadFeed]);

  useEffect(() => {
    if (!open) return undefined;
    const onPointerDown = (event) => { if (!container.current?.contains(event.target)) setOpen(false); };
    const onKeyDown = (event) => { if (event.key === "Escape") setOpen(false); };
    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);

  const markAllRead = async () => {
    try {
      await userNotificationApi.markAllRead();
      await loadFeed(true);
    } catch (requestError) {
      setError(requestError.message || "Не вдалося оновити сповіщення.");
    }
  };

  const dismiss = async (id) => {
    setDismissing((current) => [...current, id]);
    try {
      await userNotificationApi.dismiss(id);
      await loadFeed(true);
    } catch (requestError) {
      setError(requestError.message || "Не вдалося прибрати сповіщення.");
    } finally {
      setDismissing((current) => current.filter((value) => value !== id));
    }
  };

  return <div ref={container} className="relative">
    <button type="button" onClick={() => { setOpen((value) => !value); if (!open) void loadFeed(); }}
      className="relative flex h-9 w-9 items-center justify-center rounded-md text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-900 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 sm:h-10 sm:w-10"
      aria-label={feed.unreadCount ? `Сповіщення: ${feed.unreadCount} непрочитаних` : "Сповіщення"}
      aria-haspopup="dialog" aria-expanded={open}>
      <Bell className="h-5 w-5 text-slate-600" strokeWidth={2.1} aria-hidden="true" />
      {feed.unreadCount > 0 ? <span className="absolute -right-0.5 -top-0.5 flex min-h-4 min-w-4 items-center justify-center rounded-full bg-rose-600 px-1 text-[0.6rem] font-bold text-white ring-2 ring-white">{feed.unreadCount > 99 ? "99+" : feed.unreadCount}</span> : null}
    </button>
    {open ? <section role="dialog" aria-label="Сповіщення" className="fixed inset-x-3 top-[4.5rem] z-50 overflow-hidden rounded-lg border border-slate-200 bg-white shadow-xl sm:absolute sm:inset-x-auto sm:right-0 sm:top-[calc(100%+0.65rem)] sm:w-[390px]">
      <header className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
        <div><h2 className="text-[0.88rem] font-bold text-slate-950">Сповіщення</h2><p className="mt-0.5 text-[0.7rem] text-slate-500">{feed.unreadCount ? `${feed.unreadCount} непрочитаних` : "Усе переглянуто"}</p></div>
        <div className="flex items-center gap-1">
          {feed.unreadCount > 0 ? <button type="button" onClick={() => void markAllRead()} aria-label="Позначити всі як прочитані" title="Позначити всі як прочитані" className="flex h-8 w-8 items-center justify-center rounded-md text-slate-500 hover:bg-slate-100"><CheckCheck className="h-4 w-4" /></button> : null}
          <button type="button" onClick={() => void loadFeed()} aria-label="Оновити сповіщення" title="Оновити" className="flex h-8 w-8 items-center justify-center rounded-md text-slate-500 hover:bg-slate-100"><RefreshCw className="h-4 w-4" /></button>
        </div>
      </header>
      <div className="max-h-[min(410px,calc(100dvh-10rem))] overflow-y-auto">
        {loading ? <div className="flex min-h-40 items-center justify-center text-slate-400"><Loader2 className="h-5 w-5 animate-spin" aria-label="Завантаження" /></div> : null}
        {!loading && error ? <div className="px-5 py-8 text-center text-sm text-rose-700">{error}</div> : null}
        {!loading && !error && !feed.items.length ? <div className="px-5 py-8 text-center"><Bell className="mx-auto h-6 w-6 text-slate-300" /><p className="mt-3 text-[0.8rem] font-semibold text-slate-700">Сповіщень поки немає</p></div> : null}
        {!loading && !error ? feed.items.map((item) => <div key={item.id} className={`flex items-start border-b border-slate-100 last:border-b-0 hover:bg-slate-50 ${item.read ? "" : "bg-sky-50/50"}`}><a href={`#${item.target}`} onClick={() => { if (!item.read) void userNotificationApi.markRead(item.id).catch(() => {}); setOpen(false); }} className="block min-w-0 flex-1 py-3 pl-4 pr-1">
          <span className="flex items-start justify-between gap-3"><span className="text-[0.76rem] font-bold text-slate-900">{item.title}</span><time dateTime={item.createdAt} className="shrink-0 text-[0.65rem] text-slate-400">{relativeTime(item.createdAt)}</time></span>
          <span className="mt-1 block text-[0.74rem] leading-5 text-slate-600">{item.body}</span>
        </a><button type="button" onClick={() => void dismiss(item.id)} disabled={dismissing.includes(item.id)} aria-label={`Прибрати сповіщення: ${item.title}`} title="Прибрати сповіщення" className="mr-2 mt-2 flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-40"><X className="h-4 w-4" aria-hidden="true" /></button></div>) : null}
      </div>
      <a href={`#${role}/profile?notifications=1`} onClick={() => setOpen(false)} className="flex h-11 items-center justify-center gap-2 border-t border-slate-200 text-[0.76rem] font-bold text-slate-700 hover:bg-slate-50"><Settings2 className="h-4 w-4" />Налаштувати</a>
    </section> : null}
  </div>;
}
