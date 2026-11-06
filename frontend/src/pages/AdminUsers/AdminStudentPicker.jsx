import { useEffect, useId, useRef, useState } from "react";
import { Search, X } from "lucide-react";
import { Input } from "@/components/ui/input.jsx";
import { adminUsersApi } from "@/lib/api.js";
import { cn } from "@/lib/utils.js";

function studentDetails(student) {
  return [student.className ? `${student.className} клас` : "Без класу", student.login].join(" · ");
}

export function AdminStudentPicker({ student, onChange, error }) {
  const id = useId();
  const input = useRef(null);
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [result, setResult] = useState(null);
  const [active, setActive] = useState(-1);
  const term = query.trim().replace(/\s+/g, " ");
  const showList = open && !student && term.length >= 2;
  const current = result?.term === term ? result : null;
  const options = current?.options ?? [];

  useEffect(() => {
    if (!showList) return;
    let ignore = false;
    const timeout = setTimeout(async () => {
      try {
        const options = await adminUsersApi.searchStudents(term);
        if (!ignore) setResult({ term, options });
      } catch {
        if (!ignore) setResult({ term, error: true });
      }
    }, 200);
    return () => { ignore = true; clearTimeout(timeout); };
  }, [showList, term]);

  const select = (option) => {
    onChange(option);
    setQuery("");
    setOpen(false);
    setActive(-1);
  };

  const search = (value) => {
    if (student) onChange(null);
    if (value.trim().replace(/\s+/g, " ") !== term) setResult(null);
    setQuery(value);
    setOpen(true);
    setActive(-1);
  };

  const handleKey = (event) => {
    if (event.key === "Escape" && open) {
      event.stopPropagation();
      setOpen(false);
      setActive(-1);
    } else if (["ArrowDown", "ArrowUp"].includes(event.key) && !student && term.length >= 2) {
      event.preventDefault();
      setOpen(true);
      if (!options.length) return;
      const next = event.key === "ArrowDown"
        ? (active + 1) % options.length
        : (active <= 0 ? options.length - 1 : active - 1);
      setActive(next);
      document.getElementById(`${id}-option-${next}`)?.scrollIntoView({ block: "nearest" });
    } else if (event.key === "Enter" && !student) {
      event.preventDefault();
      if (showList && options[active]) select(options[active]);
    }
  };

  return (
    <div className="min-w-0 sm:col-span-2" onBlur={(event) => {
      if (!event.currentTarget.contains(event.relatedTarget)) {
        setOpen(false);
        setActive(-1);
      }
    }}>
      <label htmlFor={id} className="block text-[0.8rem] font-semibold text-slate-700">Учень для прив’язки</label>
      <div className="relative mt-2">
        <Search aria-hidden="true" className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
        <Input
          ref={input}
          id={id}
          role="combobox"
          aria-expanded={showList}
          aria-controls={showList ? `${id}-list` : undefined}
          aria-autocomplete="list"
          aria-activedescendant={showList && options[active] ? `${id}-option-${active}` : undefined}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? `${id}-error` : student ? `${id}-details` : undefined}
          value={student?.fullName ?? query}
          onChange={(event) => search(event.target.value)}
          onFocus={() => { if (!student) setOpen(true); }}
          onKeyDown={handleKey}
          className={cn("pl-9 pr-9", error && "border-red-400")}
          autoComplete="off"
          spellCheck={false}
          maxLength={152}
          placeholder="Пошук за ПІБ або логіном"
        />
        {student || query ? (
          <button type="button" aria-label="Очистити вибір учня" onClick={() => { search(""); input.current?.focus(); }}
            className="absolute right-1 top-1/2 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-md text-slate-500 hover:bg-slate-100">
            <X className="h-4 w-4" />
          </button>
        ) : null}
      </div>
      {student ? <p id={`${id}-details`} className="mt-1.5 break-words text-xs text-slate-500">{studentDetails(student)}</p> : null}
      {showList ? (
        <div className="mt-1 overflow-hidden rounded-md border border-slate-200 bg-white shadow-sm">
          <div id={`${id}-list`} role="listbox" aria-label="Знайдені учні" className="max-h-44 overflow-y-auto p-1">
            {options.map((option, index) => (
              <button key={option.id} type="button" role="option" id={`${id}-option-${index}`} tabIndex={-1}
                aria-selected={active === index} onMouseDown={(event) => event.preventDefault()}
                onClick={() => select(option)}
                className={cn("block w-full rounded px-3 py-2 text-left hover:bg-slate-100", active === index && "bg-slate-100")}>
                <span className="block break-words text-sm font-medium text-slate-900">{option.fullName}</span>
                <span className="block break-words text-xs text-slate-500">{studentDetails(option)}</span>
              </button>
            ))}
          </div>
          {!options.length ? <p role="status" className="px-3 pb-3 text-xs text-slate-500">
            {!current ? "Пошук…" : current.error ? "Не вдалося завантажити учнів." : "Учнів не знайдено."}
          </p> : null}
        </div>
      ) : null}
      {error ? <p id={`${id}-error`} className="mt-1.5 text-[0.72rem] text-red-500">{error}</p> : null}
    </div>
  );
}
