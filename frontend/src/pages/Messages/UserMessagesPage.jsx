import { useState } from "react";
import {
  BookOpen,
  CalendarDays,
  GraduationCap,
  Home,
  Inbox,
  LogOut,
  Mail,
  PenLine,
  Send,
  Star,
  User,
} from "lucide-react";
import { AppFooter } from "@/components/AppFooter.jsx";
import { LearnerNavigation } from "@/components/LearnerNavigation.jsx";
import { NotificationButton } from "@/components/NotificationButton.jsx";
import { CurrentUserBadge } from "@/lib/currentUser.jsx";
import { authApi } from "@/lib/api.js";
import { teacherNavigationItems } from "@/lib/teacherNavigation.js";
import { TeacherNavigation } from "@/components/TeacherNavigation.jsx";
import { cn } from "@/lib/utils.js";

const roleConfig = {
  STUDENT: {
    homeRoute: "student",
    navigation: [
      { label: "Головна", icon: Home, route: "student" },
      { label: "Розклад", icon: CalendarDays, route: "student/schedule" },
      { label: "Предмети", icon: BookOpen, route: "student/subjects" },
      { label: "Повідомлення", shortLabel: "Повідомл.", icon: Mail, route: "student/messages" },
      { label: "Профіль", icon: User, route: "student/profile" },
    ],
  },
  PARENT: {
    homeRoute: "parent",
    navigation: [
      { label: "Головна", icon: Home, route: "parent" },
      { label: "Розклад", icon: CalendarDays, route: "parent/schedule" },
      { label: "Предмети", icon: BookOpen, route: "parent/subjects" },
      { label: "Повідомлення", shortLabel: "Повідомл.", icon: Mail, route: "parent/messages" },
      { label: "Профіль", icon: User, route: "parent/profile" },
    ],
  },
  TEACHER: {
    homeRoute: "teacher",
    navigation: teacherNavigationItems,
  },
};

const tabs = [
  { id: "inbox", label: "Вхідні", icon: Inbox },
  { id: "sent", label: "Вихідні", icon: Send },
  { id: "important", label: "Важливі", icon: Star },
  { id: "compose", label: "Нове повідомлення", shortLabel: "Нове", icon: PenLine },
];

const emptyStates = {
  inbox: { icon: Inbox, title: "Вхідних повідомлень поки немає" },
  sent: { icon: Send, title: "Вихідних повідомлень поки немає" },
  important: { icon: Star, title: "Важливих повідомлень поки немає" },
};

export function UserMessagesPage({ role, initialRecipient = "", initialRecipientId = "" }) {
  const config = roleConfig[role];
  const [activeTab, setActiveTab] = useState(initialRecipient ? "compose" : "inbox");
  const [recipient, setRecipient] = useState(initialRecipient);
  const [recipientId, setRecipientId] = useState(initialRecipientId);
  const [subject, setSubject] = useState("");
  const [message, setMessage] = useState("");

  return (
    <div className="flex min-h-screen flex-col bg-slate-50 text-slate-800">
      <TopBar homeRoute={config.homeRoute} />
      <Navigation config={config} compact />

      <div className="flex flex-1">
        <Navigation config={config} />
        <div className="flex min-w-0 flex-1 flex-col lg:border-l lg:border-slate-200/80">
          <main className="min-w-0 flex-1 px-4 py-5 pb-8 sm:px-6 lg:px-8 lg:py-6">
            <div className="mx-auto w-full max-w-[1280px] 2xl:max-w-[1500px] min-[1920px]:max-w-[1680px]">
              <header className="mb-5 sm:mb-6">
                <h1 className="text-[1.45rem] font-bold leading-tight text-slate-900 sm:text-[1.65rem]">Повідомлення</h1>
                <p className="mt-2 text-[0.9rem] text-slate-500">Листування з учасниками навчального процесу</p>
              </header>

              <section className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)]" aria-label="Повідомлення">
                <div role="tablist" aria-label="Розділи повідомлень" className="grid grid-cols-4 border-b border-slate-200 px-2 sm:flex sm:gap-1 sm:px-4">
                  {tabs.map((tab, index) => {
                    const Icon = tab.icon;
                    const selected = activeTab === tab.id;
                    return (
                      <button
                        key={tab.id}
                        id={`messages-tab-${tab.id}`}
                        type="button"
                        role="tab"
                        aria-selected={selected}
                        aria-controls="messages-panel"
                        tabIndex={selected ? 0 : -1}
                        onClick={() => setActiveTab(tab.id)}
                        onKeyDown={(event) => {
                          const nextIndex = event.key === "ArrowRight" ? (index + 1) % tabs.length
                            : event.key === "ArrowLeft" ? (index + tabs.length - 1) % tabs.length
                              : event.key === "Home" ? 0 : event.key === "End" ? tabs.length - 1 : -1;
                          if (nextIndex < 0) return;
                          event.preventDefault();
                          setActiveTab(tabs[nextIndex].id);
                          document.getElementById(`messages-tab-${tabs[nextIndex].id}`)?.focus();
                        }}
                        className={cn(
                          "flex min-w-0 items-center justify-center gap-1.5 border-b-2 px-1 py-3 text-[0.72rem] font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-slate-500 sm:px-4 sm:text-[0.82rem]",
                          selected ? "border-slate-800 text-slate-900" : "border-transparent text-slate-500 hover:text-slate-800",
                        )}
                      >
                        <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
                        <span className="truncate sm:hidden">{tab.shortLabel || tab.label}</span>
                        <span className="hidden sm:inline">{tab.label}</span>
                      </button>
                    );
                  })}
                </div>

                <div id="messages-panel" role="tabpanel" aria-labelledby={`messages-tab-${activeTab}`}>
                  {activeTab === "compose" ? (
                    <form className="max-w-[760px] space-y-5 p-4 sm:p-6" onSubmit={(event) => event.preventDefault()}>
                      <Field label="ПІБ одержувача" htmlFor="message-recipient">
                        <input type="hidden" name="recipientId" value={recipientId} />
                        <input id="message-recipient" name="recipient" type="text" autoComplete="off" value={recipient} onChange={(event) => { setRecipient(event.target.value); setRecipientId(""); }} placeholder="Прізвище Ім’я По батькові" className={fieldClasses} />
                      </Field>
                      <Field label="Тема повідомлення" htmlFor="message-subject">
                        <input id="message-subject" name="subject" type="text" maxLength={150} value={subject} onChange={(event) => setSubject(event.target.value)} placeholder="Вкажіть тему" className={fieldClasses} />
                      </Field>
                      <Field label="Текст повідомлення" htmlFor="message-text">
                        <textarea id="message-text" name="message" maxLength={1000} value={message} onChange={(event) => setMessage(event.target.value)} placeholder="Напишіть повідомлення" className={cn(fieldClasses, "min-h-[180px] resize-y py-3 leading-5")} />
                      </Field>
                      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 pt-4">
                        <p className="text-[0.76rem] text-slate-500">Надсилання стане доступним після підключення модуля повідомлень.</p>
                        <button type="submit" disabled title="Надсилання ще недоступне" className="inline-flex h-10 items-center gap-2 rounded-md bg-slate-800 px-4 text-[0.8rem] font-semibold text-white opacity-50 disabled:cursor-not-allowed">
                          <Send className="h-4 w-4" aria-hidden="true" />Надіслати
                        </button>
                      </div>
                    </form>
                  ) : <EmptyMailbox state={emptyStates[activeTab]} onCompose={() => setActiveTab("compose")} />}
                </div>
              </section>
            </div>
          </main>
          <AppFooter />
        </div>
      </div>
    </div>
  );
}

