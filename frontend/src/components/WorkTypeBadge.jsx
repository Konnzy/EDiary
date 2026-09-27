import { cn } from "@/lib/utils.js";

const workTypes = {
  "Класна робота": "border-sky-200 bg-sky-50 text-sky-700",
  "Домашня робота": "border-emerald-200 bg-emerald-50 text-emerald-700",
  "Самостійна робота": "border-amber-200 bg-amber-50 text-amber-700",
  "Контрольна робота": "border-red-200 bg-red-50 text-red-600",
  "Практична робота": "border-teal-200 bg-teal-50 text-teal-700",
  "Лабораторна робота": "border-violet-200 bg-violet-50 text-violet-700",
  "Тематична": "border-indigo-200 bg-indigo-50 text-indigo-700",
  "Семестрова": "border-lime-200 bg-lime-50 text-lime-800",
  "Річна": "border-fuchsia-200 bg-fuchsia-50 text-fuchsia-800",
};
const finalWorkTypes = new Set(["Тематична", "Семестрова", "Річна"]);

export function WorkTypeBadge({ type }) {
  return <span className={cn(
    "inline-flex max-w-full items-center rounded-md border px-2 py-1 text-[0.7rem] leading-4",
    finalWorkTypes.has(type) ? "font-bold" : "font-semibold",
    workTypes[type] || "border-slate-200 bg-slate-50 text-slate-600",
  )}>{type}</span>;
}
