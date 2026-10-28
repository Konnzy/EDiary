import { useEffect, useId, useMemo, useRef, useState } from "react";
import {
  Bell,
  BookOpen,
  BriefcaseBusiness,
  CalendarDays,
  CalendarX2,
  Camera,
  Check,
  ChevronDown,
  Eye,
  EyeOff,
  GraduationCap,
  Home,
  KeyRound,
  LockKeyhole,
  LogOut,
  Loader2,
  Mail,
  Phone,
  Plus,
  Save,
  ShieldCheck,
  Trash2,
  User,
  UserRound,
  Users,
  X,
} from "lucide-react";
import { AppFooter } from "@/components/AppFooter.jsx";
import { LearnerNavigation } from "@/components/LearnerNavigation.jsx";
import { NotificationButton } from "@/components/NotificationButton.jsx";
import { Alert } from "@/components/ui/alert.jsx";
import { Button } from "@/components/ui/button.jsx";
import { Card } from "@/components/ui/card.jsx";
import { Input } from "@/components/ui/input.jsx";
import { ApiError, authApi, parentApi, userProfileApi } from "@/lib/api.js";
import { childOptionLabel } from "@/lib/childOption.js";
import { getPasswordStrength } from "@/lib/passwordStrength.js";
import { teacherNavigationItems } from "@/lib/teacherNavigation.js";
import { TeacherNavigation } from "@/components/TeacherNavigation.jsx";
import { formatShortName } from "@/lib/userIdentity.js";
import { cn } from "@/lib/utils.js";
import {
  normalizePhone,
  mapProfileResponse,
  validateAvatarFile,
  validatePasswordChange,
  validatePhone,
} from "./profileModel.js";

const roleConfig = {
  STUDENT: {
    homeRoute: "student",
    profileRoute: "student/profile",
    title: "Профіль учня",
    subtitle: "Особиста інформація та налаштування облікового запису",
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
    profileRoute: "parent/profile",
    title: "Профіль батьків",
    subtitle: "Особиста інформація та налаштування облікового запису",
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
    profileRoute: "teacher/profile",
    title: "Профіль учителя",
    subtitle: "Особиста та професійна інформація і налаштування облікового запису",
    navigation: teacherNavigationItems,
  },
};

const notificationOptions = {
  STUDENT: [
    { key: "newGrade", title: "Нова оцінка", description: "Сповіщати після виставлення нової оцінки", icon: GraduationCap },
    { key: "newHomework", title: "Нове домашнє завдання", description: "Сповіщати про нове призначене завдання", icon: BookOpen },
    { key: "homeworkDeadlineReminder", title: "Дедлайн домашнього завдання", description: "Нагадувати за день до терміну виконання завдання", icon: CalendarDays },
    { key: "absence", title: "Відсутність на уроці", description: "Сповіщати, коли викладач виставив статус «Н»", icon: CalendarX2 },
    { key: "scheduleChange", title: "Зміна розкладу", description: "Сповіщати про зміни у розкладі занять", icon: CalendarDays },
  ],
  PARENT: [
    { key: "newGrade", title: "Нова оцінка", description: "Сповіщати після виставлення нової оцінки", icon: GraduationCap },
    { key: "newHomework", title: "Нове домашнє завдання", description: "Сповіщати про нове призначене завдання", icon: BookOpen },
    { key: "homeworkDeadlineReminder", title: "Дедлайн домашнього завдання", description: "Нагадувати за день до терміну виконання завдання дитини", icon: CalendarDays },
    { key: "absence", title: "Відсутність на уроці", description: "Сповіщати, коли викладач виставив статус «Н»", icon: CalendarX2 },
    { key: "scheduleChange", title: "Зміна розкладу", description: "Сповіщати про зміни у розкладі занять дитини", icon: CalendarDays },
  ],
  TEACHER: [
    { key: "scheduleChange", title: "Зміна розкладу", description: "Сповіщати про зміни у розкладі занять", icon: CalendarDays },
    { key: "classScheduleChange", title: "Зміни розкладу класу", description: "Сповіщати про зміни у розкладі класу", icon: CalendarDays, homeroomOnly: true },
    { key: "prolongedAbsence", title: "Тривалі пропуски", description: "Сповіщати про тривалі пропуски учнів класу", icon: CalendarX2, homeroomOnly: true },
    { key: "classRosterChange", title: "Зміни у складі класу", description: "Сповіщати, коли учня додають до класу або переводять з нього", icon: Users, homeroomOnly: true },
  ],
};

