import { useId, useMemo, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { ArrowLeft, Eye, EyeOff, Info, Lock } from "lucide-react";
import { AuthShell } from "@/components/AuthShell.jsx";
import { Alert } from "@/components/ui/alert.jsx";
import { Button } from "@/components/ui/button.jsx";
import { Input } from "@/components/ui/input.jsx";
import { cn } from "@/lib/utils.js";
import { authApi } from "@/lib/api.js";
import { getPasswordStrength } from "@/lib/passwordStrength.js";

function validatePassword(password, confirmPassword) {
  if (!password) {
    return "Будь ласка, введіть пароль";
  }

  if (password.length < 8) {
    return "Пароль має містити щонайменше 8 символів";
  }

  if (new TextEncoder().encode(password).length > 72) return "Пароль надто довгий: максимум 72 байти у UTF-8";

  if (!/[a-zA-Zа-яА-ЯіІїЇєЄґҐ]/.test(password) || !/\d/.test(password)) {
    return "Пароль має містити літери та цифри";
  }

  if (!confirmPassword) {
    return "Будь ласка, підтвердіть пароль";
  }

  if (password !== confirmPassword) {
    return "Паролі не збігаються";
  }

  return "";
}

export function ResetPasswordPage({ token = "" }) {
  const passwordId = useId();
  const confirmPasswordId = useId();

  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [serverError, setServerError] = useState("");
  const [notice, setNotice] = useState("");
  const validToken = /^[A-Za-z0-9_-]{43}$/.test(token);

  const strength = useMemo(() => getPasswordStrength(password), [password]);
  const error = !validToken ? "Посилання недійсне. Запитайте нове посилання для відновлення пароля." : serverError || (submitted ? validatePassword(password, confirmPassword) : "");

  const handleSubmit = async (event) => {
    event.preventDefault();
    const nextError = validatePassword(password, confirmPassword);

    setSubmitted(true);

    if (nextError || !validToken || isLoading || notice) return;
    setServerError("");
    setIsLoading(true);
    try {
      const response = await authApi.resetPassword(token, password);
      setNotice(response.message);
      setPassword("");
      setConfirmPassword("");
      setSubmitted(false);
      window.history.replaceState(null, "", "#reset-password");
    } catch (error) {
      setServerError(error.fieldErrors?.newPassword || error.message || "Не вдалося змінити пароль.");
    } finally {
      setIsLoading(false);
    }
  };

  const handlePasswordChange = (value) => {
    setPassword(value);
    if (submitted) setSubmitted(false);
  };

  const handleConfirmPasswordChange = (value) => {
    setConfirmPassword(value);
    if (submitted) setSubmitted(false);
  };

  return (
    <AuthShell>
      <motion.div
        initial={{ opacity: 0, y: 18, scale: 0.985 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.42, delay: 0.08, ease: [0.22, 1, 0.36, 1] }}
      >
        <section
          aria-labelledby="reset-title"
          className="rounded-xl border border-slate-200/90 bg-white px-7 py-8 shadow-[0_10px_40px_-32px_rgba(15,23,42,0.55)] ring-1 ring-slate-900/[0.015] sm:px-8 sm:py-9"
        >
          <header className="text-center">
            <h1 id="reset-title" className="text-[1.65rem] font-bold leading-tight text-slate-900">
              Встановлення нового пароля
            </h1>
            <p className="mx-auto mt-4 max-w-[300px] text-[0.98rem] leading-6 text-slate-500">
              Створіть новий пароль для вашого облікового запису
            </p>
          </header>

          <form className="mt-8 space-y-5" onSubmit={handleSubmit} noValidate>
            {notice ? <p role="status" className="rounded-md border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">{notice}</p> : null}
            <fieldset disabled={isLoading || Boolean(notice) || !validToken} className="space-y-5">
            <PasswordField
              id={passwordId}
              label="Новий пароль"
              value={password}
              visible={showPassword}
              onChange={handlePasswordChange}
              onToggle={() => setShowPassword((current) => !current)}
              invalid={Boolean(error)}
              ariaDescribedBy={error ? "reset-password-error" : undefined}
            />

            <PasswordField
              id={confirmPasswordId}
              label="Підтвердження пароля"
              value={confirmPassword}
              visible={showConfirmPassword}
              onChange={handleConfirmPasswordChange}
              onToggle={() => setShowConfirmPassword((current) => !current)}
              invalid={Boolean(error)}
              ariaDescribedBy={error ? "reset-password-error" : undefined}
            />

            <div className="space-y-2">
              <div className="flex items-center justify-between text-[0.78rem] font-medium text-slate-500">
                <span>Надійність пароля</span>
                <span>{strength.label}</span>
              </div>
              <div className="h-1.5 overflow-hidden rounded-full bg-slate-100">
                <div className={cn("h-full rounded-full transition-all duration-300", strength.width, strength.color)} />
              </div>
              <p className="text-[0.78rem] leading-5 text-slate-400">
                Пароль має містити щонайменше 8 символів, включаючи літери та цифри
              </p>
            </div>

            <AnimatePresence initial={false}>
              {error ? (
                <motion.div
                  initial={{ opacity: 0, y: -8, height: 0 }}
                  animate={{ opacity: 1, y: 0, height: "auto" }}
                  exit={{ opacity: 0, y: -8, height: 0 }}
                  transition={{ duration: 0.24, ease: "easeOut" }}
                >
                  <Alert id="reset-password-error">{error}</Alert>
                </motion.div>
              ) : null}
            </AnimatePresence>

            <Button type="submit" disabled={isLoading || Boolean(notice) || !validToken} className="h-12 w-full rounded-md text-[0.95rem] font-bold">
              {isLoading ? "Збереження..." : "Зберегти пароль"}
            </Button>
            </fieldset>
          </form>

          <div className="mt-8 flex justify-center">
            <a
              href="#login"
              className="inline-flex items-center gap-5 text-[0.95rem] font-medium text-slate-500 transition-colors hover:text-slate-900 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
            >
              <ArrowLeft className="h-5 w-5" aria-hidden="true" />
              Повернутися до входу
            </a>
          </div>

          <div className="mt-8 border-t border-slate-200 pt-6">
            <div className="flex items-start gap-3 rounded-md border border-slate-200 bg-slate-50 px-4 py-4 text-[0.82rem] leading-5 text-slate-500">
              <Info className="mt-0.5 h-4 w-4 shrink-0 fill-slate-500 text-white" aria-hidden="true" />
              <p>Якщо у вас виникли проблеми, зверніться до системного адміністратора вашої школи.</p>
            </div>
          </div>
        </section>
      </motion.div>
    </AuthShell>
  );
}

function PasswordField({ id, label, value, visible, onChange, onToggle, invalid, ariaDescribedBy }) {
  return (
    <div className="space-y-2">
      <label htmlFor={id} className="text-[0.92rem] font-medium text-slate-600">
        {label}
      </label>
      <div className="relative">
        <Lock
          className="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-500"
          aria-hidden="true"
        />
        <Input
          id={id}
          type={visible ? "text" : "password"}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          placeholder="••••••••"
          className={cn("h-12 pl-11 pr-12 text-[0.9rem]", invalid && "border-red-400 bg-red-50/30")}
          aria-invalid={invalid}
          aria-describedby={ariaDescribedBy}
          autoComplete="new-password"
        />
        <Button
          type="button"
          variant="ghost"
          size="icon"
          onClick={onToggle}
          className="absolute right-1.5 top-1/2 h-8 w-8 -translate-y-1/2 rounded-lg text-slate-400 hover:bg-transparent hover:text-slate-700"
          aria-label={visible ? "Приховати пароль" : "Показати пароль"}
          aria-pressed={visible}
        >
          {visible ? (
            <EyeOff className="h-4 w-4" aria-hidden="true" />
          ) : (
            <Eye className="h-4 w-4" aria-hidden="true" />
          )}
        </Button>
      </div>
    </div>
  );
}
