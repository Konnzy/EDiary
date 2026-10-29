import { useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Loader2, Trash2, X } from "lucide-react";
import { Button } from "@/components/ui/button.jsx";

export function NotificationClearDialog({ admin = false, onConfirm, onClose }) {
  const dialog = useRef(null);
  const cancelButton = useRef(null);
  const pending = useRef(false);
  const titleId = useId();
  const descriptionId = useId();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    const element = dialog.current;
    const previousFocus = document.activeElement;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    element.showModal();
    cancelButton.current?.focus();
    return () => {
      element.close();
      document.body.style.overflow = overflow;
      previousFocus?.focus();
    };
  }, []);

  const confirm = async () => {
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    setError("");
    try {
      await onConfirm();
      onClose();
    } catch (requestError) {
      setError(requestError.message || "Не вдалося приховати сповіщення. Спробуйте ще раз.");
      pending.current = false;
      setBusy(false);
    }
  };

  return createPortal(<dialog ref={dialog} tabIndex={-1} role="alertdialog" aria-labelledby={titleId} aria-describedby={descriptionId} aria-busy={busy}
    onCancel={(event) => { event.preventDefault(); if (!pending.current) onClose(); }}
    onKeyDown={(event) => {
      if (event.key !== "Tab") return;
      const buttons = [...dialog.current.querySelectorAll("button:not(:disabled)")];
      const first = buttons[0], last = buttons.at(-1);
      if (!first || (event.shiftKey && document.activeElement === first) || (!event.shiftKey && document.activeElement === last)) {
        event.preventDefault();
        (event.shiftKey ? last : first)?.focus();
      }
    }}
    className="fixed inset-0 m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-[440px] overflow-y-auto rounded-lg border border-slate-200 bg-white p-5 text-slate-950 shadow-2xl backdrop:bg-slate-950/35 sm:p-6">
    <div className="flex items-start justify-between gap-3">
      <h2 id={titleId} className="text-base font-bold">Приховати всі сповіщення?</h2>
      <button type="button" onClick={onClose} disabled={busy} aria-label="Закрити підтвердження" className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 disabled:opacity-50"><X className="h-4 w-4" /></button>
    </div>
    <div id={descriptionId} className="mt-3 text-sm leading-6 text-slate-600">
      <p>Сповіщення зникнуть зі списку. Самі повідомлення, оцінки та інші дані залишаться без змін.</p>
      {admin ? <p className="mt-2">Заявки на розгляді залишаться у списку сповіщень.</p> : null}
    </div>
    {error ? <p role="alert" className="mt-4 text-sm text-rose-700">{error}</p> : null}
    <div className="mt-6 flex flex-wrap justify-end gap-2">
      <Button ref={cancelButton} type="button" variant="ghost" disabled={busy} onClick={onClose} className="h-9 rounded-md border border-slate-200 text-xs">Скасувати</Button>
      <Button type="button" disabled={busy} onClick={() => void confirm()} className="h-9 rounded-md text-xs">
        {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}Приховати всі
      </Button>
    </div>
  </dialog>, document.body);
}
