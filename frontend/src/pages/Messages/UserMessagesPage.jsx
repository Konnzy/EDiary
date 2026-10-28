import { GraduationCap, LogOut } from "lucide-react";
import { AppFooter } from "@/components/AppFooter.jsx";
import { LearnerNavigation } from "@/components/LearnerNavigation.jsx";
import { NotificationButton } from "@/components/NotificationButton.jsx";
import { TeacherNavigation } from "@/components/TeacherNavigation.jsx";
import { CurrentUserBadge } from "@/lib/currentUser.jsx";
import { authApi } from "@/lib/api.js";
import { Mailbox } from "./Mailbox.jsx";

export function UserMessagesPage({ role, initialRecipient="", initialRecipientId="", initialMessageId="" }) {
  const homeRoute = role.toLowerCase();
  const logout = async () => {
    try { await authApi.logout(); } catch { /* Session may already be expired. */ }
    window.location.hash="login";
  };
  const navigation = compact => role === "TEACHER"
    ? <TeacherNavigation activeRoute="teacher/messages" compact={compact} />
    : <LearnerNavigation role={role} activeRoute={`${homeRoute}/messages`} compact={compact} />;
  return <div className="flex min-h-screen flex-col bg-slate-50 text-slate-800">
    <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-slate-200/80 bg-white px-4 sm:px-5 lg:px-6">
      <a href={`#${homeRoute}`} className="inline-flex min-w-0 items-center gap-2 text-slate-900" aria-label="Електронний щоденник - головна"><GraduationCap className="h-7 w-7 shrink-0 sm:h-8 sm:w-8" strokeWidth={2.4} /><span className="max-w-[150px] truncate text-[0.92rem] font-bold min-[420px]:max-w-none sm:text-[1.05rem]">Електронний щоденник</span></a>
      <div className="flex items-center gap-1.5 sm:gap-4"><NotificationButton /><CurrentUserBadge /><button type="button" onClick={logout} className="inline-flex h-10 items-center gap-2 rounded-md px-3 text-[0.82rem] font-semibold text-slate-500 hover:bg-slate-100"><LogOut className="h-4 w-4" /><span className="hidden sm:inline">Вийти</span></button></div>
    </header>
    {navigation(true)}
    <div className="flex flex-1">{navigation(false)}
      <div className="flex min-w-0 flex-1 flex-col lg:border-l lg:border-slate-200/80">
        <main className="min-w-0 flex-1 px-4 py-5 sm:px-6 lg:px-8 lg:py-6"><div className="mx-auto w-full max-w-[1280px] 2xl:max-w-[1500px]">
          <header className="mb-6"><h1 className="text-[1.65rem] font-bold text-slate-900">Повідомлення</h1><p className="mt-2 text-[0.9rem] text-slate-500">Листування з учасниками навчального процесу</p></header>
          <Mailbox initialRecipient={initialRecipient} initialRecipientId={initialRecipientId} initialMessageId={initialMessageId} />
        </div></main><AppFooter />
      </div>
    </div>
  </div>;
}
