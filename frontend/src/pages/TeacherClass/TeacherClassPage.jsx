import { navigate } from "@/lib/navigation.js";
import { Link } from "react-router-dom";
import { useEffect, useMemo, useState } from "react";
import { BarChart3, BookOpen, CalendarDays, ChevronRight, GraduationCap, Loader2, LogOut, Search } from "lucide-react";
import { AppFooter } from "@/components/AppFooter.jsx";
import { NotificationButton } from "@/components/NotificationButton.jsx";
import { TeacherNavigation } from "@/components/TeacherNavigation.jsx";
import { Alert } from "@/components/ui/alert.jsx";
import { CurrentUserBadge } from "@/lib/currentUser.jsx";
import { ApiError, authApi, teacherClassApi } from "@/lib/api.js";
import { StudentLearningHeader } from "@/pages/AdminClasses/StudentLearningHeader.jsx";
import { SubjectProgressView } from "@/pages/Subjects/UserSubjectsPage.jsx";

const statusLabels = { STUDYING: "Навчається", GRADUATED: "Випускник", EXPELLED: "Відрахований" };
const statusStyles = {
  STUDYING: "border-emerald-200 bg-emerald-50 text-emerald-700",
  GRADUATED: "border-slate-200 bg-slate-100 text-slate-600",
  EXPELLED: "border-red-200 bg-red-50 text-red-700",
};

function errorMessage(error, fallback) {
  return error instanceof ApiError ? error.message : fallback;
}

