import { useId, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { ArrowLeft, Loader2, Mail } from "lucide-react";
import { AuthShell } from "@/components/AuthShell.jsx";
import { Alert } from "@/components/ui/alert.jsx";
import { Button } from "@/components/ui/button.jsx";
import { Input } from "@/components/ui/input.jsx";
import { cn } from "@/lib/utils.js";
import { authApi } from "@/lib/api.js";
import { hasEmailFormat } from "@/lib/emailValidation.js";

function validateEmail(email) {
  const address = email.trim();
  if (!address) {
    return "Будь ласка, введіть електронну пошту.";
  }

  if (!hasEmailFormat(address)) {
    return "Некоректний формат електронної пошти.";
  }

  return "";
}

export function ForgetPasswordPage({ administrator = false }) {
  const emailId = useId();
  const [email, setEmail] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [serverError, setServerError] = useState("");
  const [notice, setNotice] = useState("");

  const error = serverError || (submitted ? validateEmail(email) : "");

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSubmitted(true);
    setServerError("");
    setNotice("");
    if (validateEmail(email)) return;
    setIsLoading(true);
    try {
      const response = await authApi.forgotPassword(email.trim());
      setNotice(response.message);
    } catch (error) {
      setServerError(error.message || "Не вдалося виконати запит.");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthShell>
      <motion.div
        initial={{ opacity: 0, y: 18, scale: 0.985 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.42, delay: 0.08, ease: [0.22, 1, 0.36, 1] }}
      >
        <section
          aria-labelledby="forgot-title"
          className="rounded-xl border border-slate-200/90 bg-white px-7 py-8 shadow-[0_10px_40px_-32px_rgba(15,23,42,0.55)] ring-1 ring-slate-900/[0.015] sm:px-8 sm:py-9"
        >
          <header>
            <h1 id="forgot-title" className="text-[1.85rem] font-bold leading-tight text-slate-900">
              Відновлення пароля
            </h1>
            <p className="mt-4 text-[1.05rem] leading-7 text-slate-500">
              Введіть електронну пошту для отримання інструкцій
            </p>
          </header>

          <div className="mt-8 border-l-4 border-slate-300 bg-slate-50 px-5 py-5 text-[0.95rem] leading-6 text-slate-600">
            Ми надішлемо лист із посиланням для відновлення пароля на вказану адресу.
          </div>

          <form className="mt-7 space-y-5" onSubmit={handleSubmit} noValidate>
            {notice ? <p role="status" className="rounded-md border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">{notice}</p> : null}
            <AnimatePresence initial={false}>
              {error ? (
                <motion.div
                  initial={{ opacity: 0, y: -8, height: 0 }}
                  animate={{ opacity: 1, y: 0, height: "auto" }}
                  exit={{ opacity: 0, y: -8, height: 0 }}
                  transition={{ duration: 0.24, ease: "easeOut" }}
                >
                  <Alert>{error}</Alert>
                </motion.div>
              ) : null}
            </AnimatePresence>

            <div className="space-y-2">
              <label htmlFor={emailId} className="text-[0.92rem] font-medium text-slate-600">
                Електронна пошта
              </label>
              <div className="relative">
                <Mail
                  className="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-400"
                  aria-hidden="true"
                />
                <Input
                  id={emailId}
                  name="email"
                  type="email"
                  autoComplete="email"
                  value={email}
                  onChange={(event) => {
                    setEmail(event.target.value);
                    setServerError("");
                    setNotice("");
                    if (submitted) {
                      setSubmitted(false);
                    }
                  }}
                  placeholder="example@school.edu.ua"
                  className={cn("h-12 pl-11 text-[0.9rem]", error && "border-red-400 bg-red-50/30")}
                  aria-invalid={Boolean(error)}
                  aria-describedby={error ? "forgot-error" : undefined}
                />
              </div>
            </div>

            <p id="forgot-error" className="sr-only">
              {error}
            </p>

            <Button
              type="submit"
              className="h-12 w-full rounded-md text-[0.95rem] font-bold"
              disabled={isLoading}
            >
              {isLoading ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
                  Надсилання...
                </>
              ) : (
                "Надіслати інструкції"
              )}
            </Button>

          </form>

          <div className="mt-9 flex justify-center">
            <a
              href={administrator ? "#admin" : "#login"}
              className="inline-flex items-center gap-5 text-[0.95rem] font-medium text-slate-500 transition-colors hover:text-slate-900 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
            >
              <ArrowLeft className="h-5 w-5" aria-hidden="true" />
              Повернутися до входу
            </a>
          </div>
        </section>
      </motion.div>
    </AuthShell>
  );
}
