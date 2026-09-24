import { useEffect, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { ShieldAlert, X } from "lucide-react";
import {
  consumeRouteAccessNotice,
  ROUTE_ACCESS_NOTICE_EVENT,
} from "@/lib/routeAccessNotice.js";

export function RouteAccessToast() {
  const [notice, setNotice] = useState(null);

  useEffect(() => {
    const readNotice = () => {
      const nextNotice = consumeRouteAccessNotice();
      if (nextNotice) setNotice(nextNotice);
    };

    readNotice();
    window.addEventListener(ROUTE_ACCESS_NOTICE_EVENT, readNotice);
    window.addEventListener("hashchange", readNotice);

    return () => {
      window.removeEventListener(ROUTE_ACCESS_NOTICE_EVENT, readNotice);
      window.removeEventListener("hashchange", readNotice);
    };
  }, []);

  useEffect(() => {
    if (!notice) return undefined;

    const timeoutId = window.setTimeout(() => setNotice(null), 5000);
    return () => window.clearTimeout(timeoutId);
  }, [notice]);

  return (
    <div className="pointer-events-none fixed inset-x-4 top-4 z-[70] flex justify-end sm:left-auto sm:right-5 sm:top-5 sm:w-[390px]" aria-live="polite" aria-atomic="true">
      <AnimatePresence>
        {notice ? (
          <motion.div
            key={notice.type}
            role="status"
            initial={{ opacity: 0, y: -12, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -8, scale: 0.98 }}
            transition={{ duration: 0.2, ease: "easeOut" }}
            className="pointer-events-auto flex w-full items-start gap-3 rounded-md border border-amber-300 bg-white p-4 text-slate-700 shadow-xl"
          >
            <div className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-amber-50 text-amber-700">
              <ShieldAlert className="h-4 w-4" aria-hidden="true" />
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-[0.78rem] font-bold text-slate-900">Доступ обмежено</p>
              <p className="mt-1 text-[0.76rem] leading-5 text-slate-600">{notice.message}</p>
            </div>
            <button
              type="button"
              onClick={() => setNotice(null)}
              className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-slate-400"
              aria-label="Закрити повідомлення"
            >
              <X className="h-4 w-4" aria-hidden="true" />
            </button>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  );
}
