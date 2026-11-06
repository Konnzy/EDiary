import { useEffect, useId, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { AlertCircle, CheckCircle2, ChevronDown, Download, FileSpreadsheet, FileUp, Loader2, MinusCircle, RefreshCw, TriangleAlert, X } from "lucide-react";
import { Button } from "@/components/ui/button.jsx";
import { adminScheduleApi } from "@/lib/api.js";
import { cn } from "@/lib/utils.js";

const columns = [
  ["WEEK", "Тиждень"], ["DAY", "День"], ["LESSON", "Урок", "Номер уроку"],
  ["SUBJECT", "Предмет"], ["TEACHER", "Учитель", "Логін учителя"], ["ROOM", "Кабінет"],
];
const examples = [
  ["1", "Понеділок", "1", "Математика", "teacher.example", "101"],
  ["2", "П’ятниця", "3", "Українська мова", "teacher.language", "205"],
];
const statuses = {
  ADD: { label: "Новий урок", count: "Нові", color: "text-emerald-700", Icon: CheckCircle2 },
  UNCHANGED: { label: "Без змін", count: "Без змін", color: "text-slate-500", Icon: MinusCircle },
  CONFLICT: { label: "Конфлікт", count: "Конфлікти", color: "text-amber-700", Icon: TriangleAlert },
  INVALID: { label: "Помилка", count: "Помилки", color: "text-red-600", Icon: AlertCircle },
};

export function ScheduleImportDialog({ classId, className, academicYear, onClose, onImported }) {
  const dialog = useRef(null);
  const input = useRef(null);
  const table = useRef(null);
  const pending = useRef(false);
  const id = useId();
  const [format, setFormat] = useState("xlsx");
  const [file, setFile] = useState(null);
  const [action, setAction] = useState("");
  const [error, setError] = useState("");
  const [report, setReport] = useState(null);
  const [result, setResult] = useState(null);
  const [filter, setFilter] = useState("ALL");
  const [exampleOpen, setExampleOpen] = useState(true);
  const [decisions, setDecisions] = useState({});
  const [needsRefresh, setNeedsRefresh] = useState(false);
  const busy = Boolean(action);
  const rows = report?.rows ?? [];
  const counts = Object.fromEntries(Object.keys(statuses).map((status) => [status, rows.filter((row) => row.status === status).length]));
  const unresolved = rows.filter((row) => row.status === "CONFLICT" && !decisions[row.number]).length;
  const canConfirm = Boolean(report && !counts.INVALID && !unresolved && !needsRefresh && !busy);
  const shownRows = filter === "ALL" ? rows : rows.filter((row) => row.status === filter);

  useEffect(() => {
    const element = dialog.current;
    const previous = document.activeElement;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    element.showModal();
    return () => { element.close(); document.body.style.overflow = overflow; previous?.focus(); };
  }, []);

  useEffect(() => { table.current?.scrollTo(0, 0); }, [filter, report, exampleOpen]);

  const revealError = (row) => {
    const container = table.current;
    const tr = container?.querySelector(`[data-import-row="${row}"]`);
    const cell = tr?.querySelector("[data-invalid]");
    if (!cell) return;

    const viewport = container.getBoundingClientRect();
    const left = viewport.left + tr.firstElementChild.offsetWidth;
    const right = viewport.right - tr.lastElementChild.offsetWidth;
    const bounds = cell.getBoundingClientRect();
    if (bounds.left < left || bounds.right > right) {
      container.scrollLeft += bounds.left - left;
    }
    requestAnimationFrame(() => cell.focus({ preventScroll: true }));
  };

  const resetPreview = () => {
    setReport(null); setError(""); setDecisions({}); setNeedsRefresh(false); setFilter("ALL");
  };

  const run = async (name, task, fallback) => {
    if (pending.current) return;
    pending.current = true;
    setAction(name);
    setError("");
    try { await task(); }
    catch (failure) { setError(failure.message || fallback); }
    finally { pending.current = false; setAction(""); }
  };

  const preview = (selected) => {
    if (!selected || pending.current) return;
    resetPreview();
    setFile(selected);
    if (!selected.name.toLowerCase().endsWith(`.${format}`)) {
      setError(`Оберіть файл у форматі ${format.toUpperCase()}.`);
      return;
    }
    if (selected.size > 5 * 1024 * 1024) {
      setError("Файл не має перевищувати 5 МБ.");
      return;
    }
    void run("preview", async () => setReport(await adminScheduleApi.previewImport({
      classId, academicYear, file: selected, format,
    })), "Не вдалося перевірити файл.");
  };

  const download = () => void run("template", async () => {
    const url = URL.createObjectURL(await adminScheduleApi.importTemplate(format));
    const link = document.createElement("a");
    link.href = url;
    link.download = `schedule-template.${format}`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }, "Не вдалося завантажити шаблон.");

  const confirm = () => {
    if (!canConfirm) return;
    void run("confirm", async () => {
      try {
        const saved = await adminScheduleApi.confirmImport({ classId, academicYear, file, format, fingerprint: report.fingerprint, decisions });
        setResult(saved);
        if (saved.added || saved.replaced) onImported();
      } catch (failure) {
        if (failure.status === 409) setNeedsRefresh(true);
        throw failure;
      }
    }, "Не вдалося імпортувати розклад.");
  };

  return createPortal(
    <dialog ref={dialog} aria-labelledby={id} aria-busy={busy}
      onCancel={(event) => { event.preventDefault(); if (!pending.current) onClose(); }}
      className="fixed inset-0 m-auto flex max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-[1120px] flex-col overflow-hidden rounded-lg border border-slate-200 bg-white p-0 text-slate-900 shadow-2xl backdrop:bg-slate-950/40">
      <header className="flex shrink-0 items-start justify-between gap-3 border-b border-slate-200 px-5 py-4">
        <div>
          <h2 id={id} className="text-base font-bold">Імпорт розкладу</h2>
          <p className="mt-1 text-xs text-slate-500">{className} · {academicYear} н. р. · CSV / XLSX · до 80 уроків · до 5 МБ</p>
        </div>
        <button type="button" onClick={onClose} disabled={busy} aria-label="Закрити імпорт"
          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-slate-500 transition-colors hover:bg-slate-100 disabled:pointer-events-none disabled:opacity-40"><X className="h-4 w-4" /></button>
      </header>
      <div className="min-h-0 overflow-y-auto px-5 py-4">
        {result ? <div role="status" className="py-10 text-center">
          <CheckCircle2 className="mx-auto h-10 w-10 text-emerald-600" />
          <h3 className="mt-4 text-base font-bold">Імпорт завершено</h3>
          <p className="mt-2 text-sm text-slate-600">
            Додано: {result.added} · Замінено: {result.replaced} · Залишено: {result.kept + result.unchanged}
          </p>
        </div> : <>
          <div className="flex flex-wrap items-end justify-between gap-3">
            <fieldset disabled={busy}>
              <legend className="mb-2 text-xs font-semibold text-slate-600">Формат файлу</legend>
              <div className="inline-flex gap-1 rounded-md border border-slate-200 bg-slate-100 p-1">
                {["xlsx", "csv"].map((value) => <button type="button" key={value} aria-pressed={format === value}
                  className={cn("h-8 min-w-20 rounded px-4 text-xs font-semibold transition-colors disabled:pointer-events-none", format === value ? "bg-white text-slate-900 shadow-xs hover:bg-slate-50" : "text-slate-500 hover:bg-slate-100 hover:text-slate-800")}
                  onClick={() => { if (value !== format) { setFormat(value); setFile(null); resetPreview(); } }}>{value.toUpperCase()}</button>)}
              </div>
            </fieldset>
            <Button type="button" variant="ghost" disabled={busy} onClick={download} className="h-10 rounded-md border border-slate-300 px-3 text-xs hover:border-slate-400 hover:bg-slate-100">
              {action === "template" ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />}
              Завантажити шаблон {format.toUpperCase()}
            </Button>
          </div>
          <input ref={input} type="file" accept={`.${format}`} disabled={busy} className="sr-only" tabIndex={-1}
            aria-label="Файл розкладу" onChange={(event) => preview(event.target.files?.[0])} />
          <button type="button" disabled={busy} onClick={() => { input.current.value = ""; input.current.click(); }}
            onDragOver={(event) => event.preventDefault()} onDrop={(event) => { event.preventDefault(); preview(event.dataTransfer.files?.[0]); }}
            className="mt-4 flex min-h-24 w-full items-center justify-center gap-3 rounded-md border border-dashed border-slate-300 bg-slate-50 px-4 py-4 text-sm text-slate-600 transition-colors hover:border-slate-400 hover:bg-slate-100 disabled:pointer-events-none disabled:opacity-60">
            {action === "preview" ? <Loader2 className="h-5 w-5 shrink-0 animate-spin" /> : <FileUp className="h-5 w-5 shrink-0" />}
            <span className="min-w-0 break-all">{action === "preview" ? "Перевірка файлу…" : file ? `${file.name} · Обрати інший файл` : "Обрати або перетягнути файл"}</span>
          </button>
          {error ? <p role="alert" className="mt-3 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-600">{error}</p> : null}
          {needsRefresh ? <Button type="button" variant="ghost" disabled={busy} onClick={() => preview(file)} className="mt-3 h-10 border border-slate-300 text-xs hover:border-slate-400 hover:bg-slate-100">
            <RefreshCw className="h-4 w-4" />Перевірити повторно
          </Button> : null}
          {action === "preview" ? <p role="status" className="mt-4 text-xs text-slate-500">Перевіряємо поля, дублікати та зайняті слоти…</p> : <>
            <div className="mb-3 mt-4 flex flex-wrap items-center justify-between gap-3">
              {report ? <h3 className="text-sm font-bold">Попередній перегляд</h3> : <button type="button" aria-expanded={exampleOpen}
                aria-controls={`${id}-table`} onClick={() => setExampleOpen((open) => !open)} className="flex items-center gap-2 py-1 text-sm font-bold">
                <FileSpreadsheet className="h-4 w-4 text-slate-500" />Приклад заповнення
                <ChevronDown className={cn("h-4 w-4 text-slate-500 transition-transform", !exampleOpen && "-rotate-90")} />
              </button>}
              {report ? <div role="status" aria-label="Статистика перевірки" className="flex flex-wrap gap-x-4 gap-y-2 text-xs">
                <span className="inline-flex items-center gap-1.5 text-slate-600"><span className="h-1.5 w-1.5 rounded-full bg-slate-400" />Усього <strong className="tabular-nums">{rows.length}</strong></span>
                {Object.entries(statuses).map(([status, { Icon, count, color }]) => (
                  <span key={status} className={cn("inline-flex items-center gap-1.5", counts[status] ? color : "text-slate-500")}>
                    <Icon className="h-3.5 w-3.5" aria-hidden="true" />{count} <strong className="tabular-nums">{counts[status]}</strong>
                  </span>
                ))}
              </div> : null}
            </div>
            {report ? <div role="group" aria-label="Фільтр записів імпорту" className="mb-3 inline-flex flex-wrap gap-1 rounded-md border border-slate-200 bg-slate-100 p-1">
              {[["ALL", "Усі"], ["CONFLICT", statuses.CONFLICT.count], ["INVALID", statuses.INVALID.count]].map(([status, label]) => (
                <button type="button" key={status} aria-pressed={filter === status} disabled={busy} onClick={() => setFilter(status)}
                  className={cn("h-8 rounded px-3 text-xs font-semibold transition-colors disabled:pointer-events-none", filter === status ? "bg-white text-slate-900 shadow-xs hover:bg-slate-50" : "text-slate-500 hover:bg-slate-100 hover:text-slate-800")}>{label}</button>
              ))}
            </div> : null}
            {report || exampleOpen ? <div ref={table} id={`${id}-table`} data-import-scroll tabIndex={0} aria-label={report ? "Дані імпорту" : "Приклад заповнення"}
              className={cn("rounded-md border border-slate-200", report ? "max-h-[300px] overflow-auto" : "overflow-x-auto")}>
              <table className={cn("w-full table-fixed border-separate border-spacing-0 text-left text-xs", report ? "min-w-[830px]" : "min-w-[650px]")}>
                <colgroup><col className="w-16" /><col className="w-[76px]" /><col className="w-[115px]" /><col className="w-[100px]" />
                  <col /><col /><col className="w-[88px]" />{report ? <col className="w-28" /> : null}</colgroup>
                <thead className="sticky top-0 z-20 bg-slate-50 text-slate-500"><tr>
                  <th scope="col" className="sticky left-0 z-30 border-b border-r border-slate-200 bg-slate-50 px-3 py-2.5">Рядок</th>
                  {columns.map(([key, label, templateLabel]) => <th scope="col" key={key} className="border-b border-slate-200 bg-slate-50 px-3 py-2.5 font-semibold">{report ? label : templateLabel || label}</th>)}
                  {report ? <th scope="col" className="sticky right-0 z-30 border-b border-l border-slate-200 bg-slate-50 px-3 py-2.5">Перевірка</th> : null}
                </tr></thead>
                <tbody>
                  {(report ? shownRows : examples.map((values, index) => ({ number: index + 2, values }))).map((row) => (
                    <PreviewRow key={row.number} row={row} dialog={dialog} decision={decisions[row.number]} disabled={busy || needsRefresh}
                      onReveal={() => revealError(row.number)} onDecision={(value) => setDecisions((current) => {
                        const next = { ...current };
                        if (value) next[row.number] = value;
                        else delete next[row.number];
                        return next;
                      })} />
                  ))}
                </tbody>
              </table>
              {report && !shownRows.length ? <p className="sticky left-0 w-full px-4 py-6 text-center text-xs text-slate-500">Немає рядків для цього фільтра.</p> : null}
            </div> : null}
            {report ? counts.INVALID ? <p className="mt-3 text-xs leading-5 text-slate-500">Виправте файл і завантажте його повторно.</p> : null : exampleOpen ? (
              <p className="mt-3 text-xs leading-5 text-slate-500">
                Вкажіть тиждень 1 або 2, день з понеділка до п’ятниці та номер уроку від 1 до 8. Замініть приклади назвами предметів і логінами вчителів.
                {format === "csv" ? " CSV: UTF-8, кома або крапка з комою." : " XLSX: один аркуш."}
              </p>
            ) : null}
          </>}
        </>}
      </div>
      <footer className="flex shrink-0 flex-wrap items-center justify-end gap-2 border-t border-slate-200 px-5 py-4">
        {!result && unresolved ? <span className="mr-auto text-xs font-medium text-amber-800">Без рішення: {unresolved}</span> : null}
        <Button type="button" variant="ghost" disabled={busy} onClick={onClose} className="h-10 rounded-md border border-slate-300 px-4 text-xs hover:border-slate-400 hover:bg-slate-100">{result ? "Готово" : "Скасувати"}</Button>
        {!result ? <Button type="button" disabled={!canConfirm} onClick={confirm} className="h-10 rounded-md px-4 text-xs">
          {action === "confirm" ? <Loader2 className="h-4 w-4 animate-spin" /> : <FileUp className="h-4 w-4" />}
          {action === "confirm" ? "Імпортування…" : `Імпортувати${report ? ` (${rows.length})` : ""}`}
        </Button> : null}
      </footer>
    </dialog>, document.body,
  );
}

function PreviewRow({ row, dialog, decision, onDecision, onReveal, disabled }) {
  const info = statuses[row.status];
  const { current, proposed } = row;
  const time = (lesson) => lesson ? `${lesson.startTime}–${lesson.endTime}` : "";
  const comparing = row.status === "CONFLICT";
  const details = {
    LESSON: { secondary: time(proposed), previous: comparing && time(current) !== time(proposed) ? time(current) : null },
    SUBJECT: { previous: comparing && current.subjectId !== proposed.subjectId ? current.subject : null },
    TEACHER: { secondary: proposed ? row.values[4] : null, previous: comparing && current.teacherId !== proposed.teacherId ? current.teacher : null },
    ROOM: { previous: comparing && current.room !== proposed.room ? current.room || "—" : null },
  };
  const normalized = { SUBJECT: proposed?.subject, TEACHER: proposed?.teacher, ROOM: proposed?.room };
  return <tr data-import-row={row.number}>
    <th scope="row" className="sticky left-0 z-10 border-b border-r border-slate-200 bg-white px-3 py-2.5 font-normal text-slate-500">{row.number}</th>
    {columns.map(([key], index) => (
      <ImportCell key={key} value={normalized[key] ?? row.values[index]} {...details[key]} errors={row.cellErrors?.[key]} dialog={dialog} />
    ))}
    {info ? <td className="sticky right-0 z-10 border-b border-l border-slate-200 bg-white px-2.5 py-2.5">
      {row.status === "INVALID" ? (
        <button type="button" onClick={onReveal} aria-label={`Помилка в рядку ${row.number}`}
          title={!row.cellErrors ? row.errors?.join(" ") : undefined}
          className="inline-flex items-center gap-1.5 font-semibold text-red-600 underline-offset-2 hover:underline">
          <AlertCircle className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />Помилка
        </button>
      ) : <span className={cn("inline-flex items-center gap-1.5 whitespace-nowrap", info.color)}>
        <info.Icon className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />{info.label}
      </span>}
      {comparing ? <select disabled={disabled} value={decision ?? ""} onChange={(event) => onDecision(event.target.value)}
        aria-label={`Рішення для рядка ${row.number}`}
        className="mt-1.5 block h-7 w-full rounded border border-slate-300 bg-white px-1 text-xs text-slate-700 disabled:opacity-50">
        <option value="">Оберіть</option>
        <option value="keep">Залишити</option>
        <option value="replace">Замінити</option>
      </select> : null}
    </td> : null}
  </tr>;
}

function ImportCell({ value, secondary, previous, errors, dialog }) {
  const cell = useRef(null);
  const tooltip = useRef(null);
  const delay = useRef(null);
  const id = useId();
  const [open, setOpen] = useState(false);
  const [position, setPosition] = useState(null);
  const invalid = Boolean(errors?.length);
  const hide = () => { clearTimeout(delay.current); setOpen(false); setPosition(null); };

  useEffect(() => () => clearTimeout(delay.current), []);
  useLayoutEffect(() => {
    if (!open || !cell.current || !tooltip.current) return;
    const update = () => {
      const anchor = cell.current.getBoundingClientRect();
      const scroller = cell.current.closest("[data-import-scroll]");
      const viewport = scroller.getBoundingClientRect();
      const row = cell.current.parentElement;
      // Ignore parts of the anchor hidden behind the sticky columns.
      const start = Math.max(anchor.left, viewport.left + row.firstElementChild.offsetWidth);
      const end = Math.min(anchor.right, viewport.right - row.lastElementChild.offsetWidth);
      if (start >= end || anchor.bottom <= viewport.top || anchor.top >= viewport.bottom) {
        setPosition(null);
        return;
      }
      const center = (start + end) / 2;
      const tip = tooltip.current.getBoundingClientRect();
      const left = Math.max(12, Math.min(center - tip.width / 2, window.innerWidth - tip.width - 12));
      const above = anchor.top >= tip.height + 20;
      const top = above ? anchor.top - tip.height - 8 : Math.min(anchor.bottom + 8, window.innerHeight - tip.height - 12);
      setPosition({ left, top: Math.max(12, top), above, arrow: Math.max(8, Math.min(center - left, tip.width - 8)) });
    };
    update();
    document.addEventListener("scroll", update, true);
    window.addEventListener("resize", update);
    return () => { document.removeEventListener("scroll", update, true); window.removeEventListener("resize", update); };
  }, [open]);

  return <td ref={cell} data-invalid={invalid || undefined} tabIndex={invalid ? 0 : undefined} aria-describedby={open ? id : undefined}
    onMouseEnter={() => { if (invalid) delay.current = setTimeout(() => setOpen(true), 300); }}
    onMouseLeave={hide} onFocus={() => { if (invalid) setOpen(true); }} onBlur={hide}
    onKeyDown={(event) => { if (event.key === "Escape" && open) { event.preventDefault(); event.stopPropagation(); hide(); } }}
    className={cn("border-b border-slate-200 px-3 py-2.5 text-slate-700 [overflow-wrap:anywhere]",
      invalid && "bg-red-50 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-red-400")}>
    {value || "—"}
    {secondary ? <span className="mt-0.5 block text-[0.68rem] leading-4 text-slate-500">{secondary}</span> : null}
    {previous ? <span className="mt-0.5 block text-[0.68rem] leading-4 text-slate-500">Було: {previous}</span> : null}
    {open && dialog.current ? createPortal(
      <span ref={tooltip} id={id} role="tooltip"
        className="pointer-events-none fixed z-50 w-max max-w-[min(280px,calc(100vw-24px))] rounded-md bg-[#17212b] px-3 py-2.5 text-xs leading-4 text-white shadow-lg"
        style={{ left: position?.left ?? 0, top: position?.top ?? 0, visibility: position ? "visible" : "hidden" }}>
        {errors.join(" ")}
        {position ? <span aria-hidden="true" className={cn("absolute size-2 -translate-x-1/2 rotate-45 bg-[#17212b]", position.above ? "-bottom-1" : "-top-1")} style={{ left: position.arrow }} /> : null}
      </span>, dialog.current,
    ) : null}
  </td>;
}