export function UserProfilePage({ role, openAddChild = false, focusNotifications = false }) {
  const config = roleConfig[role];
  const [data, setData] = useState(null);
  const [loadError, setLoadError] = useState("");
  const [reloadKey, setReloadKey] = useState(0);
  const [pending, setPending] = useState("");
  const [avatarError, setAvatarError] = useState("");
  const [avatarNotice, setAvatarNotice] = useState("");
  const [phone, setPhone] = useState("");
  const [phoneError, setPhoneError] = useState("");
  const [phoneNotice, setPhoneNotice] = useState("");
  const [notificationDraft, setNotificationDraft] = useState({});
  const [notificationError, setNotificationError] = useState("");
  const [notificationNotice, setNotificationNotice] = useState("");
  const [selectedChildId, setSelectedChildId] = useState("");
  const [isLinkModalOpen, setIsLinkModalOpen] = useState(role === "PARENT" && openAddChild);
  const [linkCode, setLinkCode] = useState("");
  const [linkError, setLinkError] = useState("");
  const [isLinking, setIsLinking] = useState(false);
  const [linkNotice, setLinkNotice] = useState("");
  const fileInput = useRef(null);

  useEffect(() => {
    if (role === "PARENT" && openAddChild) setIsLinkModalOpen(true);
  }, [role, openAddChild]);

  useEffect(() => {
    if (!data || !focusNotifications) return undefined;
    const frame = window.requestAnimationFrame(() => document.getElementById("profile-notifications")?.scrollIntoView({ behavior: "smooth", block: "start" }));
    return () => window.cancelAnimationFrame(frame);
  }, [data, focusNotifications]);

  useEffect(() => {
    let active = true;
    userProfileApi.find().then((response) => {
      if (!active) return;
      if (response.role !== role) throw new Error("Доступ до цього профілю відсутній.");
      const profile = mapProfileResponse(response);
      setData(profile);
      setPhone(profile.personal.phone || "");
      setNotificationDraft(profile.notifications);
      setSelectedChildId((current) => profile.children.some((child) => child.id === current)
        ? current : profile.children[0]?.id || "");
      setLoadError("");
    }).catch((error) => {
      if (active) setLoadError(error.message || "Не вдалося завантажити профіль.");
    });
    return () => { active = false; };
  }, [role, reloadKey]);

  if (!config) return null;

  const avatar = data?.avatarVersion != null ? userProfileApi.avatarUrl(data.avatarVersion) : "";

  const handleAvatar = async (event) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    setAvatarError("");
    setAvatarNotice("");

    const fileError = validateAvatarFile(file);
    if (fileError) {
      setAvatarError(fileError);
      return;
    }

    try {
      setPending("avatar");
      await readAvatar(file);
      const response = await userProfileApi.uploadAvatar(file);
      setData(mapProfileResponse(response));
      setAvatarNotice("Фото профілю оновлено.");
    } catch (error) {
      setAvatarError(error.message || "Не вдалося обробити зображення.");
    } finally {
      setPending("");
    }
  };

  const removeAvatar = async () => {
    setPending("avatar");
    setAvatarError("");
    setAvatarNotice("");
    try {
      await userProfileApi.deleteAvatar();
      setData((current) => ({ ...current, avatarVersion: null }));
      setAvatarNotice("Фото профілю видалено.");
    } catch (error) {
      setAvatarError(error.message || "Не вдалося видалити фото.");
    } finally {
      setPending("");
    }
  };

  const savePhone = async (event) => {
    event.preventDefault();
    const error = validatePhone(phone);
    setPhoneError(error);
    setPhoneNotice("");
    if (error) return;
    const normalized = normalizePhone(phone);
    setPending("phone");
    try {
      const response = await userProfileApi.updatePhone(normalized);
      const profile = mapProfileResponse(response);
      setData(profile);
      setPhone(profile.personal.phone);
      setPhoneNotice("Номер телефону збережено.");
    } catch (requestError) {
      setPhoneError(requestError.message || "Не вдалося зберегти номер телефону.");
    } finally {
      setPending("");
    }
  };

  const saveNotifications = async () => {
    setPending("notifications");
    setNotificationError("");
    setNotificationNotice("");
    try {
      const response = await userProfileApi.updateNotifications(notificationDraft);
      const visibleOptions = notificationOptions[role].filter((option) => !option.homeroomOnly || Boolean(data.professional?.homeroomClass));
      if (!visibleOptions.every((option) => response.notifications?.[option.key] === notificationDraft[option.key])) {
        throw new Error("Не вдалося підтвердити збереження налаштувань. Спробуйте ще раз.");
      }
      const profile = mapProfileResponse(response);
      setData(profile);
      setNotificationDraft(profile.notifications);
      setNotificationNotice("Налаштування сповіщень збережено.");
    } catch (error) {
      setNotificationError(error.message || "Не вдалося зберегти налаштування.");
    } finally {
      setPending("");
    }
  };

  const selectChild = (childId) => {
    setSelectedChildId(childId);
  };

  const resetLinkModal = () => {
    setIsLinkModalOpen(false);
    setLinkCode("");
    setLinkError("");
    if (window.location.hash.startsWith("#parent/profile?")) {
      window.location.hash = "parent/profile";
    }
  };

  const closeLinkModal = () => {
    if (!isLinking) resetLinkModal();
  };

  const linkChild = async (event) => {
    event.preventDefault();
    if (!/^[A-Z0-9]{4}-[A-Z0-9]{4}$/.test(linkCode)) {
      setLinkError("Введіть код у форматі XXXX-XXXX");
      return;
    }
    setIsLinking(true);
    setLinkError("");
    try {
      const child = await parentApi.linkChild(linkCode);
      setSelectedChildId(String(child.studentId));
      try {
        const response = await userProfileApi.find();
        const updatedChildren = mapProfileResponse(response).children;
        setData((current) => ({ ...current, children: updatedChildren }));
        setLinkNotice("Дитину додано до профілю.");
      } catch {
        setLinkNotice("Дитину додано. Оновіть сторінку, щоб побачити її дані.");
      }
      resetLinkModal();
    } catch (error) {
      setLinkError(error instanceof ApiError ? error.message : "Не вдалося додати дитину.");
    } finally {
      setIsLinking(false);
    }
  };

  if (!data) {
    return <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      {loadError
        ? <div className="max-w-md text-center"><Alert>{loadError}</Alert><Button type="button" className="mt-4" onClick={() => setReloadKey((value) => value + 1)}>Спробувати ще раз</Button></div>
        : <p role="status" className="text-sm text-slate-500">Завантаження профілю…</p>}
    </div>;
  }

  return (
    <div className="flex min-h-screen flex-col bg-slate-50 text-slate-800">
      <TopBar config={config} data={data} avatar={avatar} />
      <Navigation config={config} compact />

      <div className="flex flex-1">
        <Navigation config={config} />
        <div className="flex min-w-0 flex-1 flex-col lg:border-l lg:border-slate-200/80">
          <main className="min-w-0 flex-1 px-4 py-5 pb-8 sm:px-6 lg:px-8 lg:py-6">
            <div className="mx-auto w-full max-w-[1280px] 2xl:max-w-[1500px]">
              <header className="mb-5 sm:mb-7">
                <h1 className="text-[1.45rem] font-bold leading-tight text-slate-900 sm:text-[1.65rem]">Профіль</h1>
                <p className="mt-2 text-[0.86rem] text-slate-500">{config.subtitle}</p>
              </header>

              <ProfileSummary
                role={role}
                data={data}
                avatar={avatar}
                busy={pending === "avatar"}
                fileInput={fileInput}
                avatarError={avatarError}
                avatarNotice={avatarNotice}
                onAvatarChange={handleAvatar}
                onRemoveAvatar={removeAvatar}
              />

              <div className="mt-5 grid items-stretch gap-5 xl:grid-cols-[minmax(0,1.08fr)_minmax(400px,0.92fr)]">
                <PersonalInformation
                  className="xl:col-start-1 xl:row-start-1"
                  role={role}
                  data={data}
                  phone={phone}
                  phoneError={phoneError}
                  phoneNotice={phoneNotice}
                  busy={pending === "phone"}
                  onPhoneChange={(value) => { setPhone(value); setPhoneError(""); setPhoneNotice(""); }}
                  onPhoneSave={savePhone}
                />
                {role === "STUDENT" ? <EducationInformation className="xl:col-start-1 xl:row-start-2" data={data.education} /> : null}
                {role === "PARENT" ? <LinkedChildInformation className="xl:col-start-1 xl:row-start-2" children={data.children} selectedChildId={selectedChildId} notice={linkNotice} onSelect={selectChild} onAdd={() => { setLinkNotice(""); setIsLinkModalOpen(true); }} /> : null}
                {role === "TEACHER" ? <ProfessionalInformation className="xl:col-start-1 xl:row-start-2" data={data.professional} /> : null}
                <NotificationSettings
                  className="xl:col-start-2 xl:row-start-1"
                  options={notificationOptions[role].filter((option) => !option.homeroomOnly || Boolean(data.professional?.homeroomClass))}
                  settings={notificationDraft}
                  notice={notificationNotice}
                  error={notificationError}
                  busy={pending === "notifications"}
                  onChange={(key) => {
                    setNotificationDraft((current) => ({ ...current, [key]: !current[key] }));
                    setNotificationNotice("");
                  }}
                  onSave={saveNotifications}
                />
                <PasswordSettings className="xl:col-start-2 xl:row-start-2" />
              </div>
            </div>
          </main>
          <AppFooter />
        </div>
      </div>
      {role === "PARENT" && isLinkModalOpen ? (
        <LinkChildModal
          code={linkCode}
          error={linkError}
          isSubmitting={isLinking}
          onChange={(value) => { setLinkCode(formatLinkCode(value)); setLinkError(""); }}
          onClose={closeLinkModal}
          onSubmit={linkChild}
        />
      ) : null}
    </div>
  );
}