const fieldClasses = "block h-10 w-full rounded-md border border-slate-300 bg-white px-3 text-[0.82rem] text-slate-800 outline-hidden placeholder:text-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200";

function Field({ label, htmlFor, children }) {
  return <div className="space-y-2"><label htmlFor={htmlFor} className="block text-[0.78rem] font-semibold text-slate-700">{label}</label>{children}</div>;
}

function EmptyMailbox({ state, onCompose }) {
  const Icon = state.icon;
  return (
    <div className="flex min-h-[340px] flex-col items-center justify-center px-5 py-12 text-center">
      <Icon className="h-8 w-8 text-slate-400" strokeWidth={1.7} aria-hidden="true" />
      <h2 className="mt-4 text-[0.95rem] font-bold text-slate-900">{state.title}</h2>
      <p className="mt-2 max-w-sm text-[0.8rem] leading-5 text-slate-500">Листи з’являться тут після підключення модуля повідомлень.</p>
      <button type="button" onClick={onCompose} className="mt-5 inline-flex h-9 items-center gap-2 rounded-md border border-slate-300 px-3 text-[0.8rem] font-semibold text-slate-700 transition-colors hover:bg-slate-50">
        <PenLine className="h-4 w-4" aria-hidden="true" />Написати повідомлення
      </button>
    </div>
  );
}

function TopBar({ homeRoute }) {
  const logout = async () => {
    try { await authApi.logout(); } catch { /* The session may already be expired. */ }
    window.location.hash = "login";
  };
  return (
    <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-slate-200/80 bg-white px-4 sm:px-5 lg:px-6">
      <a href={`#${homeRoute}`} className="inline-flex min-w-0 items-center gap-2 text-slate-900" aria-label="Електронний щоденник - головна"><GraduationCap className="h-7 w-7 shrink-0 sm:h-8 sm:w-8" strokeWidth={2.4} aria-hidden="true" /><span className="max-w-[150px] truncate text-[0.92rem] font-bold min-[420px]:max-w-none sm:text-[1.05rem]">Електронний щоденник</span></a>
      <div className="flex items-center gap-1.5 sm:gap-4"><NotificationButton /><CurrentUserBadge /><button type="button" onClick={logout} className="inline-flex h-10 items-center gap-2 rounded-md px-3 text-[0.82rem] font-semibold text-slate-500 hover:bg-slate-100 hover:text-slate-900"><LogOut className="h-4 w-4" aria-hidden="true" /><span className="hidden sm:inline">Вийти</span></button></div>
    </header>
  );
}

function Navigation({ config, compact = false }) {
  return config.homeRoute === "teacher"
    ? <TeacherNavigation activeRoute="teacher/messages" compact={compact} />
    : <LearnerNavigation role={config.homeRoute.toUpperCase()} activeRoute={`${config.homeRoute}/messages`} compact={compact} />;
}
