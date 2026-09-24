import { createContext, useContext } from "react";
import { userProfileApi } from "@/lib/api.js";
import { formatShortName, profileInitials, roleLabels } from "@/lib/userIdentity.js";

export const CurrentUserContext = createContext(null);

export function CurrentUserBadge() {
  const currentUser = useContext(CurrentUserContext);
  if (!currentUser) return null;

  const { session, profile } = currentUser;
  const shortName = profile ? formatShortName(profile) : session.login;
  const initials = profile ? profileInitials(profile) : session.login.slice(0, 2).toUpperCase();

  return (
    <a href={`#${session.role.toLowerCase()}/profile`} aria-label="Відкрити профіль" className="hidden min-w-0 items-center gap-3 rounded-md bg-slate-50 px-3 py-1.5 transition-colors hover:bg-slate-100 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-slate-500 focus-visible:ring-offset-2 sm:flex">
      <div className="min-w-0 text-right leading-tight">
        <div className="max-w-[190px] truncate text-[0.78rem] font-bold text-slate-800">{shortName}</div>
        <div className="text-[0.7rem] text-slate-500">{roleLabels[session.role]}</div>
      </div>
      {profile?.avatarVersion != null
        ? <img src={userProfileApi.avatarUrl(profile.avatarVersion)} alt="Фото профілю" className="h-9 w-9 shrink-0 rounded-full border border-slate-200 object-cover" />
        : <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-slate-200 bg-white text-xs font-bold text-slate-700" aria-label="Фото профілю не встановлено">{initials}</span>}
    </a>
  );
}
