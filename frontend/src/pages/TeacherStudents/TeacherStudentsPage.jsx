import { navigate } from "@/lib/navigation.js";
import { Link } from "react-router-dom";
import { useEffect, useMemo, useState } from "react";
import { BookOpen, ChevronDown, GraduationCap, Loader2, LogOut, Mail, Phone, RotateCcw, Search, Send, Users } from "lucide-react";
import { AppFooter } from "@/components/AppFooter.jsx";
import { NotificationButton } from "@/components/NotificationButton.jsx";
import { Alert } from "@/components/ui/alert.jsx";
import { CurrentUserBadge } from "@/lib/currentUser.jsx";
import { ApiError, authApi, teacherStudentsApi } from "@/lib/api.js";
import { TeacherNavigation } from "@/components/TeacherNavigation.jsx";
import { cn } from "@/lib/utils.js";
import { formatPhoneForDisplay } from "@/pages/Profile/profileModel.js";

const fieldClasses = "h-10 w-full rounded-md border border-slate-300 bg-white text-[0.82rem] text-slate-700 outline-hidden hover:border-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200";

export function TeacherStudentsPage() {
  const [students, setStudents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const [classId, setClassId] = useState("");
  const [subjectId, setSubjectId] = useState("");
  const [query, setQuery] = useState("");

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError("");
    teacherStudentsApi.find().then((items) => {
      if (active) setStudents(items);
    }).catch((requestError) => {
      if (active) setError(requestError instanceof ApiError ? requestError.message : "Не вдалося завантажити учнів.");
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [reload]);

  const classes = useMemo(() => [...new Map(students.map((student) => [String(student.classId), { id: String(student.classId), name: student.className }])).values()]
    .sort((a, b) => a.name.localeCompare(b.name, "uk", { numeric: true })), [students]);
  const subjects = useMemo(() => [...new Map(students.filter((student) => !classId || String(student.classId) === classId)
    .flatMap((student) => student.subjects).map((subject) => [String(subject.id), subject])).values()]
    .sort((a, b) => a.name.localeCompare(b.name, "uk")), [students, classId]);
  const visibleStudents = useMemo(() => {
    const terms = query.trim().toLocaleLowerCase("uk-UA").split(/\s+/).filter(Boolean);
    return students.filter((student) => (!classId || String(student.classId) === classId)
      && (!subjectId || student.subjects.some((subject) => String(subject.id) === subjectId))
      && terms.every((term) => student.fullName.toLocaleLowerCase("uk-UA").includes(term)));
  }, [students, classId, subjectId, query]);
  const hasFilters = Boolean(classId || subjectId || query);
  const resetFilters = () => { setClassId(""); setSubjectId(""); setQuery(""); };

  return (
    <div className="flex min-h-screen flex-col bg-slate-50 text-slate-800">
      <TopBar />
      <TeacherNavigation activeRoute="teacher/students" compact />
      <div className="flex flex-1">
        <TeacherNavigation activeRoute="teacher/students" />
        <div className="flex min-w-0 flex-1 flex-col lg:border-l lg:border-slate-200/80">
          <main className="min-w-0 flex-1 px-4 py-5 pb-8 sm:px-6 lg:px-8 lg:py-6">
            <div className="mx-auto w-full max-w-[1280px] 2xl:max-w-[1500px] min-[1920px]:max-w-[1680px]">
              <header className="mb-5 sm:mb-6">
                <h1 className="text-[1.45rem] font-bold leading-tight text-slate-900 sm:text-[1.65rem]">Учні</h1>
                <p className="mt-2 text-[0.9rem] text-slate-500">Ваші учні, навчальні предмети та контакти батьків</p>
              </header>
              <section aria-label="Фільтри учнів" className="mb-5 max-w-[1040px] rounded-lg border border-slate-200 bg-white p-4 shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)] sm:p-5">
                <div className="grid items-end gap-3 sm:grid-cols-2 lg:grid-cols-[minmax(110px,0.7fr)_minmax(150px,1fr)_minmax(180px,1.5fr)_40px]">
                  <FilterSelect id="students-class" label="Клас" value={classId} onChange={(value) => { setClassId(value); setSubjectId(""); }} options={classes} emptyLabel="Усі класи" />
                  <FilterSelect id="students-subject" label="Предмет" value={subjectId} onChange={setSubjectId} options={subjects} emptyLabel="Усі предмети" />
                  <div className="min-w-0">
                    <label htmlFor="students-search" className="mb-2 block text-[0.75rem] font-semibold text-slate-600">ПІБ учня</label>
                    <div className="relative">
                      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                      <input id="students-search" type="search" autoComplete="off" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Пошук за ПІБ" className={cn(fieldClasses, "pl-9 pr-3")} />
                    </div>
                  </div>
                  <button type="button" onClick={resetFilters} disabled={!hasFilters} title="Скинути фільтри" aria-label="Скинути фільтри" className="flex h-10 w-10 items-center justify-center rounded-md border border-slate-200 text-slate-600 hover:bg-slate-50 disabled:opacity-40"><RotateCcw className="h-4 w-4" /></button>
                </div>
                <p className="mt-3 text-[0.72rem] text-slate-500" role="status">{loading ? "Завантаження учнів..." : `Показано ${visibleStudents.length} із ${students.length} учнів`}</p>
              </section>
              {error ? <><Alert>{error}</Alert><button type="button" onClick={() => setReload((value) => value + 1)} className="mt-3 inline-flex h-9 items-center gap-2 rounded-md border border-slate-300 bg-white px-3 text-sm text-slate-700"><RotateCcw className="h-4 w-4" />Повторити</button></> : loading ? (
                <div className="flex min-h-48 items-center justify-center gap-2 text-sm text-slate-500"><Loader2 className="h-5 w-5 animate-spin" />Завантаження учнів...</div>
              ) : visibleStudents.length ? (
                <div className="grid gap-4 md:grid-cols-2 2xl:grid-cols-3">
                  {visibleStudents.map((student) => <StudentCard key={student.id} student={student} />)}
                </div>
              ) : (
                <div className="flex min-h-56 flex-col items-center justify-center rounded-lg border border-slate-200 bg-white px-4 text-center">
                  <Users className="h-8 w-8 text-slate-400" aria-hidden="true" />
                  <h2 className="mt-3 text-sm font-semibold text-slate-800">{students.length ? "За цими фільтрами учнів не знайдено" : "Учнів поки немає"}</h2>
                  {hasFilters ? <button type="button" onClick={resetFilters} className="mt-3 text-sm font-semibold text-slate-600 underline underline-offset-4">Скинути фільтри</button> : null}
                </div>
              )}
            </div>
          </main>
          <AppFooter />
        </div>
      </div>
    </div>
  );
}

function FilterSelect({ id, label, value, onChange, options, emptyLabel }) {
  return <div className="min-w-0"><label htmlFor={id} className="mb-2 block text-[0.75rem] font-semibold text-slate-600">{label}</label><div className="relative"><select id={id} value={value} onChange={(event) => onChange(event.target.value)} className={cn(fieldClasses, "appearance-none px-3 pr-9")}><option value="">{emptyLabel}</option>{options.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select><ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" /></div></div>;
}

function StudentCard({ student }) {
  const initials = student.fullName.split(/\s+/).slice(0, 2).map((part) => part[0]).join("");
  const messageParams = new URLSearchParams({ recipient: student.fullName, recipientId: String(student.userId) });
  return (
    <article className="flex min-w-0 flex-col rounded-lg border border-slate-200 bg-white p-5 shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)]">
      <div className="flex items-start gap-3">
        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-slate-100 text-sm font-bold text-slate-600" aria-hidden="true">{initials}</div>
        <div className="min-w-0"><h2 className="break-words text-[0.92rem] font-bold leading-5 text-slate-900">{student.fullName}</h2><span className="mt-2 inline-block rounded-md bg-slate-100 px-2 py-1 text-[0.72rem] font-medium text-slate-600">{student.className} клас</span></div>
      </div>
      <div className="mt-4 border-t border-slate-200 pt-4">
        <h3 className="flex items-center gap-2 text-[0.75rem] font-semibold text-slate-500"><BookOpen className="h-4 w-4" aria-hidden="true" />Викладаю предмети</h3>
        <div className="mt-2 flex flex-wrap gap-1.5">{student.subjects.map((subject) => <span key={subject.id} className="max-w-full break-words rounded-md border border-slate-200 bg-slate-50 px-2 py-1 text-[0.72rem] text-slate-700">{subject.name}</span>)}</div>
      </div>
      <div className="mt-4 border-t border-slate-200 pt-4">
        <h3 className="flex items-center gap-2 text-[0.75rem] font-semibold text-slate-500"><Users className="h-4 w-4" aria-hidden="true" />Батьки</h3>
        {student.parents.length ? <div className="mt-2 space-y-3">{student.parents.map((parent, index) => <div key={index} className="min-w-0 space-y-1.5 text-[0.78rem]"><p className="break-words font-semibold text-slate-700">{parent.fullName}</p>{parent.phone ? <Link to={`tel:${parent.phone.replace(/[^+\d]/g, "")}`} className="flex items-center gap-2 text-slate-500 hover:text-slate-900"><Phone className="h-3.5 w-3.5 shrink-0" aria-hidden="true" /><span>{formatPhoneForDisplay(parent.phone)}</span></Link> : null}{parent.email ? <Link to={`mailto:${parent.email}`} className="flex items-start gap-2 text-slate-500 hover:text-slate-900"><Mail className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" /><span className="min-w-0 break-all">{parent.email}</span></Link> : null}</div>)}</div> : <p className="mt-2 text-[0.78rem] text-slate-400">Контакти батьків не додано</p>}
      </div>
      <div className="mt-auto pt-5"><Link to={`/teacher/messages?${messageParams}`} className="inline-flex min-h-10 w-full items-center justify-center gap-2 rounded-md bg-slate-800 px-3 py-2 text-[0.8rem] font-semibold text-white hover:bg-slate-700" aria-label={`Написати повідомлення: ${student.fullName}`}><Send className="h-4 w-4 shrink-0" aria-hidden="true" />Написати повідомлення</Link></div>
    </article>
  );
}

function TopBar() {
  const logout = async () => {
    try { await authApi.logout(); } catch { /* The session may already be expired. */ }
    navigate("login");
  };
  return <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-slate-200/80 bg-white px-4 sm:px-5 lg:px-6"><Link to="/teacher" className="inline-flex min-w-0 items-center gap-2 text-slate-900" aria-label="Електронний щоденник - головна"><GraduationCap className="h-7 w-7 shrink-0 sm:h-8 sm:w-8" strokeWidth={2.4} aria-hidden="true" /><span className="max-w-[150px] truncate text-[0.92rem] font-bold min-[420px]:max-w-none sm:text-[1.05rem]">Електронний щоденник</span></Link><div className="flex items-center gap-1.5 sm:gap-4"><NotificationButton /><CurrentUserBadge /><button type="button" onClick={logout} className="inline-flex h-10 items-center gap-2 rounded-md px-3 text-[0.82rem] font-semibold text-slate-500 hover:bg-slate-100 hover:text-slate-900"><LogOut className="h-4 w-4" aria-hidden="true" /><span className="hidden sm:inline">Вийти</span></button></div></header>;
}

