import { useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { ImagePlus, Loader2, Minus, Plus, X } from "lucide-react";
import { Button } from "@/components/ui/button.jsx";
import { encodeAvatar, getAvatarCrop, readAvatarDimensions, validateAvatarDimensions } from "./avatarCrop.js";
import { AVATAR_TYPES, validateAvatarFile } from "./profileModel.js";

const CENTER = { x: 0, y: 0 };
const MIN_ZOOM = 1;
const MAX_ZOOM = 3;
const secondaryButton = "border border-slate-200";

export function AvatarDialog({ onClose, onSave }) {
  const dialog = useRef(null);
  const chooseButton = useRef(null);
  const fileInput = useRef(null);
  const preview = useRef(null);
  const objectUrl = useRef("");
  const selection = useRef(0);
  const saving = useRef(false);
  const drag = useRef(null);
  const titleId = useId();
  const hintId = useId();
  const zoomId = useId();
  const [photo, setPhoto] = useState(null);
  const [zoom, setZoom] = useState(MIN_ZOOM);
  const [position, setPosition] = useState(CENTER);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const crop = photo && getAvatarCrop(photo.width, photo.height, zoom, position);

  useEffect(() => {
    const element = dialog.current;
    const previousFocus = document.activeElement;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    element.showModal();
    chooseButton.current.focus();
    return () => {
      selection.current++;
      URL.revokeObjectURL(objectUrl.current);
      element.close();
      document.body.style.overflow = overflow;
      previousFocus?.focus();
    };
  }, []);

  const choosePhoto = async (event) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    const request = ++selection.current;
    setError("");
    setLoading(true);
    let url;
    try {
      const fileError = validateAvatarFile(file);
      if (fileError) throw new Error(fileError);
      const dimensions = readAvatarDimensions(await file.arrayBuffer(), file.type);
      const dimensionError = validateAvatarDimensions(...dimensions);
      if (dimensionError) throw new Error(dimensionError);
      if (request !== selection.current) return;

      url = URL.createObjectURL(file);
      const image = new Image();
      image.src = url;
      await image.decode();
      if (request !== selection.current) return;
      const decodedError = validateAvatarDimensions(image.naturalWidth, image.naturalHeight);
      if (decodedError) throw new Error(decodedError);
      URL.revokeObjectURL(objectUrl.current);
      objectUrl.current = url;
      url = undefined;
      setPhoto({ image, width: image.naturalWidth, height: image.naturalHeight });
      setZoom(MIN_ZOOM);
      setPosition(CENTER);
    } catch (failure) {
      if (request === selection.current) {
        setError(failure.name === "EncodingError" ? "Файл не вдалося відкрити як зображення." : failure.message);
      }
    } finally {
      if (url) URL.revokeObjectURL(url);
      if (request === selection.current) setLoading(false);
    }
  };

  const changeZoom = (value) => {
    const next = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, Math.round(value * 100) / 100));
    const ratio = next / zoom;
    const adjusted = getAvatarCrop(photo.width, photo.height, next, { x: crop.x * ratio, y: crop.y * ratio });
    setPosition({ x: adjusted.x, y: adjusted.y });
    setZoom(next);
  };

  const movePhoto = (dx, dy) => {
    setPosition((previous) => {
      const { x, y } = getAvatarCrop(photo.width, photo.height, zoom, { x: previous.x + dx, y: previous.y + dy });
      return { x, y };
    });
  };

  const save = async () => {
    if (!crop || loading || saving.current) return;
    saving.current = true;
    setBusy(true);
    setError("");
    try {
      await onSave(await encodeAvatar(photo.image, crop));
      onClose();
    } catch (failure) {
      setError(failure.message || "Не вдалося зберегти фото. Спробуйте ще раз.");
      saving.current = false;
      setBusy(false);
    }
  };

  return createPortal(
    <dialog ref={dialog} aria-labelledby={titleId} aria-describedby={hintId} aria-busy={busy || loading}
      onCancel={(event) => { event.preventDefault(); if (!saving.current) onClose(); }}
      className="fixed inset-0 m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-[460px] overflow-y-auto rounded-lg border border-slate-200 bg-white p-5 text-slate-900 shadow-2xl backdrop:bg-slate-950/50 sm:p-6">
      <div className="flex items-center justify-between gap-3">
        <h2 id={titleId} className="text-lg font-bold">Фото профілю</h2>
        <Button type="button" variant="ghost" size="icon" onClick={onClose} disabled={busy} aria-label="Закрити">
          <X aria-hidden="true" />
        </Button>
      </div>
      <p id={hintId} className="mt-1 text-sm text-slate-500">JPG або PNG, до 5 МБ і 20 мегапікселів.</p>
      <input ref={fileInput} type="file" accept={AVATAR_TYPES.join(",")} onChange={choosePhoto} hidden />
      <Button ref={chooseButton} type="button" variant="ghost" className={`mt-4 ${secondaryButton}`} disabled={busy}
        onClick={() => fileInput.current.click()}>
        <ImagePlus aria-hidden="true" />{photo ? "Вибрати інше фото" : "Вибрати фото"}
      </Button>
      {loading && <p role="status" className="mt-3 text-sm text-slate-500">Підготовка фото…</p>}

      {photo && (
        <>
          <p id={`${hintId}-crop`} className="mt-4 text-sm text-slate-500">
            Перетягуйте фото або пересувайте його стрілками на клавіатурі.
          </p>
          <div ref={preview} role="group" tabIndex={0} aria-label="Кадрування фото" aria-describedby={`${hintId}-crop`}
            className="relative mx-auto mt-3 aspect-square w-full max-w-[280px] touch-none select-none overflow-hidden rounded-md bg-white focus-visible:ring-2 focus-visible:ring-slate-500"
            onPointerDown={(event) => {
              if (busy || loading || event.button !== 0 || !event.isPrimary) return;
              const side = preview.current.getBoundingClientRect().width;
              drag.current = { id: event.pointerId, x: event.clientX, y: event.clientY, side };
              event.currentTarget.setPointerCapture(event.pointerId);
            }}
            onPointerMove={(event) => {
              const start = drag.current;
              if (busy || loading || start?.id !== event.pointerId) return;
              movePhoto((event.clientX - start.x) / start.side, (event.clientY - start.y) / start.side);
              start.x = event.clientX;
              start.y = event.clientY;
            }}
            onLostPointerCapture={() => { drag.current = null; }}
            onKeyDown={(event) => {
              const directions = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] };
              const direction = directions[event.key];
              if (!direction || busy || loading) return;
              event.preventDefault();
              const step = event.shiftKey ? 0.08 : 0.02;
              movePhoto(direction[0] * step, direction[1] * step);
            }}>
            <img src={photo.image.src} alt="" draggable={false}
              className="pointer-events-none absolute max-w-none"
              style={{ width: `${crop.scaledWidth * 100}%`, height: `${crop.scaledHeight * 100}%`,
                left: `${(0.5 + crop.x - crop.scaledWidth / 2) * 100}%`,
                top: `${(0.5 + crop.y - crop.scaledHeight / 2) * 100}%` }} />
            <div aria-hidden="true" className="pointer-events-none absolute inset-0 rounded-full border border-white/80 shadow-[0_0_0_300px_rgba(15,23,42,0.65)]" />
          </div>
          <label htmlFor={zoomId} className="mt-4 flex justify-between text-sm font-semibold">
            Масштаб <span>{Math.round(zoom * 100)}%</span>
          </label>
          <div className="mt-2 flex items-center gap-3">
            <Button type="button" variant="ghost" size="icon" className={secondaryButton} aria-label="Зменшити масштаб"
              disabled={busy || loading || zoom <= MIN_ZOOM} onClick={() => changeZoom(zoom - 0.1)}><Minus aria-hidden="true" /></Button>
            <input id={zoomId} type="range" min={MIN_ZOOM} max={MAX_ZOOM} step={0.01} value={zoom}
              aria-valuetext={`${Math.round(zoom * 100)}%`} disabled={busy || loading}
              onChange={(event) => changeZoom(event.target.valueAsNumber)} className="min-w-0 flex-1 accent-slate-600" />
            <Button type="button" variant="ghost" size="icon" className={secondaryButton} aria-label="Збільшити масштаб"
              disabled={busy || loading || zoom >= MAX_ZOOM} onClick={() => changeZoom(zoom + 0.1)}><Plus aria-hidden="true" /></Button>
          </div>
        </>
      )}
      {error && <p role="alert" className="mt-4 text-sm font-medium text-red-600">{error}</p>}
      <div className="mt-5 flex flex-wrap justify-end gap-2">
        <Button type="button" variant="ghost" className={secondaryButton} disabled={busy} onClick={onClose}>Скасувати</Button>
        <Button type="button" disabled={!photo || loading || busy} onClick={save}>
          {busy && <Loader2 className="animate-spin" aria-hidden="true" />}{busy ? "Збереження…" : "Зберегти"}
        </Button>
      </div>
    </dialog>, document.body,
  );
}