function ProfileSummary({ data, avatar, busy, fileInput, avatarError, avatarNotice, onAvatarChange, onRemoveAvatar }) {
  return (
    <Card className="rounded-lg px-4 py-5 sm:px-6">
      <div className="flex flex-col gap-5 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex min-w-0 items-center gap-4">
          <Avatar avatar={avatar} initials={data.initials} size="large" />
          <div className="min-w-0">
            <h2 className="break-words text-[1.05rem] font-bold text-slate-900 sm:text-[1.2rem]">{data.fullName}</h2>
            <span className="mt-2 inline-flex rounded-md border border-slate-200 bg-slate-50 px-2.5 py-1 text-[0.7rem] font-bold text-slate-600">{data.roleLabel}</span>
          </div>
        </div>

        <div className="sm:text-right">
            <div className="flex flex-wrap gap-2 sm:justify-end">
              <input ref={fileInput} type="file" accept="image/jpeg,image/png" onChange={onAvatarChange} className="sr-only" aria-label="Оберіть фото профілю" />
              <Button type="button" disabled={busy} onClick={() => fileInput.current?.click()} className="h-9 rounded-md px-3 text-xs">
                <Camera className="h-4 w-4" aria-hidden="true" />Змінити фото
              </Button>
              {avatar ? (
                <Button type="button" variant="ghost" disabled={busy} onClick={onRemoveAvatar} className="h-9 rounded-md border border-red-200 bg-white px-3 text-xs text-red-600 hover:bg-red-50">
                  <Trash2 className="h-4 w-4" aria-hidden="true" />Видалити
                </Button>
              ) : null}
            </div>
            <p className="mt-2 text-[0.68rem] leading-4 text-slate-400">JPG або PNG · до 5 МБ</p>
            {avatarError ? <p className="mt-2 text-[0.7rem] font-medium text-red-600" role="alert">{avatarError}</p> : null}
            {avatarNotice ? <SuccessMessage className="sm:justify-end">{avatarNotice}</SuccessMessage> : null}
        </div>
      </div>
    </Card>
  );
}

