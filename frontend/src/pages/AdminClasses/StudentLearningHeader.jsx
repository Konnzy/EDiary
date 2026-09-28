import { BookOpen, ChevronDown, Mail, Phone, Users } from "lucide-react";

export function StudentLearningHeader({ student, className, parents = [], parentsError = "" }) {
  return (
    <section className="mb-5 rounded-lg border border-slate-200 bg-white p-4 sm:p-5" aria-label="Учень">
      <div className="flex items-center gap-4">
        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-sky-50 text-sky-700"><BookOpen className="h-5 w-5" /></span>
        <div className="min-w-0">
          <h2 className="text-base font-bold text-slate-900">{student.fullName}</h2>
          <p className="mt-1 text-xs text-slate-500">{className} · {student.login}</p>
        </div>
      </div>
      <details className="group mt-4 border-t border-slate-200 pt-3">
        <summary className="flex cursor-pointer list-none items-center gap-2 text-sm font-medium text-slate-700 hover:text-slate-900 [&::-webkit-details-marker]:hidden">
          <Users className="h-4 w-4 text-slate-500" />
          Контакти батьків{!parentsError && parents.length > 0 ? ` (${parents.length})` : ""}
          <ChevronDown className="ml-auto h-4 w-4 text-slate-500 transition-transform group-open:rotate-180" />
        </summary>
        <div className="mt-3">
          {parentsError ? <p className="text-sm text-slate-500">{parentsError}</p> : parents.length === 0 ? (
            <p className="text-sm text-slate-500">До учня не прив’язано батьків.</p>
          ) : (
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {parents.map((parent) => (
                <div key={parent.id} className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                  <p className="font-semibold text-slate-900">{parent.fullName}</p>
                  <div className="mt-2 space-y-1.5 text-sm text-slate-600">
                    <p className="flex items-center gap-2"><Mail className="h-4 w-4 shrink-0" />{parent.email?.trim() ? <a className="break-all hover:underline" href={`mailto:${parent.email}`}>{parent.email}</a> : "Не вказано"}</p>
                    <p className="flex items-center gap-2"><Phone className="h-4 w-4 shrink-0" />{parent.phone?.trim() ? <a className="hover:underline" href={`tel:${parent.phone}`}>{parent.phone}</a> : "Не вказано"}</p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </details>
    </section>
  );
}