function formatMetric(value) {
  return value == null ? "—" : new Intl.NumberFormat("uk-UA", { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(value);
}

function TeacherClassLayout({ title, subtitle, breadcrumb, children }) {
  const logout = async () => {
    try { await authApi.logout(); } catch { /* Session may already be expired. */ }
    navigate("login");
  };
  return (
    <div className="flex min-h-screen flex-col bg-slate-50 text-slate-800">
      <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-slate-200/80 bg-white px-4 sm:px-5 lg:px-6">
        <Link to="/teacher" className="inline-flex min-w-0 items-center gap-2 text-slate-900" aria-label="Електронний щоденник - головна">
          <GraduationCap className="h-7 w-7 shrink-0 sm:h-8 sm:w-8" strokeWidth={2.4} aria-hidden="true" />
          <span className="max-w-[150px] truncate text-[0.92rem] font-bold min-[420px]:max-w-none sm:text-[1.05rem]">Електронний щоденник</span>
        </Link>
        <div className="flex items-center gap-1.5 sm:gap-4">
          <NotificationButton /><CurrentUserBadge />
          <button type="button" onClick={logout} className="inline-flex h-10 items-center gap-2 rounded-md px-3 text-[0.82rem] font-semibold text-slate-500 hover:bg-slate-100 hover:text-slate-900">
            <LogOut className="h-4 w-4" aria-hidden="true" /><span className="hidden sm:inline">Вийти</span>
          </button>
        </div>
      </header>
      <TeacherNavigation activeRoute="teacher/my-class" compact />
      <div className="flex flex-1">
        <TeacherNavigation activeRoute="teacher/my-class" />
        <div className="flex min-w-0 flex-1 flex-col lg:border-l lg:border-slate-200/80">
          <main className="min-w-0 flex-1 px-4 py-5 pb-8 sm:px-6 lg:px-8 lg:py-6">
            <div className="mx-auto w-full max-w-[1280px] 2xl:max-w-[1500px] min-[1920px]:max-w-[1680px]">
              {breadcrumb ? <nav aria-label="Шлях сторінки" className="mb-4 flex items-center gap-2 text-xs text-slate-500">{breadcrumb}</nav> : null}
              <header className="mb-5 sm:mb-6">
                <h1 className="text-[1.45rem] font-bold leading-tight text-slate-900 sm:text-[1.65rem]">{title}</h1>
                <p className="mt-2 text-[0.9rem] text-slate-500">{subtitle}</p>
              </header>
              {children}
            </div>
          </main>
          <AppFooter />
        </div>
      </div>
    </div>
  );
}

export function TeacherClassPage({ initialClassId = "" }) {
  const [classes, setClasses] = useState(null);
  const [classId, setClassId] = useState(initialClassId);
  const [schoolClass, setSchoolClass] = useState(null);
  const [loadingClass, setLoadingClass] = useState(false);
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let active = true;
    setError("");
    teacherClassApi.findAll().then((items) => {
      if (!active) return;
      setClasses(items);
      setClassId((current) => items.some((item) => String(item.id) === String(current))
        ? String(current) : items[0] ? String(items[0].id) : "");
    }).catch((requestError) => {
      if (active) setError(errorMessage(requestError, "Не вдалося завантажити ваші класи."));
    });
    return () => { active = false; };
  }, [reload]);

  useEffect(() => {
    if (!classId || !classes?.length) return undefined;
    let active = true;
    setSchoolClass(null);
    setLoadingClass(true);
    setError("");
    teacherClassApi.findById(classId).then((data) => {
      if (active) setSchoolClass(data);
    }).catch((requestError) => {
      if (active) setError(errorMessage(requestError, "Не вдалося завантажити клас."));
    }).finally(() => { if (active) setLoadingClass(false); });
    return () => { active = false; };
  }, [classId, classes]);

  const visibleStudents = useMemo(() => {
    const terms = query.trim().toLocaleLowerCase("uk-UA").split(/\s+/).filter(Boolean);
    return (schoolClass?.students || []).filter((student) => terms.every((term) =>
      `${student.fullName} ${student.login} ${student.email || ""}`.toLocaleLowerCase("uk-UA").includes(term)));
  }, [query, schoolClass]);

  return (
    <TeacherClassLayout title="Мій клас" subtitle="Склад класу та навчальні результати учнів">
      {classes?.length > 1 ? (
        <div className="mb-5 max-w-sm rounded-lg border border-slate-200 bg-white p-4">
          <label htmlFor="homeroom-class" className="mb-2 block text-xs font-semibold text-slate-600">Клас і навчальний рік</label>
          <select id="homeroom-class" value={classId} onChange={(event) => { setClassId(event.target.value); setSchoolClass(null); setLoadingClass(true); setQuery(""); }} className="h-10 w-full rounded-md border border-slate-300 bg-white px-3 text-sm text-slate-800">
            {classes.map((item) => <option key={item.id} value={item.id}>{item.name} · {item.academicYear} н. р.</option>)}
          </select>
        </div>
      ) : null}
      {error ? <div className="space-y-3"><Alert>{error}</Alert><button type="button" onClick={() => setReload((value) => value + 1)} className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium">Спробувати ще раз</button></div> : null}
      {!error && (classes === null || loadingClass) ? <div className="flex justify-center py-20"><Loader2 className="h-6 w-6 animate-spin text-slate-400" aria-label="Завантаження" /></div> : null}
      {!error && classes?.length === 0 ? <div className="rounded-lg border border-slate-200 bg-white p-8 text-center text-sm text-slate-500">За вами поки не закріплено клас.</div> : null}
      {!error && schoolClass && !loadingClass ? (
        <>
          <section className="rounded-lg border border-slate-200 bg-white p-5 sm:p-6" aria-label="Інформація про клас">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div><h2 className="text-lg font-bold text-slate-900">{schoolClass.name}</h2><p className="mt-1 text-sm text-slate-500">{schoolClass.academicYear} н. р.</p></div>
              <span className="rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-xs text-emerald-700">Класний керівник</span>
            </div>
            <div className="mt-5 flex flex-wrap items-center justify-between gap-5 border-t border-slate-100 pt-5">
              <dl className="grid min-w-0 flex-1 gap-5 sm:grid-cols-2">
                <div><dt className="text-xs text-slate-500">Класний керівник</dt><dd className="mt-2 text-sm font-semibold text-slate-800">{schoolClass.classTeacherName}</dd></div>
                <div><dt className="text-xs text-slate-500">Кількість учнів</dt><dd className="mt-2 text-sm font-semibold text-slate-800">{schoolClass.students.length} / {schoolClass.capacity}</dd></div>
              </dl>
              <Link to={`/teacher/schedule?classId=${schoolClass.id}`} className="inline-flex shrink-0 items-center gap-2 text-sm font-semibold text-slate-700 hover:text-slate-950">
                <CalendarDays className="h-4 w-4" />Переглянути розклад класу<ChevronRight className="h-4 w-4" />
              </Link>
            </div>
          </section>
          <div className="mt-5 grid gap-4 sm:grid-cols-2 xl:grid-cols-3" aria-label="Статистика класу">
            <StatCard icon={CalendarDays} label="Пропущено уроків учнями" value={schoolClass.statistics.recordedAttendance ? schoolClass.statistics.recordedAttendance - schoolClass.statistics.attended : "—"} detail="Сумарно за навчальний рік" />
            <StatCard icon={BarChart3} label="Відвідуваність класу" value={schoolClass.statistics.attendancePercent == null ? "—" : `${formatMetric(schoolClass.statistics.attendancePercent)}%`} detail={schoolClass.statistics.recordedAttendance ? `${schoolClass.statistics.attended} із ${schoolClass.statistics.recordedAttendance} внесених відміток` : "Відміток поки немає"} />
            <StatCard icon={BookOpen} label="Середній бал класу" value={formatMetric(schoolClass.statistics.averageGrade)} detail={schoolClass.statistics.gradeCount ? `${schoolClass.statistics.gradeCount} оцінок за навчальний рік` : "Оцінок поки немає"} />
          </div>
          <section className="mt-6 overflow-hidden rounded-lg border border-slate-200 bg-white">
            <div className="border-b border-slate-200 p-5"><h2 className="text-base font-bold">Учні класу <span className="ml-2 font-normal text-slate-400">{schoolClass.students.length}</span></h2></div>
            <div className="border-b border-slate-100 p-5">
              <div className="relative max-w-lg"><Search className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-slate-400" /><input type="search" aria-label="Пошук учнів за ПІБ або логіном" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Пошук за ПІБ або логіном" className="h-10 w-full rounded-md border border-slate-300 bg-white pl-10 pr-3 text-sm outline-hidden focus:border-slate-500 focus:ring-2 focus:ring-slate-200" /></div>
            </div>
            {visibleStudents.length ? <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead className="border-b border-slate-200 bg-slate-50 text-xs text-slate-500"><tr><th className="px-5 py-3 font-medium">ПІБ</th><th className="px-5 py-3 font-medium">Логін</th><th className="px-5 py-3 font-medium">Пошта</th><th className="px-5 py-3 font-medium">Статус учня</th><th className="px-5 py-3 text-right font-medium">Дії</th></tr></thead><tbody className="divide-y divide-slate-100">{visibleStudents.map((student) => <tr key={student.id} className="hover:bg-slate-50/60"><td className="px-5 py-4 font-semibold text-slate-800">{student.fullName}</td><td className="px-5 py-4 text-slate-600">{student.login}</td><td className="break-all px-5 py-4 text-slate-600">{student.email || "—"}</td><td className="px-5 py-4"><span className={`inline-flex whitespace-nowrap rounded-full border px-2.5 py-1 text-xs ${statusStyles[student.status] || statusStyles.STUDYING}`}>{statusLabels[student.status] || student.status}</span></td><td className="px-5 py-4 text-right"><Link to={`/teacher/my-class/${schoolClass.id}/students/${student.id}/learning-data`} className="inline-flex h-9 items-center gap-2 rounded-md border border-sky-300 bg-sky-50 px-3 text-xs font-medium text-sky-700 hover:bg-sky-100"><BookOpen className="h-4 w-4" />Навчальні дані</Link></td></tr>)}</tbody></table></div> : <div className="py-12 text-center text-sm text-slate-500">{query ? "Учнів за цим запитом не знайдено." : "У класі поки немає учнів."}</div>}
          </section>
        </>
      ) : null}
    </TeacherClassLayout>
  );
}

function StatCard({ icon: Icon, label, value, detail }) {
  return <section className="rounded-lg border border-slate-200 bg-white p-5"><div className="flex items-start justify-between gap-3"><div><p className="text-xs font-semibold text-slate-500">{label}</p><p className="mt-3 text-2xl font-bold text-slate-900">{value}</p><p className="mt-2 text-xs text-slate-500">{detail}</p></div><span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-slate-100 text-slate-600"><Icon className="h-4 w-4" /></span></div></section>;
}

export function TeacherStudentLearningPage({ classId, studentId }) {
  const [schoolClass, setSchoolClass] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    teacherClassApi.findById(classId).then((data) => {
      if (!active) return;
      if (!data.students.some((item) => item.id === studentId)) {
        setError("Учня не знайдено в цьому класі.");
      } else setSchoolClass(data);
    }).catch((requestError) => {
      if (active) setError(errorMessage(requestError, "Не вдалося завантажити дані учня."));
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [classId, studentId]);

  const student = schoolClass?.students.find((item) => item.id === studentId);
  const backHref = `/teacher/my-class?classId=${classId}`;
  return (
    <TeacherClassLayout title="Навчальні дані" subtitle="Оцінки, відвідування та домашні завдання учня" breadcrumb={<><Link to={backHref} className="hover:text-slate-900">Мій клас</Link><ChevronRight className="h-3.5 w-3.5" /><span className="font-medium text-slate-700" aria-current="page">Навчальні дані</span></>}>
      {loading ? <div className="flex justify-center py-20"><Loader2 className="h-6 w-6 animate-spin text-slate-400" aria-label="Завантаження" /></div> : error ? <Alert>{error}</Alert> : student ? <>
        <StudentLearningHeader student={student} className={schoolClass.name} parents={student.parents} />
        <SubjectProgressView role="HOMEROOM" classId={classId} adminStudentId={studentId} initialAcademicYear={schoolClass.academicYear} />
      </> : null}
    </TeacherClassLayout>
  );
}