function PersonalInformation({ className, role, data, phone, phoneError, phoneNotice, busy, onPhoneChange, onPhoneSave }) {
  const studentFields = [
    ["Ім’я", data.personal.firstName],
    ["Прізвище", data.personal.lastName],
    ["По батькові", data.personal.patronymic || "Не вказано"],
    ["Логін", data.personal.login],
    ["Електронна пошта", data.personal.email || "Не вказано"],
    ["Email батьків", data.personal.parentEmail || "Не прив’язано"],
    ["Телефон батьків", data.personal.parentPhone || "Не прив’язано"],
  ];
  const accountFields = [
    ["Ім’я", data.personal.firstName],
    ["Прізвище", data.personal.lastName],
    ["По батькові", data.personal.patronymic || "Не вказано"],
    ["Логін", data.personal.login],
    ["Електронна пошта", data.personal.email],
  ];

  return (
    <Section className={className} title="Особиста інформація" icon={UserRound}>
      <InformationGrid fields={role === "STUDENT" ? studentFields : accountFields} />
      {role !== "STUDENT" ? (
        <form onSubmit={onPhoneSave} className="mt-5 border-t border-slate-200 pt-5" noValidate>
          <label htmlFor={`${role.toLowerCase()}-phone`} className="text-[0.72rem] font-semibold text-slate-500">Номер телефону</label>
          <div className="mt-2 flex flex-col gap-2 sm:flex-row">
            <div className="relative min-w-0 flex-1">
              <Phone className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-slate-400" aria-hidden="true" />
              <Input id={`${role.toLowerCase()}-phone`} type="tel" value={phone} onChange={(event) => onPhoneChange(event.target.value)} className={cn("pl-10", phoneError && "border-red-400")} autoComplete="tel" />
            </div>
            <Button type="submit" disabled={busy} className="h-10 rounded-md px-4 text-xs"><Save className="h-4 w-4" aria-hidden="true" />Зберегти</Button>
          </div>
          {phoneError ? <p className="mt-2 text-[0.7rem] font-medium text-red-600" role="alert">{phoneError}</p> : null}
          {phoneNotice ? <SuccessMessage>{phoneNotice}</SuccessMessage> : null}
        </form>
      ) : null}
    </Section>
  );
}

