import { Link } from "react-router-dom";
import { useEffect, useState } from "react";
import {
  Clock3,
  GraduationCap,
  History,
  Loader2,
  Mail,
  ShieldCheck,
  UserRound,
  Users,
} from "lucide-react";
import { Alert } from "@/components/ui/alert.jsx";
import { Card } from "@/components/ui/card.jsx";
import { ApiError, adminDashboardApi } from "@/lib/api.js";
import { cn } from "@/lib/utils.js";
import { AdminLayout } from "@/pages/AdminHome/AdminLayout.jsx";

const roleLabels = {
  STUDENT: "Учень",
  TEACHER: "Вчитель",
  PARENT: "Батьки",
  ADMIN: "Адміністратор",
};

const statusLabels = {
  PENDING: "Перевірка",
  APPROVED: "Схвалено",
  REJECTED: "Відхилено",
};

export function AdminHomePage() {
  const [dashboard, setDashboard] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    adminDashboardApi.find()
      .then((data) => {
        if (active) setDashboard(data);
      })
      .catch((requestError) => {
        if (!active) return;
        setError(requestError instanceof ApiError
          ? requestError.message
          : "Не вдалося завантажити дані панелі адміністратора.");
      })
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, []);

  return (
    <AdminLayout
      activeItem="Головна"
      title="Панель адміністратора"
      subtitle="Огляд користувачів, заявок і занять за актуальними даними системи."
    >
      {error ? <Alert>{error}</Alert> : null}
      {loading ? <LoadingState /> : null}
      {!loading && dashboard ? (
        <>
          <StatsGrid stats={dashboard.stats} />
          <div className="mt-6 grid gap-5 xl:grid-cols-[minmax(0,1.35fr)_minmax(360px,0.85fr)]">
            <RegistrationRequestsCard requests={dashboard.recentRequests} />
            <TodayLessonsCard
              lessons={dashboard.todayLessons}
              academicYear={dashboard.currentAcademicYear}
              week={dashboard.currentWeek}
            />
          </div>
          <div className="mt-6"><AdminActivityCard items={dashboard.recentAdminActions || []} /></div>
        </>
      ) : null}
    </AdminLayout>
  );
}

function CardHeading({ title, subtitle, actionLabel, actionHref }) {
  return (
    <div className="mb-4 flex items-start justify-between gap-4">
      <div className="min-w-0">
        <h2 className="text-[1rem] font-bold leading-tight text-slate-950">{title}</h2>
        {subtitle ? <p className="mt-1 text-[0.78rem] leading-5 text-slate-500">{subtitle}</p> : null}
      </div>
      {actionLabel ? <Link to={actionHref} className="shrink-0 text-[0.76rem] font-bold text-[#17233a] hover:text-slate-700">{actionLabel}</Link> : null}
    </div>
  );
}

function StatsGrid({ stats }) {
  const items = [
    { label: "Учні", value: stats.students, detail: `${formatNumber(stats.activeStudents)} активні`, icon: GraduationCap },
    { label: "Вчителі", value: stats.teachers, detail: `${formatNumber(stats.activeTeachers)} активні`, icon: UserRound },
    { label: "Батьки", value: stats.parents, detail: `${formatNumber(stats.activeParents)} активні`, icon: Users },
    { label: "Активні користувачі", value: stats.activeUsers, detail: `із ${formatNumber(stats.totalUsers)} облікових записів`, icon: ShieldCheck },
    { label: "Заявки на розгляді", value: stats.pendingRequests, detail: "Очікують рішення", icon: Mail },
  ];
  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
      {items.map((item) => {
        const Icon = item.icon;
        return (
          <Card key={item.label} className="group rounded-lg px-4 py-4 transition-all duration-200 hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-[0_18px_44px_-30px_rgba(15,23,42,0.7)]">
            <div className="flex min-h-[118px] flex-col justify-between gap-5">
              <div className="flex items-start justify-between gap-3"><p className="text-[0.8rem] font-semibold leading-snug text-slate-500">{item.label}</p><span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-slate-100 text-slate-500 transition-colors group-hover:bg-[#17233a] group-hover:text-white"><Icon className="h-5 w-5" aria-hidden="true" /></span></div>
              <div><div className="text-[1.8rem] font-bold leading-none text-slate-950">{formatNumber(item.value)}</div><p className="mt-2 text-[0.75rem] font-semibold text-slate-400">{item.detail}</p></div>
            </div>
          </Card>
        );
      })}
    </div>
  );
}

function RegistrationRequestsCard({ requests }) {
  return (
    <Card className="rounded-lg px-4 py-4 sm:px-5 sm:py-5">
      <CardHeading title="Заявки на реєстрацію" subtitle="Останні заявки у системі." actionLabel="Усі заявки" actionHref="/admin/requests" />
      {requests.length ? (
        <>
          <div className="hidden overflow-x-auto md:block">
            <table className="w-full min-w-[680px] border-collapse text-left">
              <thead><tr className="border-b border-slate-100 text-[0.72rem] font-bold uppercase text-slate-400"><th className="pb-3 pr-4">Користувач</th><th className="pb-3 pr-4">Роль</th><th className="pb-3 pr-4">Дані</th><th className="pb-3 pr-4">Статус</th><th className="pb-3 text-right">Дата</th></tr></thead>
              <tbody>{requests.map((request) => <RequestRow key={request.id} request={request} />)}</tbody>
            </table>
          </div>
          <div className="space-y-3 md:hidden">
            {requests.map((request) => (
              <article key={`${request.id}-mobile`} className="rounded-lg border border-slate-200 bg-slate-50/60 px-3 py-3">
                <div className="flex items-start justify-between gap-3"><div className="min-w-0"><h3 className="truncate text-[0.88rem] font-bold text-slate-900">{request.fullName}</h3><p className="mt-1 text-[0.75rem] text-slate-500">{roleLabels[request.role]} · {request.details}</p></div><StatusBadge status={request.status} /></div>
                <p className="mt-2 truncate text-[0.72rem] text-slate-400">{request.email}</p>
              </article>
            ))}
          </div>
        </>
      ) : <EmptyMessage>Заявок на реєстрацію ще немає.</EmptyMessage>}
    </Card>
  );
}

