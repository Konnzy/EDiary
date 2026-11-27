import { Link } from "react-router-dom";
import { useCallback, useEffect, useState } from "react";
import { Mail, RefreshCw } from "lucide-react";
import { messagesApi } from "@/lib/api.js";
import { Card, CardHeader, CardContent } from "@/components/ui/card.jsx";

export function RecentMessagesCard({ role }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const load = useCallback(() => messagesApi.list().then(value => { setData(value); setError(""); }).catch(() => setError("Не вдалося завантажити повідомлення.")), []);
  useEffect(() => {
    void load();
    const timer = setInterval(load, 60000);
    window.addEventListener("messages-updated", load);
    return () => { clearInterval(timer); window.removeEventListener("messages-updated", load); };
  }, [load]);
  const route = `${role.toLowerCase()}/messages`;
  return <Card className="rounded-lg px-4 py-4 shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)] sm:px-5 sm:py-5">
    <CardHeader className="mb-4 flex-row items-center justify-between space-y-0"><h2 className="text-[0.98rem] font-bold text-slate-900">Останні повідомлення</h2>{data?.unreadCount > 0 ? <span className="text-xs font-semibold text-slate-500">Непрочитаних: {data.unreadCount}</span> : null}</CardHeader>
    <CardContent className="flex min-h-28 flex-col justify-between gap-4">
      {error ? <div className="flex items-center gap-2 text-xs text-red-600">{error}<button type="button" onClick={load} title="Повторити" aria-label="Повторити завантаження повідомлень"><RefreshCw className="h-4 w-4" /></button></div>
        : !data ? <p className="text-sm text-slate-500">Завантаження…</p>
          : data.messages.content.length ? <ul className="divide-y divide-slate-100">{data.messages.content.slice(0,3).map(item => <li key={item.id}>
            <Link className="block min-w-0 py-2 hover:bg-slate-50" to={`/${route}?messageId=${item.id}`}><p className="truncate text-[0.8rem] font-semibold text-slate-800">{!item.read ? "• " : ""}{item.subject}</p><p className="mt-1 truncate text-xs text-slate-500">{item.sender.fullName}</p></Link>
          </li>)}</ul> : <p className="text-[0.8rem] text-slate-500">Повідомлень поки немає.</p>}
      <Link to={`/${route}`} className="inline-flex w-fit items-center gap-2 text-[0.8rem] font-semibold text-slate-800 hover:underline"><Mail className="h-4 w-4" aria-hidden="true" />Перейти до повідомлень</Link>
    </CardContent>
  </Card>;
}
