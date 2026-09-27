import { useEffect, useMemo, useState } from "react";
import { BookOpen, ChevronDown, GraduationCap, Loader2, LogOut, Mail, Phone, RotateCcw, School, Search, Send, Users } from "lucide-react";
import { AppFooter } from "@/components/AppFooter.jsx";
import { LearnerNavigation } from "@/components/LearnerNavigation.jsx";
import { NotificationButton } from "@/components/NotificationButton.jsx";
import { SearchableSelect } from "@/components/SearchableSelect.jsx";
import { Alert } from "@/components/ui/alert.jsx";
import { CurrentUserBadge } from "@/lib/currentUser.jsx";
import { ApiError, authApi, learnerTeachersApi, parentApi } from "@/lib/api.js";
import { formatPhoneForDisplay } from "@/pages/Profile/profileModel.js";
import { cn } from "@/lib/utils.js";

export function UserTeachersPage({ role }) {
  const root = role.toLowerCase();
  const [children, setChildren] = useState([]);
  const [studentId, setStudentId] = useState("");
  const [loadingChildren, setLoadingChildren] = useState(role === "PARENT");
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(role === "STUDENT");
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const [subjectId, setSubjectId] = useState("");
  const [query, setQuery] = useState("");

  useEffect(() => {
    if (role !== "PARENT") return;
    let active = true;
    setLoadingChildren(true);
    setError("");
    parentApi.findChildren().then(items => { if (active) { setChildren(items); setStudentId(current => items.some(item => String(item.studentId) === current) ? current : String(items[0]?.studentId || "")); } })
      .catch(err => { if (active) setError(err instanceof ApiError ? err.message : "Не вдалося завантажити дітей."); })
      .finally(() => { if (active) setLoadingChildren(false); });
    return () => { active = false; };
  }, [role, reload]);

  useEffect(() => {
    if (role === "PARENT" && !studentId) return;
    let active = true;
    setLoading(true);
    setData(null);
    setError("");
    setSubjectId("");
    setQuery("");
    (role === "PARENT" ? learnerTeachersApi.parent(studentId) : learnerTeachersApi.student())
      .then(result => { if (active) setData(result); })
      .catch(err => { if (active) setError(err instanceof ApiError ? err.message : "Не вдалося завантажити вчителів."); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [role, studentId, reload]);

  const subjects = useMemo(() => [...new Map([...(data?.homeroomTeacher ? [data.homeroomTeacher] : []), ...(data?.teachers || [])].flatMap(teacher => teacher.subjects).map(subject => [String(subject.id), subject])).values()].sort((a, b) => a.name.localeCompare(b.name, "uk")), [data]);
  const visibleTeachers = useMemo(() => {
    const terms = query.trim().toLocaleLowerCase("uk-UA").split(/\s+/).filter(Boolean);
    return (data?.teachers || []).filter(teacher => (!subjectId || teacher.subjects.some(subject => String(subject.id) === subjectId)) && terms.every(term => teacher.fullName.toLocaleLowerCase("uk-UA").includes(term)));
  }, [data, query, subjectId]);
  const reset = () => { setSubjectId(""); setQuery(""); };

  return <div className="flex min-h-screen flex-col bg-slate-50 text-slate-800">
    <TopBar root={root} /><LearnerNavigation role={role} activeRoute={`${root}/teachers`} compact />
    <div className="flex flex-1"><LearnerNavigation role={role} activeRoute={`${root}/teachers`} /><div className="flex min-w-0 flex-1 flex-col lg:border-l lg:border-slate-200/80">
      <main className="min-w-0 flex-1 px-4 py-5 pb-8 sm:px-6 lg:px-8 lg:py-6"><div className="mx-auto w-full max-w-[1280px] 2xl:max-w-[1500px]">
        <header className="mb-5 sm:mb-6"><h1 className="text-[1.45rem] font-bold leading-tight text-slate-900 sm:text-[1.65rem]">Вчителі</h1><p className="mt-2 text-[0.9rem] text-slate-500">Класний керівник та вчителі {role === "PARENT" ? "вашої дитини" : "вашого класу"}</p></header>
        <section aria-label="Фільтри вчителів" className="mb-5 max-w-[1040px] rounded-lg border border-slate-200 bg-white p-4 shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)] sm:p-5">
          <div className={cn("grid items-end gap-3", role === "PARENT" ? "sm:grid-cols-2 lg:grid-cols-[minmax(140px,1fr)_minmax(160px,1fr)_minmax(180px,1.3fr)_40px]" : "sm:grid-cols-[minmax(160px,1fr)_minmax(180px,1.5fr)_40px]")}>
            {role === "PARENT" ? <div className="min-w-0"><label htmlFor="teachers-child" className="mb-2 block text-[0.75rem] font-semibold text-slate-600">Дитина</label><div className="relative"><select id="teachers-child" disabled={loadingChildren || !children.length} value={studentId} onChange={event => setStudentId(event.target.value)} className="h-10 w-full appearance-none rounded-md border border-slate-300 bg-white px-3 pr-9 text-[0.82rem] text-slate-700 outline-hidden focus:ring-2 focus:ring-slate-200 disabled:opacity-60">{children.length ? children.map(child => <option key={child.studentId} value={child.studentId}>{child.lastName} {child.firstName}</option>) : <option value="">Немає прив’язаних дітей</option>}</select><ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" /></div></div> : null}
            <SearchableSelect label="Предмет" options={subjects} value={subjectId} onChange={setSubjectId} placeholder="Пошук предмета" />
            <div><label htmlFor="teachers-search" className="mb-2 block text-[0.75rem] font-semibold text-slate-600">ПІБ вчителя</label><div className="relative"><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" /><input id="teachers-search" type="search" autoComplete="off" value={query} onChange={event => setQuery(event.target.value)} placeholder="Пошук за ПІБ" className="h-10 w-full rounded-md border border-slate-300 bg-white pl-9 pr-3 text-[0.82rem] text-slate-700 outline-hidden focus:ring-2 focus:ring-slate-200" /></div></div>
            <button type="button" onClick={reset} disabled={!subjectId && !query} title="Скинути фільтри" aria-label="Скинути фільтри" className="flex h-10 w-10 items-center justify-center rounded-md border border-slate-200 text-slate-600 hover:bg-slate-50 disabled:opacity-40"><RotateCcw className="h-4 w-4" /></button>
          </div>
        </section>
        {error ? <><Alert>{error}</Alert><button type="button" onClick={() => setReload(value => value + 1)} className="mt-3 inline-flex h-9 items-center gap-2 rounded-md border border-slate-300 bg-white px-3 text-sm text-slate-700"><RotateCcw className="h-4 w-4" />Повторити</button></> : loading || loadingChildren ? <div className="flex min-h-48 items-center justify-center gap-2 text-sm text-slate-500"><Loader2 className="h-5 w-5 animate-spin" />Завантаження вчителів...</div> : data?.className ? <>
          {data.homeroomTeacher ? <TeacherCard teacher={data.homeroomTeacher} root={root} className={data.className} homeroom /> : <div className="mb-5 rounded-lg border border-slate-200 bg-white p-6"><h2 className="text-base font-bold text-slate-900">Класний керівник</h2><p className="mt-2 text-sm text-slate-500">Не призначено</p></div>}
          <div className="mb-3 mt-5 flex items-center justify-between gap-3"><h2 className="text-[0.98rem] font-bold text-slate-900">Вчителі предметів</h2><span role="status" className="text-[0.72rem] text-slate-500">Показано {visibleTeachers.length} із {data.teachers.length}</span></div>
          {visibleTeachers.length ? <div className="grid gap-4 md:grid-cols-2 2xl:grid-cols-3">{visibleTeachers.map(teacher => <TeacherCard key={teacher.id} teacher={teacher} root={root} className={data.className} />)}</div> : <div className="flex min-h-40 flex-col items-center justify-center rounded-lg border border-slate-200 bg-white p-5 text-center"><Users className="h-7 w-7 text-slate-400" /><p className="mt-3 text-sm text-slate-500">{subjectId || query ? "За цими фільтрами вчителів не знайдено" : "Інших учителів предметів поки немає"}</p></div>}
        </> : <div className="rounded-lg border border-slate-200 bg-white p-8 text-center text-sm text-slate-500">{role === "PARENT" && !children.length ? "Додайте дитину на головній сторінці, щоб переглянути її вчителів." : "Учня ще не зараховано до класу."}</div>}
      </div></main><AppFooter />
    </div></div>
  </div>;
}

function TeacherCard({ teacher, root, className, homeroom = false }) {
  const initials = teacher.fullName.split(/\s+/).slice(0, 2).map(part => part[0]).join("");
  const params = new URLSearchParams({ recipient: teacher.fullName, recipientId: String(teacher.userId) });
  return <article className={cn("flex min-w-0 flex-col rounded-lg border border-slate-200 bg-white shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)]", homeroom ? "p-5 sm:p-6" : "p-5")}>
    {homeroom ? <h2 className="mb-5 flex items-center gap-2 text-[0.98rem] font-bold text-slate-900"><School className="h-5 w-5 text-slate-500" />Класний керівник <span className="ml-auto text-[0.78rem] font-medium text-slate-500">{className} клас</span></h2> : null}
    <div className={cn(homeroom && "grid gap-5 lg:grid-cols-[minmax(240px,1fr)_minmax(220px,1fr)_auto] lg:items-start")}>
      <div className="min-w-0">
        <div className="flex min-w-0 items-start gap-3"><div className={cn("flex shrink-0 items-center justify-center rounded-full bg-slate-100 font-bold text-slate-600", homeroom ? "h-16 w-16 text-lg" : "h-12 w-12 text-sm")}>{initials}</div><div className="min-w-0"><h3 className={cn("break-words font-bold leading-6 text-slate-900", homeroom ? "text-base" : "text-[0.92rem]")}>{teacher.fullName}</h3><p className="mt-1 text-[0.75rem] text-slate-500">{homeroom ? "Класний керівник" : `${className} клас`}</p></div></div>
      </div>
      <div className={cn("min-w-0", homeroom ? "border-t border-slate-200 pt-4 lg:border-t-0 lg:border-l lg:pl-5 lg:pt-0" : "mt-4 border-t border-slate-200 pt-4")}>
        <p className="flex items-center gap-2 text-[0.75rem] font-semibold text-slate-500"><BookOpen className="h-4 w-4" />Викладає предмети</p><div className="mt-2 flex flex-wrap gap-1.5">{teacher.subjects.length ? teacher.subjects.map(subject => <span key={subject.id} className="break-words rounded-md border border-slate-200 bg-slate-50 px-2 py-1 text-[0.72rem] text-slate-700">{subject.name}</span>) : <span className="text-[0.78rem] text-slate-400">Не викладає предметів у цьому класі</span>}</div>
        <div className="mt-4 space-y-2 border-t border-slate-200 pt-4 text-[0.78rem]">
          {homeroom ? teacher.phone ? <a href={`tel:${teacher.phone.replace(/[^+\d]/g, "")}`} className="flex items-center gap-2 text-slate-500 hover:text-slate-900"><Phone className="h-3.5 w-3.5 shrink-0" aria-hidden="true" /><span>{formatPhoneForDisplay(teacher.phone)}</span></a> : <p className="text-slate-400">Телефон не вказано</p> : null}
          {teacher.email ? <a href={`mailto:${teacher.email}`} className="flex items-start gap-2 text-slate-500 hover:text-slate-900"><Mail className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" /><span className="min-w-0 break-all">{teacher.email}</span></a> : <p className="text-slate-400">Електронну пошту не вказано</p>}
        </div>
      </div>
      <a href={`#${root}/messages?${params}`} className={cn("inline-flex min-h-10 items-center justify-center gap-2 rounded-md bg-slate-800 px-4 py-2 text-[0.8rem] font-semibold text-white hover:bg-slate-700", homeroom ? "mt-1" : "mt-5 w-full")}><Send className="h-4 w-4 shrink-0" />Написати повідомлення</a>
    </div>
  </article>;
}

function TopBar({ root }) {
  const logout = async () => { try { await authApi.logout(); } catch { /* The session may already be expired. */ } window.location.hash = "login"; };
  return <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-slate-200/80 bg-white px-4 sm:px-5 lg:px-6"><a href={`#${root}`} className="inline-flex min-w-0 items-center gap-2 text-slate-900" aria-label="Електронний щоденник - головна"><GraduationCap className="h-7 w-7 shrink-0 sm:h-8 sm:w-8" strokeWidth={2.4} /><span className="max-w-[150px] truncate text-[0.92rem] font-bold min-[420px]:max-w-none sm:text-[1.05rem]">Електронний щоденник</span></a><div className="flex items-center gap-1.5 sm:gap-4"><NotificationButton /><CurrentUserBadge /><button type="button" onClick={logout} className="inline-flex h-10 items-center gap-2 rounded-md px-3 text-[0.82rem] font-semibold text-slate-500 hover:bg-slate-100"><LogOut className="h-4 w-4" /><span className="hidden sm:inline">Вийти</span></button></div></header>;
}
