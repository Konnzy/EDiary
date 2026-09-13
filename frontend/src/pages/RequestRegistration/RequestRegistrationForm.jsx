import { useEffect, useId, useMemo, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import {
  CheckCircle2,
  ChevronDown,
  Info,
  KeyRound,
  Loader2,
  Mail,
  Phone,
  User,
} from "lucide-react";
import { Alert } from "@/components/ui/alert.jsx";
import { BirthDateInput, getBirthDateBounds } from "@/components/ui/birth-date-input.jsx";
import { Button } from "@/components/ui/button.jsx";
import { Card, CardContent, CardHeader } from "@/components/ui/card.jsx";
import { Checkbox } from "@/components/ui/checkbox.jsx";
import { Input } from "@/components/ui/input.jsx";
import { ApiError, registrationApi } from "@/lib/api.js";
import { cn } from "@/lib/utils.js";

const initialForm = {
  firstName: "",
  lastName: "",
  patronymic: "",
  email: "",
  phone: "",
  birthDate: "",
  role: "",
  classSubject: "",
  parentLinkCode: "",
  agree: false,
};

const UKRAINIAN_NAME_PATTERN = /^[А-Яа-яІіЇїЄєҐґ]+(?:[ '’-][А-Яа-яІіЇїЄєҐґ]+)*$/;

function formatUkrainianName(value) {
  return value.replace(/[^А-Яа-яІіЇїЄєҐґ '’-]/g, "").slice(0, 50);
}

function formatUkrainianPhone(value) {
  const digits = value.replace(/\D/g, "").slice(0, 9);
  const parts = [
    digits.slice(0, 2),
    digits.slice(2, 5),
    digits.slice(5, 7),
    digits.slice(7, 9),
  ].filter(Boolean);

  return parts.join(" ");
}

function formatParentLinkCode(value) {
  const characters = value.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 8);
  return characters.length > 4
    ? `${characters.slice(0, 4)}-${characters.slice(4)}`
    : characters;
}

function parseBirthDate(value) {
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value);
  if (!match) return null;

  const [, dayText, monthText, yearText] = match;
  const day = Number(dayText);
  const month = Number(monthText);
  const year = Number(yearText);
  const date = new Date(year, month - 1, day);

  if (
    date.getFullYear() !== year
    || date.getMonth() !== month - 1
    || date.getDate() !== day
  ) {
    return null;
  }

  return {
    date,
    iso: `${yearText}-${monthText}-${dayText}`,
  };
}

function calculateAge(birthDate, today = new Date()) {
  let age = today.getFullYear() - birthDate.getFullYear();
  const birthdayHasPassed =
    today.getMonth() > birthDate.getMonth()
    || (today.getMonth() === birthDate.getMonth() && today.getDate() >= birthDate.getDate());

  if (!birthdayHasPassed) age -= 1;
  return age;
}

function validateForm(values) {
  const errors = {};

  if (!values.firstName.trim()) {
    errors.firstName = "Будь ласка, введіть ім’я";
  } else if (!UKRAINIAN_NAME_PATTERN.test(values.firstName.trim())) {
    errors.firstName = "Використовуйте лише українські літери";
  }

  if (!values.lastName.trim()) {
    errors.lastName = "Будь ласка, введіть прізвище";
  } else if (!UKRAINIAN_NAME_PATTERN.test(values.lastName.trim())) {
    errors.lastName = "Використовуйте лише українські літери";
  }


  if (values.patronymic.trim() && !UKRAINIAN_NAME_PATTERN.test(values.patronymic.trim())) {
    errors.patronymic = "Використовуйте лише українські літери";
  }

  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email.trim())) {
    errors.email = "Некоректний формат електронної пошти";
  }

  if (values.phone.replace(/\D/g, "").length !== 9) {
    errors.phone = "Введіть номер у форматі XX XXX XX XX";
  }

  const parsedBirthDate = parseBirthDate(values.birthDate);
  if (!values.birthDate) {
    errors.birthDate = "Будь ласка, вкажіть дату народження";
  } else if (!parsedBirthDate) {
    errors.birthDate = "Введіть коректну дату у форматі дд/мм/рррр";
  } else {
    const age = calculateAge(parsedBirthDate.date);
    if (values.role === "STUDENT" && (age < 5 || age > 20)) {
      errors.birthDate = "Вік учня має бути від 5 до 20 років";
    } else if ((values.role === "PARENT" || values.role === "TEACHER") && (age < 18 || age > 100)) {
      errors.birthDate = "Для обраної ролі вік має бути від 18 до 100 років";
    } else if (parsedBirthDate.date >= new Date()) {
      errors.birthDate = "Дата народження має бути в минулому";
    }
  }

  if (!values.role) {
    errors.role = "Будь ласка, оберіть роль";
  }

  if (values.role === "PARENT" && !/^[A-Z0-9]{4}-[A-Z0-9]{4}$/.test(values.parentLinkCode)) {
    errors.parentLinkCode = "Введіть код у форматі XXXX-XXXX";
  }

  if ((values.role === "STUDENT" || values.role === "TEACHER") && !values.classSubject) {
    errors.classSubject =
      values.role === "TEACHER" ? "Будь ласка, оберіть предмет" : "Будь ласка, оберіть клас";
  }

  if (!values.agree) {
    errors.agree = "Потрібно погодитися з умовами користування та політикою конфіденційності";
  }

  return errors;
}

export function RequestRegistrationForm() {
  const firstNameId = useId();
  const lastNameId = useId();
  const patronymicId = useId();
  const emailId = useId();
  const phoneId = useId();
  const birthDateId = useId();
  const roleId = useId();
  const classSubjectId = useId();
  const parentLinkCodeId = useId();
  const agreeId = useId();

  const [form, setForm] = useState(initialForm);
  const [submitted, setSubmitted] = useState(false);
  const [success, setSuccess] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [serverError, setServerError] = useState("");
  const [serverFieldErrors, setServerFieldErrors] = useState({});
  const [options, setOptions] = useState({ classes: [], subjects: [] });
  const [optionsError, setOptionsError] = useState("");

  useEffect(() => {
    let active = true;
    registrationApi.getOptions()
      .then((data) => {
        if (active) setOptions(data);
      })
      .catch(() => {
        if (active) setOptionsError("Не вдалося завантажити перелік класів і предметів.");
      });
    return () => {
      active = false;
    };
  }, []);

  const clientErrors = useMemo(() => validateForm(form), [form]);
  const errors = { ...clientErrors, ...serverFieldErrors };
  const showErrors = submitted && !success;
  const isTeacher = form.role === "TEACHER";
  const isParent = form.role === "PARENT";
  const classSubjectLabel = form.role ? (isTeacher ? "Предмет *" : "Клас *") : "Клас / Предмет *";
  const classSubjectPlaceholder = form.role
    ? isTeacher
      ? "Оберіть предмет"
      : "Оберіть клас"
    : "Спочатку оберіть роль";
  const classSubjectOptions = isTeacher ? options.subjects : options.classes;
  const birthDateBounds = getBirthDateBounds(form.role);

  const updateField = (field, value) => {
    setForm((current) => ({
      ...current,
      [field]: value,
      ...(field === "role" ? { classSubject: "", parentLinkCode: "" } : null),
    }));
    setSuccess(false);
    setServerError("");
    setServerFieldErrors((current) => {
      if (!current[field]) return current;
      const next = { ...current };
      delete next[field];
      return next;
    });
  };

  const updatePhone = (value) => {
    updateField("phone", formatUkrainianPhone(value));
  };

  const updateName = (field, value) => {
    updateField(field, formatUkrainianName(value));
  };

  const getInputClassName = (field, extraClassName = "") =>
    cn(showErrors && errors[field] && "border-red-400 bg-red-50/30", extraClassName);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSubmitted(true);
    setSuccess(false);
    const nextErrors = validateForm(form);
    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    setIsLoading(true);
    setServerError("");
    setServerFieldErrors({});

    try {
      await registrationApi.submit({
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        patronymic: form.patronymic.trim() || null,
        email: form.email.trim(),
        phone: `+380${form.phone.replace(/\D/g, "")}`,
        birthDate: parseBirthDate(form.birthDate).iso,
        role: form.role,
        requestedClassId: form.role === "STUDENT" ? Number(form.classSubject) : null,
        requestedSubjectId: isTeacher ? Number(form.classSubject) : null,
        parentLinkCode: isParent ? form.parentLinkCode : null,
        termsAccepted: form.agree,
      });
      setSuccess(true);
      setForm(initialForm);
      setSubmitted(false);
    } catch (error) {
      if (error instanceof ApiError) {
        setServerError(error.message);
        setServerFieldErrors(error.fieldErrors || {});
      } else {
        setServerError("Не вдалося надіслати заявку.");
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 18, scale: 0.985 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      transition={{ duration: 0.42, delay: 0.08, ease: [0.22, 1, 0.36, 1] }}
    >
      <Card aria-labelledby="request-title" className="px-8 py-9 sm:px-10 sm:py-10">
        <CardHeader className="text-center">
          <h1 id="request-title" className="text-[1.72rem] font-bold leading-tight text-slate-800">
            Подати заявку на реєстрацію
          </h1>
          <p className="pt-2 text-[0.9rem] text-slate-500">
            Створіть запит на доступ до системи
          </p>
        </CardHeader>

        <CardContent className="mt-7">
          <div className="mb-7 flex items-start gap-3 rounded-md border border-slate-300 bg-slate-100/75 px-4 py-4 text-[0.82rem] leading-5 text-slate-600">
            <Info className="mt-0.5 h-4 w-4 shrink-0 fill-slate-600 text-white" aria-hidden="true" />
            <p>
              Будь ласка, заповніть всі обов’язкові поля для верифікації вашого профілю.
            </p>
          </div>

          <form className="space-y-5" onSubmit={handleSubmit} noValidate>
            <AnimatePresence initial={false}>
              {serverError || optionsError ? (
                <motion.div
                  initial={{ opacity: 0, y: -6, height: 0 }}
                  animate={{ opacity: 1, y: 0, height: "auto" }}
                  exit={{ opacity: 0, y: -6, height: 0 }}
                >
                  <Alert>{serverError || optionsError}</Alert>
                </motion.div>
              ) : null}
            </AnimatePresence>

            <div className="grid gap-5 sm:grid-cols-2">
              <Field
                id={firstNameId}
                label="Ім’я *"
                error={showErrors ? errors.firstName : ""}
              >
                <IconInput icon={User}>
                  <Input
                    id={firstNameId}
                    name="firstName"
                    autoComplete="given-name"
                    value={form.firstName}
                    onChange={(event) => updateName("firstName", event.target.value)}
                    placeholder="Олександр"
                    className={getInputClassName("firstName", "pl-10")}
                    aria-invalid={Boolean(showErrors && errors.firstName)}
                  />
                </IconInput>
              </Field>

              <Field id={lastNameId} label="Прізвище *" error={showErrors ? errors.lastName : ""}>
                <Input
                  id={lastNameId}
                  name="lastName"
                  autoComplete="family-name"
                  value={form.lastName}
                  onChange={(event) => updateName("lastName", event.target.value)}
                  placeholder="Коваленко"
                  className={getInputClassName("lastName")}
                  aria-invalid={Boolean(showErrors && errors.lastName)}
                />
              </Field>
            </div>

            <Field id={patronymicId} label="По батькові" error={showErrors ? errors.patronymic : ""}>
              <IconInput icon={User}>
                <Input
                  id={patronymicId}
                  name="patronymic"
                  autoComplete="additional-name"
                  value={form.patronymic}
                  onChange={(event) => updateName("patronymic", event.target.value)}
                  maxLength={50}
                  placeholder="Олександрович"
                  className={getInputClassName("patronymic", "pl-10")}
                  aria-invalid={Boolean(showErrors && errors.patronymic)}
                />
              </IconInput>
            </Field>

            <div className="grid gap-5 sm:grid-cols-2">
              <Field id={emailId} label="Електронна пошта *" error={showErrors ? errors.email : ""}>
                <IconInput icon={Mail}>
                  <Input
                    id={emailId}
                    name="email"
                    type="email"
                    autoComplete="email"
                    value={form.email}
                    onChange={(event) => updateField("email", event.target.value)}
                    placeholder="name@example.com"
                    className={getInputClassName("email", "pl-10")}
                    aria-invalid={Boolean(showErrors && errors.email)}
                  />
                </IconInput>
              </Field>

              <Field id={phoneId} label="Номер телефону *" error={showErrors ? errors.phone : ""}>
                <div className="relative">
                  <Phone
                    className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
                    aria-hidden="true"
                  />
                  <span className="pointer-events-none absolute left-10 top-1/2 -translate-y-1/2 text-[0.82rem] text-slate-700">
                    +380
                  </span>
                  <Input
                    id={phoneId}
                    name="phone"
                    type="tel"
                    autoComplete="tel"
                    inputMode="numeric"
                    value={form.phone}
                    onChange={(event) => updatePhone(event.target.value)}
                    placeholder="XX XXX XX XX"
                    className={getInputClassName("phone", "pl-[5.05rem]")}
                    aria-invalid={Boolean(showErrors && errors.phone)}
                  />
                </div>
              </Field>
            </div>

            <div className="grid gap-5 sm:grid-cols-2">
              <Field id={birthDateId} label="Дата народження *" error={showErrors ? errors.birthDate : ""}>
                <BirthDateInput
                  id={birthDateId}
                  value={form.birthDate}
                  onChange={(value) => updateField("birthDate", value)}
                  min={birthDateBounds.min}
                  max={birthDateBounds.max}
                  className={getInputClassName("birthDate")}
                  aria-invalid={Boolean(showErrors && errors.birthDate)}
                />
              </Field>

              <Field id={roleId} label="Роль *" error={showErrors ? errors.role : ""}>
                <div className="relative">
                  <select
                    id={roleId}
                    name="role"
                    value={form.role}
                    onChange={(event) => updateField("role", event.target.value)}
                    className={cn(
                      "flex h-10 w-full appearance-none rounded-md border border-slate-300/85 bg-slate-50/40 px-3 py-2 pr-10 text-[0.82rem] text-slate-800 shadow-[inset_0_1px_1px_rgba(15,23,42,0.03)] transition-all duration-200 hover:border-slate-400/70 focus:border-primary/45 focus:bg-white focus:outline-none focus:shadow-glow",
                      !form.role && "text-slate-500",
                      showErrors && errors.role && "border-red-400 bg-red-50/30",
                    )}
                    aria-invalid={Boolean(showErrors && errors.role)}
                  >
                    <option value="" disabled>Обрати роль</option>
                    <option value="STUDENT">Учень</option>
                    <option value="PARENT">Батьки</option>
                    <option value="TEACHER">Вчитель</option>
                  </select>
                  <ChevronDown
                    className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
                    aria-hidden="true"
                  />
                </div>
              </Field>
            </div>

            <AnimatePresence initial={false}>
              {form.role ? (
                <motion.div
                  initial={{ opacity: 0, y: -8, height: 0 }}
                  animate={{ opacity: 1, y: 0, height: "auto" }}
                  exit={{ opacity: 0, y: -8, height: 0 }}
                  transition={{ duration: 0.22, ease: "easeOut" }}
                >
                  {isParent ? (
                    <Field
                      id={parentLinkCodeId}
                      label="Код прив’язки до дитини *"
                      error={showErrors ? errors.parentLinkCode : ""}
                    >
                      <IconInput icon={KeyRound}>
                        <Input
                          id={parentLinkCodeId}
                          name="parentLinkCode"
                          value={form.parentLinkCode}
                          onChange={(event) => updateField("parentLinkCode", formatParentLinkCode(event.target.value))}
                          maxLength={9}
                          autoComplete="off"
                          autoCapitalize="characters"
                          spellCheck={false}
                          placeholder="XXXX-XXXX"
                          className={getInputClassName("parentLinkCode", "pl-10 uppercase")}
                          aria-invalid={Boolean(showErrors && errors.parentLinkCode)}
                        />
                      </IconInput>
                      <p className="mt-2 text-[0.72rem] leading-5 text-slate-500">
                        Код надсилається учню разом із даними для входу.
                      </p>
                    </Field>
                  ) : (
                    <Field
                      id={classSubjectId}
                      label={classSubjectLabel}
                      error={showErrors ? errors.classSubject : ""}
                    >
                      <div className="relative">
                        <select
                          id={classSubjectId}
                          name="classSubject"
                          value={form.classSubject}
                          onChange={(event) => updateField("classSubject", event.target.value)}
                          className={cn(
                            "flex h-10 w-full appearance-none rounded-md border border-slate-300/85 bg-slate-50/40 px-3 py-2 pr-10 text-[0.82rem] text-slate-800 shadow-[inset_0_1px_1px_rgba(15,23,42,0.03)] transition-all duration-200 hover:border-slate-400/70 focus:border-primary/45 focus:bg-white focus:outline-none focus:shadow-glow",
                            !form.classSubject && "text-slate-500",
                            showErrors && errors.classSubject && "border-red-400 bg-red-50/30",
                          )}
                          aria-invalid={Boolean(showErrors && errors.classSubject)}
                        >
                          <option value="" disabled>{classSubjectPlaceholder}</option>
                          {classSubjectOptions.map((option) => (
                            <option key={option.id} value={option.id}>{option.label}</option>
                          ))}
                        </select>
                        <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                      </div>
                    </Field>
                  )}
                </motion.div>
              ) : null}
            </AnimatePresence>

            <div>
              <div className="flex items-center gap-2">
                <Checkbox
                  id={agreeId}
                  checked={form.agree}
                  onCheckedChange={(checked) => updateField("agree", checked)}
                />
                <span className="flex flex-wrap items-center gap-x-1.5 gap-y-1">
                  <label
                    htmlFor={agreeId}
                    className="cursor-pointer select-none text-[0.82rem] text-slate-600 transition-colors hover:text-slate-900"
                  >
                    Я погоджуюсь з
                  </label>
                  <a
                    href="#terms-of-use"
                    className="text-[0.82rem] font-medium text-slate-800 underline-offset-4 transition-colors hover:text-primary hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                  >
                    умовами користування
                  </a>
                  <label
                    htmlFor={agreeId}
                    className="cursor-pointer select-none text-[0.82rem] text-slate-600 transition-colors hover:text-slate-900"
                  >
                    та з
                  </label>
                  <a
                    href="#privacy-policy"
                    className="text-[0.82rem] font-medium text-slate-800 underline-offset-4 transition-colors hover:text-primary hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                  >
                    політикою конфіденційності
                  </a>
                </span>
              </div>
              {showErrors && errors.agree ? (
                <p className="mt-2 text-[0.76rem] text-red-500">{errors.agree}</p>
              ) : null}
            </div>

            <Button type="submit" className="h-12 w-full rounded-md text-[0.95rem] font-bold" disabled={isLoading}>
              {isLoading ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
                  Надсилання...
                </>
              ) : (
                "Подати заявку"
              )}
            </Button>
          </form>

          <p className="mt-6 text-center text-[0.82rem] text-slate-500">
            Вже маєте акаунт?{" "}
            <a
              href="#login"
              className="font-semibold text-slate-800 underline-offset-4 transition-all duration-200 hover:text-primary hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
            >
              Увійти
            </a>
          </p>

          <AnimatePresence initial={false}>
            {success ? (
              <motion.div
                initial={{ opacity: 0, y: 8, height: 0 }}
                animate={{ opacity: 1, y: 0, height: "auto" }}
                exit={{ opacity: 0, y: 8, height: 0 }}
                transition={{ duration: 0.24, ease: "easeOut" }}
                className="mt-7"
              >
                <div className="flex items-start gap-3 rounded-md border border-emerald-300 bg-emerald-50 px-4 py-4 text-[0.82rem] leading-5 text-emerald-800">
                  <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-emerald-600" aria-hidden="true" />
                  <p>Вашу заявку успішно надіслано. Вона буде розглянута адміністрацією навчального закладу.
                    Результат перевірки буде надіслано на вашу електронну пошту.</p>
                </div>
              </motion.div>
            ) : null}
          </AnimatePresence>
        </CardContent>
      </Card>
    </motion.div>
  );
}

function Field({ id, label, error, children }) {
  return (
    <div className="space-y-2">
      <label htmlFor={id} className="text-[0.82rem] font-medium text-slate-600">
        {label}
      </label>
      {children}
      {error ? <p className="text-[0.76rem] leading-4 text-red-500">{error}</p> : null}
    </div>
  );
}

function IconInput({ icon: Icon, children }) {
  return (
    <div className="relative">
      <Icon
        className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
        aria-hidden="true"
      />
      {children}
    </div>
  );
}