function EducationInformation({ className, data }) {
  return (
    <Section className={className} title="Інформація про навчання" icon={GraduationCap}>
      <InformationGrid fields={[
        ["Клас", data.className],
        ["Дата реєстрації", data.registeredAt],
        ["Класний керівник", data.teacherName],
        ["Email класного керівника", data.teacherEmail],
        ["Телефон класного керівника", data.teacherPhone],
      ]} />
    </Section>
  );
}

function ProfessionalInformation({ className, data }) {
  return (
    <Section className={className} title="Професійна інформація" icon={BriefcaseBusiness}>
      <InformationGrid fields={[
        ["Посада", data.position],
        ["Класний керівник", data.homeroomClass || "Не є класним керівником"],
        ["Дата реєстрації", data.registeredAt],
      ]} />
      <div className="mt-5 grid gap-5 border-t border-slate-200 pt-5 sm:grid-cols-2">
        <TagList label="Предмети" values={data.subjects} />
        <TagList label="Класи, в яких викладає" values={data.classes} />
      </div>
    </Section>
  );
}

function TagList({ label, values }) {
  return (
    <div>
      <p className="text-[0.7rem] font-semibold text-slate-400">{label}</p>
      <div className="mt-2 flex flex-wrap gap-2">
        {values.length ? values.map((value) => <span key={value} className="rounded-md border border-slate-200 bg-slate-50 px-2.5 py-1.5 text-[0.76rem] font-semibold text-slate-700">{value}</span>) : <span className="text-[0.76rem] text-slate-500">Не призначено</span>}
      </div>
    </div>
  );
}

function LinkedChildInformation({ className, children, selectedChildId, notice, onSelect, onAdd }) {
  const selectedChild = children.find((child) => child.id === selectedChildId) || children[0];
  return (
    <Section className={className} title="Пов’язані діти" icon={GraduationCap}>
      <div className="border-b border-slate-200 pb-5">
        <label htmlFor="profile-child" className="block text-[0.72rem] font-semibold text-slate-500">Оберіть дитину</label>
        <div className="mt-2 grid grid-cols-[minmax(0,1fr)_auto] gap-2">
          <span className="relative min-w-0">
            <select id="profile-child" value={selectedChild?.id || ""} onChange={(event) => onSelect(event.target.value)} disabled={!children.length} className="h-10 w-full appearance-none rounded-md border border-slate-300 bg-white px-3 pr-9 text-[0.82rem] font-semibold text-slate-700 outline-hidden transition-colors hover:border-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200 disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-400">
              {children.length ? children.map((child) => <option key={child.id} value={child.id}>{childOptionLabel(child)}</option>) : <option value="">Немає прив’язаних дітей</option>}
            </select>
            <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
          </span>
          <Button type="button" onClick={onAdd} className="h-10 whitespace-nowrap rounded-md px-3 text-xs"><Plus className="h-4 w-4" aria-hidden="true" /><span>Додати дитину</span></Button>
        </div>
      </div>
      {notice ? <SuccessMessage>{notice}</SuccessMessage> : null}
      {!selectedChild ? <p className="mt-5 text-sm text-slate-500">Дитину ще не прив’язано до профілю.</p> : null}
      {selectedChild ? <div className="mt-5">
          <InformationGrid fields={[
            ["Повне ім’я", selectedChild.fullName],
            ["Електронна пошта", selectedChild.email || "Не вказано"],
            ["Клас", selectedChild.className],
            ["Класний керівник", selectedChild.teacherName],
            ["Email класного керівника", selectedChild.teacherEmail],
            ["Телефон класного керівника", selectedChild.teacherPhone],
          ]} />
      </div> : null}
    </Section>
  );
}

