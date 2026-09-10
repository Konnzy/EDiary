import {
  CalendarDays,
  CheckCircle2,
  Clock3,
  GraduationCap,
  Mail,
  ShieldCheck,
  UserCheck,
  UserRound,
  Users,
  XCircle,
} from "lucide-react";
import { Card } from "@/components/ui/card.jsx";
import { cn } from "@/lib/utils.js";
import { AdminLayout } from "@/pages/AdminHome/AdminLayout.jsx";

const stats = [
  { label: "Учні", value: "428", detail: "+12 за місяць", icon: GraduationCap },
  { label: "Вчителі", value: "38", detail: "36 активні", icon: UserRound },
  { label: "Батьки", value: "611", detail: "594 активні", icon: Users },
  { label: "Активні користувачі", value: "1 047", detail: "97% доступів активні", icon: ShieldCheck },
  { label: "Заявки на розгляді", value: "7", detail: "3 потребують дії", icon: Mail },
];

const registrationRequests = [
  { name: "Олена Мельник", role: "Батьки", details: "5-А клас", contact: "olena.melnyk@example.com", status: "Схвалено", createdAt: "24.05.2026" },
  { name: "Андрій Литвин", role: "Учень", details: "9-Б клас", contact: "andrii.lytvyn@example.com", status: "Перевірка", createdAt: "23.05.2026" },
  { name: "Марина Савчук", role: "Вчитель", details: "Біологія", contact: "maryna.savchuk@example.com", status: "Відхилено", createdAt: "22.05.2026" },
];

const todayLessons = [
  { time: "08:30", subject: "Алгебра", className: "10-А", teacher: "О. Коваленко", room: "302" },
  { time: "09:25", subject: "Геометрія", className: "9-Б", teacher: "О. Коваленко", room: "302" },
  { time: "10:20", subject: "Українська мова", className: "5-А", teacher: "О. Петренко", room: "210" },
  { time: "11:15", subject: "Фізика", className: "11-Б", teacher: "В. Сидоренко", room: "Лаб." },
];

const activityItems = [
  { title: "Підтверджено обліковий запис учня", meta: "Іван Бондаренко, 7-Б", time: "10:45", icon: CheckCircle2 },
  { title: "Оновлено розклад для 9-Б", meta: "заміна кабінету на 302", time: "09:30", icon: CalendarDays },
  { title: "Нова заявка від батьків", meta: "Олена Мельник, 5-А", time: "08:55", icon: UserCheck },
  { title: "Деактивовано прострочений доступ", meta: "службовий обліковий запис", time: "Вчора", icon: XCircle },
];

export function AdminHomePage() {
  return (
    <AdminLayout
      activeItem="Головна"
      title="Панель адміністратора"
      subtitle="Огляд користувачів, заявок, занять і системної активності за сьогодні."
    >
      <StatsGrid />
      <div className="mt-6 grid gap-5 xl:grid-cols-[minmax(0,1.35fr)_minmax(360px,0.85fr)]">
        <RegistrationRequestsCard />
        <TodayLessonsCard />
      </div>
      <div className="mt-6"><SystemActivityCard /></div>
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
      {actionLabel ? <a href={actionHref} className="shrink-0 text-[0.76rem] font-bold text-[#17233a] transition-colors hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2">{actionLabel}</a> : null}
    </div>
  );
}

function StatsGrid() {
  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
      {stats.map((item) => {
        const Icon = item.icon;
        return (
          <Card key={item.label} className="group rounded-lg px-4 py-4 transition-all duration-200 hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-[0_18px_44px_-30px_rgba(15,23,42,0.7)]">
            <div className="flex min-h-[118px] flex-col justify-between gap-5">
              <div className="flex items-start justify-between gap-3"><p className="text-[0.8rem] font-semibold leading-snug text-slate-500">{item.label}</p><span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-slate-100 text-slate-500 transition-colors group-hover:bg-[#17233a] group-hover:text-white"><Icon className="h-5 w-5" aria-hidden="true" /></span></div>
              <div><div className="text-[1.8rem] font-bold leading-none text-slate-950">{item.value}</div><p className="mt-2 text-[0.75rem] font-semibold text-slate-400">{item.detail}</p></div>
            </div>
          </Card>
        );
      })}
    </div>
  );
}

