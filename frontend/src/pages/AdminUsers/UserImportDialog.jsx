import { useEffect, useId, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { AlertCircle, CheckCircle2, ChevronDown, ChevronLeft, ChevronRight, Download, FileSpreadsheet, FileUp, Loader2, X } from "lucide-react";
import { Button } from "@/components/ui/button.jsx";
import { adminUsersApi } from "@/lib/api.js";
import { cn } from "@/lib/utils.js";
import { IMPORT_PAGE_SIZE as PAGE_SIZE, importRows, importCellError } from "./userImportModel.js";

const columns = ["Роль", "Логін", "Прізвище", "Ім’я", "По батькові", "Електронна пошта", "Телефон", "Дата народження", "Клас", "Навчальний рік", "Предмет", "Логін учня"];
const start = new Date().getMonth() >= 8 ? new Date().getFullYear() : new Date().getFullYear() - 1;
const examples = [
  ["Учень", "student.example", "Петренко", "Іван", "Олександрович", "student@example.org", "", `15.04.${new Date().getFullYear() - 15}`, "9-А", `${start}/${start + 1}`, "", ""],
  ["Вчитель", "teacher.example", "Коваленко", "Олена", "Іванівна", "teacher@example.org", "+380671112234", "20.03.1985", "", "", "Математика", ""],
  ["Батьки", "parent.example", "Петренко", "Олександр", "Іванович", "parent@example.org", "+380671112235", "10.05.1980", "", "", "", "student.example"],
];

function ImportCell({ number, index, value, reason, linkedStudent }) {
  const cell = useRef(null), tooltip = useRef(null), timer = useRef(null);
  const id = useId();
  const [open, setOpen] = useState(false), [position, setPosition] = useState(null);
  const hide = () => { clearTimeout(timer.current); setOpen(false); setPosition(null); };
  useEffect(() => {
    document.addEventListener("scroll",hide,true); window.addEventListener("resize",hide);
    return () => { clearTimeout(timer.current);document.removeEventListener("scroll",hide,true);window.removeEventListener("resize",hide); };
  }, []);
  useLayoutEffect(() => {
    if (!open || !tooltip.current || !cell.current) return;
    const rect = cell.current.getBoundingClientRect(), tip = tooltip.current.getBoundingClientRect();
    const left = Math.max(12,Math.min(rect.left + rect.width/2 - tip.width/2,window.innerWidth-tip.width-12));
    const top = rect.top >= tip.height+20 ? rect.top-tip.height-8 : Math.min(rect.bottom+8,window.innerHeight-tip.height-12);
    setPosition({left,top:Math.max(12,top)});
  }, [open]);
  return <td ref={cell} data-import-cell={`${number}:${index}`} data-invalid={Boolean(reason)} tabIndex={reason ? 0 : undefined}
    aria-label={reason ? `${value || "Порожнє поле"}: ${reason}` : undefined} aria-describedby={open ? id : undefined}
    onMouseEnter={() => { if(reason)timer.current=setTimeout(()=>setOpen(true),300); }} onMouseLeave={hide}
    onFocus={() => { if(reason)setOpen(true); }} onBlur={hide} onKeyDown={event => { if(event.key==="Escape"){event.stopPropagation();hide();} }}
    className={cn("max-w-72 whitespace-nowrap border-b border-slate-200 px-3 py-2.5 text-slate-700",reason && "bg-red-50 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-red-400")}>
    {value || "—"}
    {linkedStudent ? <span className="mt-0.5 block min-w-36 whitespace-normal text-xs leading-4 text-slate-500">
      {linkedStudent.fullName} · {linkedStudent.className ? `${linkedStudent.className} клас` : "Без класу"}
    </span> : null}
    {open ? <span ref={tooltip} id={id} role="tooltip" className="pointer-events-none fixed z-[110] px-3 py-2.5 text-xs font-normal leading-4 shadow-lg" style={{...position,visibility:position ? "visible" : "hidden",width:"max-content",maxWidth:"min(280px, calc(100vw - 24px))",whiteSpace:"normal",borderRadius:6,backgroundColor:"#17212b",color:"#fff"}}>{reason}</span> : null}
  </td>;
}

export function UserImportDialog({ onClose, onImported }) {
  const dialog = useRef(null), input = useRef(null), pending = useRef(false);
  const id = useId();
  const [format, setFormat] = useState("xlsx"), [file, setFile] = useState(null);
  const [report, setReport] = useState(null), [result, setResult] = useState(null);
  const [action, setAction] = useState(""), [error, setError] = useState(""), [page, setPage] = useState(0);
  const [filter, setFilter] = useState("all"), [exampleOpen, setExampleOpen] = useState(true);
  const table = useRef(null);
  const busy = Boolean(action);
  useEffect(() => {
    const previous = document.activeElement, overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden"; dialog.current.showModal();
    return () => { dialog.current?.close(); document.body.style.overflow = overflow; previous?.focus(); };
  }, []);

  const chooseFormat = value => {
    setFormat(value); setFile(null); setReport(null); setError(""); setPage(0); setFilter("all");
    if (input.current) input.current.value = "";
  };
  const preview = async selected => {
    if (!selected || pending.current) return;
    setError(""); setReport(null); setFile(selected); setPage(0); setFilter("all");
    if (!selected.name.toLowerCase().endsWith(`.${format}`)) { setError(`Оберіть файл у форматі ${format.toUpperCase()}.`); return; }
    if (selected.size > 5 * 1024 * 1024) { setError("Розмір файлу не має перевищувати 5 МБ."); return; }
    pending.current = true; setAction("preview");
    try { setReport(await adminUsersApi.previewImport(selected, format)); }
    catch (requestError) { setError(requestError.message || "Не вдалося перевірити файл."); }
    finally { pending.current = false; setAction(""); }
  };
  const download = async () => {
    if (pending.current) return;
    pending.current = true; setAction("template"); setError("");
    try {
      const blob = await adminUsersApi.importTemplate(format), url = URL.createObjectURL(blob);
      const link = document.createElement("a"); link.href = url; link.download = `users-template.${format}`;
      link.click(); window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (requestError) { setError(requestError.message || "Не вдалося завантажити шаблон."); }
    finally { pending.current = false; setAction(""); }
  };
  const confirm = async () => {
    if (pending.current || !report || report.invalid || result) return;
    pending.current = true; setAction("confirm"); setError("");
    try {
      const outcome = await adminUsersApi.confirmImport(file, format, report.fingerprint);
      setResult(outcome); void onImported(outcome.message);
    } catch (requestError) {
      setError(requestError.message || "Не вдалося імпортувати користувачів.");
      // Refresh row errors after races, duplicate confirmations, or changed database data.
      try { setReport(await adminUsersApi.previewImport(file, format)); setPage(0); setFilter("all"); }
      catch { setReport(null); }
    } finally { pending.current = false; setAction(""); }
  };
  const rowStart = page * PAGE_SIZE;
  const filtered = importRows(report,filter);
  const rows = report ? filtered.slice(rowStart, rowStart + PAGE_SIZE) : examples.map((values, index) => ({ number: index + 2, values, errors: {} }));
  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const headings = report?.columns || columns;
  useEffect(() => { if (table.current) { table.current.scrollLeft = 0; table.current.scrollTop = 0; } }, [filter,page,report]);
  const revealError = row => {
    const col = headings.findIndex((name,index) => importCellError(row,name,index,headings));
    const cell = table.current?.querySelector(`[data-import-cell="${row.number}:${col}"]`);
    if (!cell) return;
    const viewport = table.current.getBoundingClientRect(), rect = cell.getBoundingClientRect();
    table.current.scrollTo({left: table.current.scrollLeft + rect.left - viewport.left - 68, behavior: "instant"});
    requestAnimationFrame(() => cell.focus({preventScroll:true}));
  };
  return createPortal(<dialog ref={dialog} aria-labelledby={id} aria-busy={busy}
    onCancel={event => { event.preventDefault(); if (!pending.current) onClose(); }}
    className="fixed inset-0 m-auto flex max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-[1120px] flex-col overflow-hidden rounded-lg border border-slate-200 bg-white p-0 text-slate-900 shadow-2xl backdrop:bg-slate-950/40">
    <header className="flex shrink-0 items-start justify-between gap-3 border-b border-slate-200 px-5 py-4">
      <div><h2 id={id} className="text-base font-bold">Імпорт користувачів</h2><p className="mt-1 text-xs text-slate-500">CSV / XLSX · до 200 користувачів · до 5 МБ</p></div>
      <button type="button" onClick={onClose} disabled={busy} aria-label="Закрити імпорт" className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-slate-500 hover:bg-slate-100 disabled:opacity-40"><X className="h-4 w-4" /></button>
    </header>
    <div className="min-h-0 overflow-y-auto px-5 py-4">
      {result ? <div role="status" className="py-10 text-center"><CheckCircle2 className="mx-auto h-10 w-10 text-emerald-600" /><h3 className="mt-4 text-base font-bold">Імпорт завершено</h3><p className="mt-2 text-sm text-slate-600">{result.message}</p></div> : <>
        <div className="flex flex-wrap items-end justify-between gap-3">
          <fieldset disabled={busy}><legend className="mb-2 text-xs font-semibold text-slate-600">Формат файлу</legend><div className="inline-flex gap-1 rounded-md border border-slate-200 bg-slate-100 p-1">{["xlsx", "csv"].map(value => <button type="button" key={value} aria-pressed={format === value} onClick={() => chooseFormat(value)} className={cn("h-8 min-w-20 rounded px-4 text-xs font-semibold", format === value ? "bg-white text-slate-900 shadow-xs" : "text-slate-500 hover:text-slate-800")}>{value.toUpperCase()}</button>)}</div></fieldset>
          <Button type="button" variant="ghost" disabled={busy} onClick={() => void download()} className="h-10 rounded-md border border-slate-300 px-3 text-xs"><Download className="h-4 w-4" />Завантажити шаблон {format.toUpperCase()}</Button>
        </div>
        <input ref={input} type="file" accept={`.${format}`} disabled={busy} className="sr-only" aria-label="Файл користувачів" onChange={event => void preview(event.target.files?.[0])} />
        <button type="button" disabled={busy} onClick={() => { input.current.value = ""; input.current.click(); }}
          onDragOver={event => event.preventDefault()} onDrop={event => { event.preventDefault(); if (!busy) void preview(event.dataTransfer.files?.[0]); }}
          className="mt-4 flex min-h-24 w-full items-center justify-center gap-3 rounded-md border border-dashed border-slate-300 bg-slate-50 px-4 py-4 text-sm text-slate-600 hover:border-slate-400 disabled:opacity-60">
          {action === "preview" ? <Loader2 className="h-5 w-5 shrink-0 animate-spin" /> : <FileUp className="h-5 w-5 shrink-0" />}<span className="min-w-0 break-all">{action === "preview" ? "Перевірка файлу…" : file ? `${file.name} · Обрати інший файл` : "Обрати або перетягнути файл"}</span>
        </button>
        {error ? <p role="alert" className="mt-3 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-600">{error}</p> : null}
        {action === "preview" ? <p role="status" className="mt-4 text-xs text-slate-500">Перевіряємо поля, дублікати та прив’язки…</p> : <>
          <div className="mb-3 mt-4 flex flex-wrap items-center justify-between gap-3">
            {report ? <h3 className="text-sm font-bold">Попередній перегляд</h3> : <button type="button" aria-expanded={exampleOpen} aria-controls={`${id}-table`} onClick={() => setExampleOpen(value => !value)} className="flex items-center gap-2 py-1 text-sm font-bold"><FileSpreadsheet className="h-4 w-4 text-slate-500" />Приклад заповнення<ChevronDown className={cn("h-4 w-4 text-slate-500 transition-transform",!exampleOpen && "-rotate-90")} /></button>}
            {report ? <div role="status" aria-label="Статистика перевірки" className="flex flex-wrap gap-x-4 gap-y-2 text-xs">
              <span className="inline-flex items-center gap-1.5 text-slate-600"><span className="h-1.5 w-1.5 rounded-full bg-slate-400" />Усього <strong className="tabular-nums">{report.total}</strong></span>
              <span className="inline-flex items-center gap-1.5 text-emerald-700"><CheckCircle2 className="h-3.5 w-3.5" />Коректних <strong className="tabular-nums">{report.valid}</strong></span>
              <span className={cn("inline-flex items-center gap-1.5",report.invalid ? "text-red-600" : "text-slate-500")}><AlertCircle className="h-3.5 w-3.5" />З помилками <strong className="tabular-nums">{report.invalid}</strong></span>
            </div> : null}
          </div>
          {report ? <div role="group" aria-label="Фільтр записів імпорту" className="mb-3 inline-flex gap-1 rounded-md border border-slate-200 bg-slate-100 p-1">{[["all","Усі"],["errors","З помилками"]].map(([value,label]) => <button key={value} type="button" disabled={busy} aria-pressed={filter===value} onClick={() => {setFilter(value);setPage(0);}} className={cn("h-8 rounded px-3 text-xs font-semibold",filter===value ? "bg-white text-slate-900 shadow-xs" : "text-slate-500 hover:text-slate-800")}>{label}</button>)}</div> : null}
          {report || exampleOpen ? <div id={`${id}-table`} ref={table} className={cn("rounded-md border border-slate-200",report ? "max-h-[300px] overflow-auto" : "overflow-x-auto")} tabIndex={0} aria-label={report ? "Дані імпорту" : "Приклад заповнення"}>
            <table className="w-full border-separate border-spacing-0 text-left text-xs"><thead className="sticky top-0 z-20 bg-slate-50 text-slate-500"><tr>
              <th className="sticky left-0 z-30 min-w-14 border-b border-r border-slate-200 bg-slate-50 px-3 py-2.5">Рядок</th>
              {headings.map(name => <th key={name} className="whitespace-nowrap border-b border-slate-200 bg-slate-50 px-3 py-2.5 font-semibold">{name}</th>)}
              {report ? <th className="sticky right-0 z-30 min-w-28 border-b border-l border-slate-200 bg-slate-50 px-3 py-2.5">Перевірка</th> : null}
            </tr></thead><tbody>{rows.map(row => <tr key={row.number}>
              <td className="sticky left-0 z-10 border-b border-r border-slate-200 bg-white px-3 py-2.5 text-slate-500">{row.number}</td>
              {row.values.map((value,index) => <ImportCell key={index} number={row.number} index={index} value={value} linkedStudent={headings[index] === "Логін учня" ? row.linkedStudent : null} reason={importCellError(row,headings[index],index,headings)} />)}
              {report ? <td className="sticky right-0 z-10 border-b border-l border-slate-200 bg-white px-3 py-2.5">{Object.keys(row.errors).length ? <button type="button" onClick={() => revealError(row)} aria-label={`Помилка в рядку ${row.number}`} className="inline-flex items-center gap-1.5 whitespace-nowrap font-semibold text-red-600 underline-offset-2 hover:underline"><AlertCircle className="h-3.5 w-3.5" />Помилка</button> : <span className="inline-flex items-center gap-1.5 whitespace-nowrap text-emerald-700"><CheckCircle2 className="h-3.5 w-3.5" />Готово</span>}</td> : null}
            </tr>)}</tbody></table>
            {report && !rows.length ? <p className="sticky left-0 w-full px-4 py-6 text-center text-xs text-slate-500">Записів із помилками немає.</p> : null}
          </div> : null}
          {report && pageCount > 1 ? <nav aria-label="Сторінки імпорту" className="mt-3 flex items-center justify-end gap-3 text-xs text-slate-500"><span>{rowStart + 1}–{Math.min(rowStart + PAGE_SIZE,filtered.length)} із {filtered.length}</span><button type="button" disabled={!page || busy} onClick={() => setPage(value => value - 1)} aria-label="Попередні записи імпорту" className="flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 disabled:opacity-40"><ChevronLeft className="h-4 w-4" /></button><button type="button" disabled={page >= pageCount - 1 || busy} onClick={() => setPage(value => value + 1)} aria-label="Наступні записи імпорту" className="flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 disabled:opacity-40"><ChevronRight className="h-4 w-4" /></button></nav> : null}
          <p className="mt-3 text-xs leading-5 text-slate-500">{report ? (report.invalid ? "Виправте файл і завантажте його повторно." : "Готово до імпорту. Будуть створені лише нові акаунти.") : `${format === "csv" ? "Збережіть файл у кодуванні UTF-8 із роздільником комою або крапкою з комою. " : ""}Дата народження у форматі дд.мм.рррр. Для учня вкажіть клас і поточний навчальний рік, телефон залиште порожнім. Для вчителя вкажіть предмет, для батьків вкажіть логін наявного учня або учня з цього файлу. Непотрібні для ролі колонки залиште порожніми. Приклади замініть власними даними.`}</p>
        </>}
      </>}
    </div>
    <footer className="flex shrink-0 flex-wrap justify-end gap-2 border-t border-slate-200 px-5 py-4">
      <Button type="button" variant="ghost" disabled={busy} onClick={onClose} className="h-10 rounded-md border border-slate-300 px-4 text-xs">{result ? "Готово" : "Скасувати"}</Button>
      {!result ? <Button type="button" disabled={busy || !report || report.invalid > 0 || !file} onClick={() => void confirm()} className="h-10 rounded-md px-4 text-xs">{action === "confirm" ? <Loader2 className="h-4 w-4 animate-spin" /> : <FileUp className="h-4 w-4" />}{action === "confirm" ? "Імпортування…" : `Імпортувати${report ? ` (${report.total})` : ""}`}</Button> : null}
    </footer>
  </dialog>, document.body);
}
