import { useRef } from "react";
import { CalendarDays } from "lucide-react";
import { Input } from "@/components/ui/input.jsx";
import { cn } from "@/lib/utils.js";

function formatBirthDateInput(value) {
  const digits = value.replace(/\D/g, "").slice(0, 8);
  return [digits.slice(0, 2), digits.slice(2, 4), digits.slice(4, 8)]
    .filter(Boolean)
    .join("/");
}

function displayDateToIso(value) {
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value);
  if (!match) return "";

  const [, day, month, year] = match;
  const date = new Date(Number(year), Number(month) - 1, Number(day));
  if (
    date.getFullYear() !== Number(year)
    || date.getMonth() !== Number(month) - 1
    || date.getDate() !== Number(day)
  ) return "";

  return `${year}-${month}-${day}`;
}

function isoDateToDisplay(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  return match ? `${match[3]}/${match[2]}/${match[1]}` : "";
}

function subtractYears(date, years) {
  const year = date.getFullYear() - years;
  const month = date.getMonth();
  const day = Math.min(date.getDate(), new Date(year, month + 1, 0).getDate());
  return new Date(year, month, day);
}

function toLocalIsoDate(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function getBirthDateBounds(role, today = new Date()) {
  const isStudent = role === "STUDENT";
  const isAdultRole = role === "TEACHER" || role === "PARENT";
  const minimumAge = isStudent ? 5 : isAdultRole ? 18 : 5;
  const maximumAge = isStudent ? 20 : 100;

  return {
    min: toLocalIsoDate(subtractYears(today, maximumAge)),
    max: toLocalIsoDate(subtractYears(today, minimumAge)),
  };
}

export function BirthDateInput({
  id,
  name = "birthDate",
  value,
  onChange,
  min,
  max,
  className,
  ...props
}) {
  const pickerRef = useRef(null);

  const openPicker = () => {
    const picker = pickerRef.current;
    if (!picker) return;

    try {
      if (typeof picker.showPicker === "function") {
        picker.showPicker();
      } else {
        picker.click();
      }
    } catch {
      picker.focus();
    }
  };

  return (
    <div className="relative">
      <button
        type="button"
        onClick={openPicker}
        className="absolute left-0 top-0 z-10 flex h-10 w-10 items-center justify-center rounded-l-md text-slate-400 transition-colors hover:text-slate-700 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-primary/40"
        aria-label="Обрати дату народження в календарі"
        title="Обрати дату в календарі"
      >
        <CalendarDays className="h-4 w-4" aria-hidden="true" />
      </button>
      <Input
        id={id}
        name={name}
        type="text"
        inputMode="numeric"
        autoComplete="bday"
        maxLength={10}
        value={value}
        onChange={(event) => onChange(formatBirthDateInput(event.target.value))}
        placeholder="дд/мм/рррр"
        className={cn("pl-10", className)}
        {...props}
      />
      <input
        ref={pickerRef}
        type="date"
        value={displayDateToIso(value)}
        min={min}
        max={max}
        onChange={(event) => onChange(isoDateToDisplay(event.target.value))}
        tabIndex={-1}
        aria-hidden="true"
        className="pointer-events-none absolute left-5 top-1/2 h-px w-px -translate-y-1/2 opacity-0"
      />
    </div>
  );
}