function RegistrationRequestsCard() {
  return (
    <Card className="rounded-lg px-4 py-4 sm:px-5 sm:py-5">
      <CardHeading title="Заявки на реєстрацію" subtitle="Останні заявки, що потребують перевірки." actionLabel="Усі заявки" actionHref="#admin/requests" />
      <div className="hidden overflow-x-auto md:block">
        <table className="w-full min-w-[680px] border-collapse text-left">
          <thead><tr className="border-b border-slate-100 text-[0.72rem] font-bold uppercase text-slate-400"><th className="pb-3 pr-4">Користувач</th><th className="pb-3 pr-4">Роль</th><th className="pb-3 pr-4">Дані</th><th className="pb-3 pr-4">Статус</th><th className="pb-3 text-right">Дата</th></tr></thead>
          <tbody>{registrationRequests.map((request) => <RequestRow key={request.contact} request={request} />)}</tbody>
        </table>
      </div>
      <div className="space-y-3 md:hidden">
        {registrationRequests.map((request) => (
          <article key={`${request.contact}-mobile`} className="rounded-lg border border-slate-200 bg-slate-50/60 px-3 py-3">
            <div className="flex items-start justify-between gap-3"><div className="min-w-0"><h3 className="truncate text-[0.88rem] font-bold text-slate-900">{request.name}</h3><p className="mt-1 text-[0.75rem] text-slate-500">{request.role} · {request.details}</p></div><StatusBadge status={request.status} /></div>
            <p className="mt-2 truncate text-[0.72rem] text-slate-400">{request.contact}</p>
          </article>
        ))}
      </div>
    </Card>
  );
}

function RequestRow({ request }) {
  return (
    <tr className="border-b border-slate-100 text-[0.82rem] text-slate-600 last:border-0 hover:bg-slate-50/80">
      <td className="py-3.5 pr-4"><div className="font-bold text-slate-900">{request.name}</div><div className="mt-0.5 text-[0.73rem] text-slate-400">{request.contact}</div></td>
      <td className="py-3.5 pr-4 font-semibold">{request.role}</td><td className="py-3.5 pr-4">{request.details}</td><td className="py-3.5 pr-4"><StatusBadge status={request.status} /></td><td className="py-3.5 text-right font-semibold text-slate-400">{request.createdAt}</td>
    </tr>
  );
}

function StatusBadge({ status }) {
  const classes = { Схвалено: "bg-emerald-50 text-emerald-700 ring-emerald-200", Відхилено: "bg-rose-50 text-rose-700 ring-rose-200", Перевірка: "bg-amber-50 text-amber-700 ring-amber-200" };
  return <span className={cn("inline-flex rounded-md px-2 py-1 text-[0.68rem] font-bold ring-1", classes[status])}>{status}</span>;
}

function TodayLessonsCard() {
  return (
    <Card className="rounded-lg px-4 py-4 sm:px-5 sm:py-5">
      <CardHeading title="Сьогоднішні заняття" subtitle="Найближчі уроки в розкладі." />
      <div className="space-y-3">
        {todayLessons.map((lesson) => (
          <article key={`${lesson.time}-${lesson.className}`} className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white px-3 py-3 transition-colors hover:bg-slate-50">
            <div className="flex h-11 w-14 shrink-0 flex-col items-center justify-center rounded-md bg-slate-100 text-slate-500"><Clock3 className="h-3.5 w-3.5" aria-hidden="true" /><time className="mt-0.5 text-[0.68rem] font-bold">{lesson.time}</time></div>
            <div className="min-w-0 flex-1"><h3 className="truncate text-[0.86rem] font-bold text-slate-900">{lesson.subject}</h3><p className="mt-0.5 truncate text-[0.73rem] font-semibold text-slate-400">{lesson.className} · {lesson.teacher}</p></div>
            <span className="shrink-0 rounded bg-slate-50 px-2 py-1 text-[0.7rem] font-bold text-slate-500">{lesson.room}</span>
          </article>
        ))}
      </div>
    </Card>
  );
}

function SystemActivityCard() {
  return (
    <Card className="rounded-lg px-4 py-4 sm:px-5 sm:py-5">
      <CardHeading title="Остання активність системи" subtitle="Події, які допомагають швидко оцінити стан школи." />
      <div className="grid gap-3 lg:grid-cols-4">
        {activityItems.map((item) => {
          const Icon = item.icon;
          return (
            <article key={`${item.title}-${item.time}`} className="rounded-lg border border-slate-200 bg-white px-4 py-3 transition-all duration-200 hover:-translate-y-0.5 hover:bg-slate-50 hover:shadow-[0_14px_34px_-28px_rgba(15,23,42,0.7)]">
              <div className="mb-3 flex items-center justify-between gap-3"><span className="flex h-9 w-9 items-center justify-center rounded-md bg-slate-100 text-[#17233a]"><Icon className="h-4 w-4" aria-hidden="true" /></span><span className="text-[0.72rem] font-bold text-slate-400">{item.time}</span></div>
              <h3 className="text-[0.86rem] font-bold leading-snug text-slate-900">{item.title}</h3><p className="mt-1 text-[0.74rem] leading-5 text-slate-500">{item.meta}</p>
            </article>
          );
        })}
      </div>
    </Card>
  );
}
