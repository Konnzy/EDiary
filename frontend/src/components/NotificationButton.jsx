import { Bell } from "lucide-react";

export function NotificationButton({ hasUnread = false }) {
  return (
    <button
      type="button"
      className="relative flex h-9 w-9 items-center justify-center rounded-md text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 sm:h-10 sm:w-10"
      aria-label={hasUnread ? "Повідомлення: є непрочитані" : "Повідомлення"}
    >
      <Bell className="h-5 w-5 text-slate-600" strokeWidth={2.1} aria-hidden="true" />
      {hasUnread ? (
        <span className="absolute right-2.5 top-2.5 h-2 w-2 rounded-full bg-slate-900" />
      ) : null}
    </button>
  );
}
