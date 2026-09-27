import { useEffect, useId, useRef, useState } from "react";
import { ChevronDown, Search } from "lucide-react";
import { cn } from "@/lib/utils.js";

export function SearchableSelect({ label, options, value, onChange, placeholder = "Пошук", emptyLabel = "Усі предмети" }) {
  const id = useId();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [activeIndex, setActiveIndex] = useState(0);
  const container = useRef(null);
  const optionRefs = useRef([]);
  const terms = query.trim().toLocaleLowerCase("uk-UA").split(/\s+/).filter(Boolean);
  const filtered = [{ id: "", name: emptyLabel }, ...options].filter(item => terms.every(term => item.name.toLocaleLowerCase("uk-UA").includes(term)));
  const selected = options.find(item => String(item.id) === String(value));
  useEffect(() => { if (open) optionRefs.current[activeIndex]?.scrollIntoView({ block: "nearest" }); }, [activeIndex, query, open]);
  const start = () => { setOpen(true); setQuery(""); setActiveIndex(0); };
  const select = item => { onChange(String(item.id)); setOpen(false); setQuery(""); };
  return <div ref={container} className="min-w-0">
    <label htmlFor={id} className="mb-2 block text-[0.75rem] font-semibold text-slate-600">{label}</label>
    <div className="relative">
      <Search className="pointer-events-none absolute left-3 top-5 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
      <input id={id} role="combobox" aria-expanded={open} aria-autocomplete="list" aria-controls={`${id}-options`} aria-activedescendant={open && filtered[activeIndex] ? `${id}-option-${activeIndex}` : undefined} autoComplete="off" value={open ? query : selected?.name || emptyLabel} placeholder={placeholder} onFocus={() => !open && start()} onClick={() => !open && start()} onChange={event => { setQuery(event.target.value); setActiveIndex(0); setOpen(true); }} onBlur={event => { if (!container.current?.contains(event.relatedTarget)) { setOpen(false); setQuery(""); } }} onKeyDown={event => {
        if (event.key === "ArrowDown" || event.key === "ArrowUp") { event.preventDefault(); if (!open) start(); else setActiveIndex(index => Math.max(0, Math.min(filtered.length - 1, index + (event.key === "ArrowDown" ? 1 : -1)))); }
        else if (event.key === "Enter" && open) { event.preventDefault(); if (filtered[activeIndex]) select(filtered[activeIndex]); }
        else if (event.key === "Escape") { setOpen(false); setQuery(""); }
      }} className="h-10 w-full rounded-md border border-slate-300 bg-white pl-9 pr-9 text-[0.82rem] text-slate-700 outline-hidden hover:border-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200" />
      <ChevronDown className="pointer-events-none absolute right-3 top-5 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
      {open ? <ul id={`${id}-options`} role="listbox" aria-label={label} className="absolute z-40 mt-1 max-h-64 w-full overflow-y-auto rounded-md border border-slate-200 bg-white py-1 shadow-lg">{filtered.length ? filtered.map((item, index) => <li key={item.id} id={`${id}-option-${index}`} ref={node => { optionRefs.current[index] = node; }} role="option" aria-selected={String(item.id) === String(value)} onMouseDown={event => event.preventDefault()} onMouseEnter={() => setActiveIndex(index)} onClick={() => select(item)} className={cn("min-h-10 cursor-pointer break-words px-3 py-2 text-[0.82rem]", activeIndex === index ? "bg-slate-100 text-slate-900" : "text-slate-700 hover:bg-slate-50")}>{item.name}</li>) : <li className="px-3 py-3 text-xs text-slate-500">Предметів не знайдено</li>}</ul> : null}
    </div>
  </div>;
}
