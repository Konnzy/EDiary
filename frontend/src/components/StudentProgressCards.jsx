import { WorkTypeBadge } from "@/components/WorkTypeBadge.jsx";
import { Loader2 } from "lucide-react";
import { Card, CardContent, CardHeader } from "@/components/ui/card.jsx";
import { cn } from "@/lib/utils.js";

function SectionTitle({ children }) {
  return <h2 className="text-[0.98rem] font-bold leading-tight text-slate-900">{children}</h2>;
}


function DataState({ loading, error, empty, emptyText, onRetry }) {
  if (loading) return <div className="flex min-h-24 items-center justify-center gap-2 text-[0.8rem] text-slate-500" role="status"><Loader2 className="h-5 w-5 animate-spin" aria-hidden="true" />Завантаження…</div>;
  if (error) return <div className="py-4 text-[0.8rem]" role="alert"><p className="text-red-600">{error}</p><button type="button" onClick={onRetry} className="mt-3 cursor-pointer font-semibold text-slate-700 hover:underline">Спробувати ще раз</button></div>;
  if (empty) return <p className="py-8 text-center text-[0.8rem] text-slate-500">{emptyText}</p>;
  return null;
}

export function RecentGradesCard({ data }) {
  const { grades, loading, error, reload } = data;
  return (
    <Card className="rounded-lg px-4 py-4 shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)] sm:px-5 sm:py-5">
      <CardHeader className="mb-4 space-y-0">
        <div className="flex items-center justify-between gap-3">
          <SectionTitle>Останні оцінки</SectionTitle>
          {grades.length ? <span className="rounded-full bg-slate-100 px-2.5 py-1 text-[0.68rem] font-semibold text-slate-600">{grades.length} {gradeCountLabel(grades.length)}</span> : null}
        </div>
      </CardHeader>

      <CardContent>
        <DataState loading={loading} error={error} empty={!grades.length} emptyText="Оцінок ще немає." onRetry={reload} />
        {!loading && !error && grades.length > 0 ? <>
        <div className="hidden overflow-x-auto rounded-lg border border-slate-200/80 sm:block">
          <table className="min-w-[600px] w-full border-collapse text-left">
            <thead>
              <tr className="bg-slate-50 text-[0.68rem] font-semibold text-slate-500">
                <th className="border-r border-slate-200/80 px-4 py-3">Дата</th>
                <th className="border-r border-slate-200/80 px-4 py-3">Предмет</th>
                <th className="border-r border-slate-200/80 px-4 py-3">Тип роботи</th>
                <th className="border-r border-slate-200/80 px-4 py-3 text-center">Оцінка</th>
                <th className="px-4 py-3">Коментар</th>
              </tr>
            </thead>
            <tbody>
              {grades.map((item) => (
                <tr key={item.id} className="border-t border-slate-100 text-[0.78rem] text-slate-600 transition-colors odd:bg-white even:bg-slate-50/40 hover:bg-slate-50 dark:odd:bg-[#242c35] dark:even:bg-[#1c242d] dark:hover:bg-[#303b46]">
                  <td className="whitespace-nowrap border-r border-slate-100 px-4 py-3 font-medium text-slate-600">{item.date}</td>
                  <td className="border-r border-slate-100 px-4 py-3 font-semibold text-slate-800">{item.subject}</td>
                  <td className="border-r border-slate-100 px-4 py-3"><WorkTypeBadge type={item.type} /></td>
                  <td className="border-r border-slate-100 px-4 py-2.5 text-center"><GradeBadge value={item.grade} /></td>
                  <td className="max-w-40 truncate px-4 py-3 text-slate-500" title={item.comment}>{item.comment}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="space-y-2 sm:hidden">
          {grades.map((item) => (
            <article
              key={item.id}
              className="flex items-center gap-3 rounded-lg border border-slate-200/80 bg-white px-3 py-2.5"
            >
              <div className="min-w-0 flex-1">
                <div className="flex items-baseline justify-between gap-2">
                  <h3 className="truncate text-[0.84rem] font-bold text-slate-800">{item.subject}</h3>
                  <time className="shrink-0 text-[0.68rem] font-medium text-slate-400">{item.date}</time>
                </div>
                <div className="mt-1"><WorkTypeBadge type={item.type} /></div>
                {item.comment !== "—" ? <p className="mt-1 truncate text-[0.7rem] text-slate-500" title={item.comment}>{item.comment}</p> : null}
              </div>
              <GradeBadge value={item.grade} />
            </article>
          ))}
        </div>
        </> : null}
      </CardContent>
    </Card>
  );
}

function GradeBadge({ value }) {
  const color = typeof value !== "number" ? "bg-slate-100 text-slate-700 ring-slate-200" : value >= 10
    ? "bg-emerald-50 text-emerald-700 ring-emerald-200"
    : value >= 7
      ? "bg-sky-50 text-sky-700 ring-sky-200"
      : "bg-amber-50 text-amber-700 ring-amber-200";
  return <span className={cn("inline-flex min-w-9 items-center justify-center rounded-md px-2.5 py-1.5 text-[0.84rem] font-bold tabular-nums ring-1 ring-inset", color)}>{value}</span>;
}

function gradeCountLabel(count) {
  const mod100 = count % 100;
  if (mod100 >= 11 && mod100 <= 14) return "оцінок";
  if (count % 10 === 1) return "оцінка";
  if (count % 10 >= 2 && count % 10 <= 4) return "оцінки";
  return "оцінок";
}

export function HomeworkCard({ data }) {
  const { homework, loading, error, reload } = data;
  return (
    <Card className="rounded-lg px-4 py-4 shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)] sm:px-5 sm:py-5">
      <CardHeader className="mb-4 space-y-0">
        <SectionTitle>Домашні завдання</SectionTitle>
      </CardHeader>

      <CardContent className="space-y-3">
        <DataState loading={loading} error={error} empty={!homework.length} emptyText="Домашніх завдань ще немає." onRetry={reload} />
        {!loading && !error ? <>
        {homework.map((item) => (
          <article
            key={item.id}
            className={cn(
              "rounded-lg border px-3.5 py-3 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-sm",
              item.overdue
                ? "border-red-200 bg-red-50/30 hover:border-red-300 hover:bg-red-50/50"
                : "border-slate-200/80 bg-white hover:border-slate-300 hover:bg-slate-50/50",
            )}
          >
            <h3 className="text-[0.84rem] font-bold leading-5 text-slate-800">{item.subject}</h3>
            <p className={cn("mt-1 break-words text-[0.8rem] leading-5", item.completed ? "text-slate-500" : "text-slate-700")}>{item.title}</p>
            <div className="mt-2.5 flex flex-wrap items-center gap-2 text-[0.7rem] font-medium text-slate-500">
              <span className="rounded bg-slate-100 px-2 py-1 tabular-nums">{item.dueDate}</span>
              <span aria-hidden="true" className="text-slate-300">·</span>
              <span className={cn("rounded px-2 py-1 font-semibold", item.statusClass)}>{item.status}</span>
            </div>
          </article>
        ))}
        </> : null}
      </CardContent>
    </Card>
  );
}