function RequestRow({ request }) {
  return (
    <tr className="border-b border-slate-100 text-[0.82rem] text-slate-600 last:border-0 hover:bg-slate-50/80">
      <td className="py-3.5 pr-4"><div className="font-bold text-slate-900">{request.fullName}</div><div className="mt-0.5 text-[0.73rem] text-slate-400">{request.email}</div></td>
      <td className="py-3.5 pr-4 font-semibold">{roleLabels[request.role]}</td><td className="py-3.5 pr-4">{request.details}</td><td className="py-3.5 pr-4"><StatusBadge status={request.status} /></td><td className="py-3.5 text-right font-semibold text-slate-400">{formatDate(request.createdAt)}</td>
    </tr>
  );
}

function StatusBadge({ status }) {
  const classes = { APPROVED: "bg-emerald-50 text-emerald-700 ring-emerald-200", REJECTED: "bg-rose-50 text-rose-700 ring-rose-200", PENDING: "bg-amber-50 text-amber-700 ring-amber-200" };
  return <span className={cn("inline-flex rounded-md px-2 py-1 text-[0.68rem] font-bold ring-1", classes[status])}>{statusLabels[status]}</span>;
}

function TodayLessonsCard({ lessons, academicYear, week }) {
  return (
    <Card className="rounded-lg px-4 py-4 sm:px-5 sm:py-5">
      <CardHeading title="Сьогоднішні заняття" subtitle={`${academicYear} н. р. · ${week}-й тиждень`} />
      {lessons.length ? (
        <div className="space-y-3">
          {lessons.map((lesson) => (
            <article key={lesson.id} className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white px-3 py-3 transition-colors hover:bg-slate-50">
              <div className="flex h-11 w-14 shrink-0 flex-col items-center justify-center rounded-md bg-slate-100 text-slate-500"><Clock3 className="h-3.5 w-3.5" aria-hidden="true" /><time className="mt-0.5 text-[0.68rem] font-bold">{lesson.startTime}</time></div>
              <div className="min-w-0 flex-1"><h3 className="truncate text-[0.86rem] font-bold text-slate-900">{lesson.subject}</h3><p className="mt-0.5 truncate text-[0.73rem] font-semibold text-slate-400">{lesson.className} · {lesson.teacher}</p></div>
              <span className="shrink-0 rounded bg-slate-50 px-2 py-1 text-[0.7rem] font-bold text-slate-500">{lesson.room || "—"}</span>
            </article>
          ))}
        </div>
      ) : <EmptyMessage>На сьогодні занять немає.</EmptyMessage>}
    </Card>
  );
}

function AdminActivityCard({ items }) {
  return (
    <Card className="rounded-lg px-4 py-4 sm:px-5 sm:py-5">
      <CardHeading title="Останні дії адміністратора" subtitle="Зміни користувачів, класів, предметів, заявок і розкладу." />
      {items.length ? (
        <div className="grid gap-3 lg:grid-cols-4">
          {items.map((item) => {
            return (
              <article key={item.id} className="rounded-lg border border-slate-200 bg-white px-4 py-3 transition-all duration-200 hover:-translate-y-0.5 hover:bg-slate-50 hover:shadow-[0_14px_34px_-28px_rgba(15,23,42,0.7)]">
                <div className="mb-3 flex items-center justify-between gap-3"><span className="flex h-9 w-9 items-center justify-center rounded-md bg-slate-100 text-[#17233a]"><History className="h-4 w-4" aria-hidden="true" /></span><span className="text-[0.72rem] font-bold text-slate-400">{formatDateTime(item.occurredAt)}</span></div>
                <h3 className="text-[0.86rem] font-bold leading-snug text-slate-900">{item.title}</h3><p className="mt-1 text-[0.74rem] leading-5 text-slate-500">{item.target} · {item.actorLogin}</p>
              </article>
            );
          })}
        </div>
      ) : <EmptyMessage>Дій адміністратора ще немає.</EmptyMessage>}
    </Card>
  );
}

function LoadingState() {
  return <div className="flex min-h-72 items-center justify-center text-slate-400"><Loader2 className="h-7 w-7 animate-spin" aria-label="Завантаження даних" /></div>;
}

function EmptyMessage({ children }) {
  return <div className="flex min-h-28 items-center justify-center rounded-lg border border-dashed border-slate-200 px-4 text-center text-[0.78rem] text-slate-400">{children}</div>;
}

function formatNumber(value) {
  return new Intl.NumberFormat("uk-UA").format(value || 0);
}

function formatDate(value) {
  return new Intl.DateTimeFormat("uk-UA").format(new Date(value));
}

function formatDateTime(value) {
  return new Intl.DateTimeFormat("uk-UA", {
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}