function formatLinkCode(value) {
  const characters = value.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 8);
  return characters.length > 4
    ? `${characters.slice(0, 4)}-${characters.slice(4)}`
    : characters;
}

function LinkChildModal({ code, error, isSubmitting, onChange, onClose, onSubmit }) {
  useEffect(() => {
    const onKeyDown = (event) => { if (event.key === "Escape") onClose(); };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 px-4 py-8" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section role="dialog" aria-modal="true" aria-labelledby="link-child-title" className="w-full max-w-[420px] rounded-lg border border-slate-200 bg-white p-5 shadow-2xl sm:p-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 id="link-child-title" className="text-[1.05rem] font-bold text-slate-900">Додати дитину</h2>
            <p className="mt-1.5 text-[0.78rem] leading-5 text-slate-500">Введіть код прив’язки, отриманий разом із реєстраційними даними дитини.</p>
          </div>
          <button type="button" onClick={onClose} disabled={isSubmitting} className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-50" aria-label="Закрити"><X className="h-4 w-4" aria-hidden="true" /></button>
        </div>

        <form className="mt-5" onSubmit={onSubmit}>
          <label htmlFor="parent-link-code" className="text-[0.78rem] font-semibold text-slate-700">Код прив’язки до дитини</label>
          <input
            id="parent-link-code"
            value={code}
            onChange={(event) => onChange(event.target.value)}
            placeholder="XXXX-XXXX"
            maxLength={9}
            autoComplete="off"
            autoFocus
            aria-invalid={Boolean(error)}
            aria-describedby={error ? "parent-link-code-error" : undefined}
            className={cn("mt-2 h-11 w-full rounded-md border bg-white px-3 text-[0.9rem] font-semibold uppercase tracking-[0.08em] text-slate-800 outline-hidden transition-colors focus:ring-2", error ? "border-red-300 focus:border-red-400 focus:ring-red-100" : "border-slate-300 focus:border-slate-500 focus:ring-slate-200")}
          />
          {error ? <p id="parent-link-code-error" className="mt-2 text-[0.76rem] text-red-600">{error}</p> : null}

          <div className="mt-6 flex justify-end gap-3">
            <button type="button" onClick={onClose} disabled={isSubmitting} className="h-10 rounded-md border border-slate-300 bg-white px-4 text-[0.8rem] font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50">Скасувати</button>
            <button type="submit" disabled={isSubmitting || code.length !== 9} className="inline-flex h-10 items-center justify-center gap-2 rounded-md bg-slate-800 px-4 text-[0.8rem] font-semibold text-white hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-60">
              {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <Plus className="h-4 w-4" aria-hidden="true" />}
              Додати
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}

function NotificationSettings({ className, options, settings, notice, error, busy, onChange, onSave }) {
  return (
    <Section id="profile-notifications" className={className} title="Сповіщення на пошту" icon={Bell}>
      <div className="divide-y divide-slate-100">
        {options.map((option) => {
          const Icon = option.icon;
          return (
            <div key={option.key} className="flex flex-wrap items-center gap-3 py-3 first:pt-0 last:pb-0">
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-slate-100 text-slate-600"><Icon className="h-4 w-4" aria-hidden="true" /></span>
              <div className="min-w-[150px] flex-1">
                <p className="text-[0.8rem] font-bold text-slate-800">{option.title}</p>
                <p className="mt-0.5 text-[0.7rem] leading-4 text-slate-500">{option.description}</p>
              </div>
              <Toggle checked={settings[option.key] ?? false} onChange={() => !busy && onChange(option.key)} label={`${option.title}: пошта`} />
            </div>
          );
        })}
      </div>
      {error ? <Alert>{error}</Alert> : null}
      <div className="mt-5 flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 pt-4">
        <div>{notice ? <SuccessMessage className="mt-0">{notice}</SuccessMessage> : null}</div>
        <Button type="button" disabled={busy} onClick={onSave} className="h-9 rounded-md px-3 text-xs"><Save className="h-4 w-4" aria-hidden="true" />Зберегти</Button>
      </div>
    </Section>
  );
}

function PasswordSettings({ className }) {
  const currentId = useId();
  const passwordId = useId();
  const confirmationId = useId();
  const [form, setForm] = useState({ current: "", password: "", confirmation: "" });
  const [visibility, setVisibility] = useState({ current: false, password: false, confirmation: false });
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const strength = useMemo(() => getPasswordStrength(form.password), [form.password]);

  const submit = async (event) => {
    event.preventDefault();
    const nextError = validatePasswordChange(form.current, form.password, form.confirmation);
    setError(nextError);
    setNotice("");
    if (nextError) return;
    setBusy(true);
    try {
      await userProfileApi.changePassword(form.current, form.password);
      setForm({ current: "", password: "", confirmation: "" });
      setNotice("Пароль успішно змінено.");
    } catch (requestError) {
      setError(requestError.message || "Не вдалося змінити пароль.");
    } finally {
      setBusy(false);
    }
  };

  const setValue = (key, value) => {
    setForm((current) => ({ ...current, [key]: value }));
    setError("");
    setNotice("");
  };

  return (
    <Section className={className} title="Зміна пароля" icon={LockKeyhole}>
      <form onSubmit={submit} className="space-y-4" noValidate>
        <PasswordField id={currentId} label="Поточний пароль" value={form.current} visible={visibility.current} autoComplete="current-password" onChange={(value) => setValue("current", value)} onToggle={() => setVisibility((current) => ({ ...current, current: !current.current }))} />
        <PasswordField id={passwordId} label="Новий пароль" value={form.password} visible={visibility.password} autoComplete="new-password" onChange={(value) => setValue("password", value)} onToggle={() => setVisibility((current) => ({ ...current, password: !current.password }))} />
        <PasswordField id={confirmationId} label="Повторіть новий пароль" value={form.confirmation} visible={visibility.confirmation} autoComplete="new-password" onChange={(value) => setValue("confirmation", value)} onToggle={() => setVisibility((current) => ({ ...current, confirmation: !current.confirmation }))} />
        <div className="space-y-2">
          <div className="flex items-center justify-between text-[0.72rem] font-medium text-slate-500">
            <span>Надійність пароля</span>
            <span>{strength.label}</span>
          </div>
          <div className="h-1.5 overflow-hidden rounded-full bg-slate-100">
            <div className={cn("h-full rounded-full transition-all duration-300", strength.width, strength.color)} />
          </div>
          <p className="text-[0.68rem] leading-4 text-slate-400">Щонайменше 8 символів, включаючи літери та цифри.</p>
        </div>
        {error ? <Alert>{error}</Alert> : null}
        {notice ? <SuccessMessage>{notice}</SuccessMessage> : null}
        <div className="flex justify-end pt-1">
          <Button type="submit" disabled={busy} className="h-10 rounded-md px-4 text-xs"><KeyRound className="h-4 w-4" aria-hidden="true" />Змінити пароль</Button>
        </div>
      </form>
    </Section>
  );
}

function PasswordField({ id, label, value, visible, onChange, onToggle, autoComplete }) {
  return (
    <label htmlFor={id} className="block">
      <span className="text-[0.72rem] font-semibold text-slate-500">{label}</span>
      <span className="relative mt-2 block">
        <Input id={id} type={visible ? "text" : "password"} value={value} onChange={(event) => onChange(event.target.value)} autoComplete={autoComplete} className="pr-10" />
        <button type="button" onClick={onToggle} className="absolute right-1 top-1 flex h-8 w-8 items-center justify-center rounded text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label={visible ? "Приховати пароль" : "Показати пароль"}>
          {visible ? <EyeOff className="h-4 w-4" aria-hidden="true" /> : <Eye className="h-4 w-4" aria-hidden="true" />}
        </button>
      </span>
    </label>
  );
}

function InformationGrid({ fields }) {
  return (
    <dl className="grid gap-x-6 gap-y-4 sm:grid-cols-2">
      {fields.map(([label, value]) => (
        <div key={label} className="min-w-0 border-b border-slate-100 pb-3 last:border-b-0 sm:last:border-b">
          <dt className="text-[0.68rem] font-semibold text-slate-400">{label}</dt>
          <dd className="mt-1 break-words text-[0.8rem] font-semibold text-slate-800">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function Section({ id, className, title, icon: Icon, children }) {
  return (
    <Card id={id} className={cn("h-full scroll-mt-20 rounded-lg px-4 py-5 sm:px-5", className)}>
      <div className="mb-5 flex items-center gap-2.5 border-b border-slate-200 pb-4">
        <span className="flex h-8 w-8 items-center justify-center rounded-md bg-slate-100 text-slate-700"><Icon className="h-4 w-4" aria-hidden="true" /></span>
        <h2 className="text-[0.92rem] font-bold text-slate-900">{title}</h2>
      </div>
      {children}
    </Card>
  );
}

function Toggle({ checked, onChange, label }) {
  return (
    <button type="button" role="switch" aria-checked={checked} aria-label={label} onClick={onChange} className={cn("relative h-6 w-11 shrink-0 rounded-full border transition-colors focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-slate-400 focus-visible:ring-offset-2", checked ? "border-slate-800 bg-slate-800" : "border-slate-300 bg-slate-200")}>
      <span className={cn("absolute top-0.5 flex h-[18px] w-[18px] items-center justify-center rounded-full bg-white shadow-xs transition-transform", checked ? "translate-x-[21px]" : "translate-x-0.5")}>
        {checked ? <Check className="h-3 w-3 text-slate-700" aria-hidden="true" /> : null}
      </span>
    </button>
  );
}

function SuccessMessage({ className, children }) {
  return <p className={cn("mt-2 flex items-center gap-1.5 text-[0.7rem] font-semibold text-emerald-700", className)} role="status"><ShieldCheck className="h-3.5 w-3.5" aria-hidden="true" />{children}</p>;
}

function Avatar({ avatar, initials, size = "small" }) {
  const sizeClass = size === "large" ? "h-20 w-20 text-xl sm:h-24 sm:w-24" : "h-9 w-9 text-xs";
  return avatar
    ? <img src={avatar} alt="Фото профілю" className={cn("shrink-0 rounded-full border border-slate-200 object-cover", sizeClass)} />
    : <span className={cn("flex shrink-0 items-center justify-center rounded-full border border-slate-200 font-bold text-slate-700", size === "large" ? "bg-slate-100" : "bg-white", sizeClass)} aria-label="Фото профілю не встановлено">{initials}</span>;
}

function TopBar({ config, data, avatar }) {
  const logout = async () => {
    try { await authApi.logout(); } catch { /* The session may already be expired. */ }
    window.location.hash = "login";
  };
  return (
    <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-slate-200/80 bg-white px-4 sm:px-5 lg:px-6">
      <a href={`#${config.homeRoute}`} className="inline-flex min-w-0 items-center gap-2 text-slate-900" aria-label="Електронний щоденник - головна"><GraduationCap className="h-7 w-7 shrink-0 sm:h-8 sm:w-8" strokeWidth={2.4} aria-hidden="true" /><span className="max-w-[150px] truncate text-[0.92rem] font-bold min-[420px]:max-w-none sm:text-[1.05rem]">Електронний щоденник</span></a>
      <div className="flex items-center gap-1.5 sm:gap-4">
        <NotificationButton />
        <div className="hidden min-w-0 items-center gap-3 rounded-md bg-slate-50 px-3 py-1.5 sm:flex"><div className="min-w-0 text-right leading-tight"><div className="max-w-[190px] truncate text-[0.78rem] font-bold text-slate-800">{formatShortName({ firstName: data.personal.firstName, lastName: data.personal.lastName })}</div><div className="text-[0.7rem] text-slate-500">{data.roleLabel}</div></div><Avatar avatar={avatar} initials={data.initials} /></div>
        <button type="button" onClick={logout} className="inline-flex h-10 items-center gap-2 rounded-md px-3 text-[0.82rem] font-semibold text-slate-500 hover:bg-slate-100 hover:text-slate-900"><LogOut className="h-4 w-4" aria-hidden="true" /><span className="hidden sm:inline">Вийти</span></button>
      </div>
    </header>
  );
}

function Navigation({ config, compact = false }) {
  return config.homeRoute === "teacher"
    ? <TeacherNavigation activeRoute="teacher/profile" compact={compact} />
    : <LearnerNavigation role={config.homeRoute.toUpperCase()} activeRoute={config.profileRoute} compact={compact} />;
}

function readAvatar(file) {
  return new Promise((resolve, reject) => {
    const objectUrl = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () => {
      URL.revokeObjectURL(objectUrl);
      resolve();
    };
    image.onerror = () => {
      URL.revokeObjectURL(objectUrl);
      reject(new Error("Файл не вдалося розпізнати як зображення."));
    };
    image.src = objectUrl;
  });
}
