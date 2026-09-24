import { useId, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import {
  ArrowLeft,
  Eye,
  EyeOff,
  KeyRound,
  Loader2,
  Lock,
  ShieldCheck,
  UserCog,
} from "lucide-react";
import { AppFooter } from "@/components/AppFooter.jsx";
import { Alert } from "@/components/ui/alert.jsx";
import { Button } from "@/components/ui/button.jsx";
import { Card, CardContent, CardHeader } from "@/components/ui/card.jsx";
import { Input } from "@/components/ui/input.jsx";
import { ApiError, adminAuthApi } from "@/lib/api.js";
import { cn } from "@/lib/utils.js";

function formatAccessCode(value) {
  const code = value.replace(/[^a-zA-Z0-9]/g, "").toUpperCase().slice(0, 6);
  const firstPart = code.slice(0, 3);
  const secondPart = code.slice(3, 6);

  return secondPart ? `${firstPart}-${secondPart}` : firstPart;
}

export function AdminLoginPage() {
  const loginId = useId();
  const passwordId = useId();
  const accessCodeId = useId();

  const [login, setLogin] = useState("");
  const [password, setPassword] = useState("");
  const [accessCode, setAccessCode] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [authError, setAuthError] = useState("");

  const isAccessCodeValid = /^[A-Z0-9]{3}-[A-Z0-9]{3}$/.test(accessCode);
  const showErrors = submitted && (!login.trim() || !password || !isAccessCodeValid);

  const handleAccessCodeChange = (value) => {
    setAccessCode(formatAccessCode(value));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSubmitted(true);
    setAuthError("");

    if (!login.trim() || !password || !isAccessCodeValid) {
      return;
    }

    setIsLoading(true);

    try {
      await adminAuthApi.login({ login: login.trim(), password, accessCode });
      window.location.hash = "admin/home";
    } catch (error) {
      setAuthError(error instanceof ApiError ? error.message : "Не вдалося виконати вхід.");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex h-screen flex-col overflow-hidden bg-slate-50 text-slate-800">
      <main className="flex min-h-0 flex-1 items-center justify-center overflow-y-auto px-4 py-4 sm:px-6 lg:px-8">
        <motion.div
          initial={{ opacity: 0, y: 18, scale: 0.985 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          transition={{ duration: 0.42, delay: 0.08, ease: [0.22, 1, 0.36, 1] }}
          className="w-full max-w-[430px]"
        >
          <Card aria-labelledby="admin-login-title" className="px-7 py-7 sm:px-9 sm:py-8">
            <CardHeader className="items-center text-center">
              <div
                className="mb-3 flex h-16 w-16 items-center justify-center rounded-xl border border-slate-200 bg-slate-50 text-slate-900"
                aria-hidden="true"
              >
                <ShieldCheck className="h-9 w-9" strokeWidth={2.1} />
              </div>
              <p className="text-[0.78rem] font-bold uppercase tracking-[0.14em] text-slate-400">
                Службовий доступ
              </p>
              <h1 id="admin-login-title" className="text-[1.45rem] font-bold leading-tight text-slate-900">
                Панель адміністратора
              </h1>
              <p className="pt-1 text-[0.88rem] leading-6 text-slate-500">
                Вхід для адміністрування облікових записів, ролей та структури закладу
              </p>
            </CardHeader>

            <CardContent className="mt-7">
              <div className="mb-5 rounded-md border border-slate-300 bg-slate-100/75 px-4 py-3 text-[0.82rem] leading-5 text-slate-600">
                Доступ до цієї панелі призначений лише для уповноважених адміністраторів навчального закладу.
              </div>

              <form className="space-y-4" onSubmit={handleSubmit} noValidate>
                <AnimatePresence initial={false}>
                  {showErrors || authError ? (
                    <motion.div
                      initial={{ opacity: 0, y: -8, height: 0 }}
                      animate={{ opacity: 1, y: 0, height: "auto" }}
                      exit={{ opacity: 0, y: -8, height: 0 }}
                      transition={{ duration: 0.24, ease: "easeOut" }}
                    >
                      <Alert>
                        {authError || "Заповніть логін, пароль та службовий код у форматі XXX-XXX."}
                      </Alert>
                    </motion.div>
                  ) : null}
                </AnimatePresence>

                <Field id={loginId} label="Логін адміністратора">
                  <IconInput icon={UserCog}>
                    <Input
                      id={loginId}
                      name="adminLogin"
                      type="text"
                      autoComplete="username"
                      value={login}
                      onChange={(event) => setLogin(event.target.value)}
                      placeholder="Введіть службовий логін"
                      className={cn("pl-10", submitted && !login.trim() && "border-red-400 bg-red-50/30")}
                      aria-invalid={Boolean(submitted && !login.trim())}
                      required
                    />
                  </IconInput>
                </Field>

                <Field id={passwordId} label="Пароль">
                  <div className="relative">
                    <Lock
                      className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
                      aria-hidden="true"
                    />
                    <Input
                      id={passwordId}
                      name="adminPassword"
                      type={showPassword ? "text" : "password"}
                      autoComplete="current-password"
                      value={password}
                      onChange={(event) => setPassword(event.target.value)}
                      placeholder="••••••••"
                      className={cn("pl-10 pr-12", submitted && !password && "border-red-400 bg-red-50/30")}
                      aria-invalid={Boolean(submitted && !password)}
                      required
                    />
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      onClick={() => setShowPassword((current) => !current)}
                      className="absolute right-1.5 top-1/2 h-8 w-8 -translate-y-1/2 rounded-lg text-slate-400 hover:bg-transparent hover:text-slate-700"
                      aria-label={showPassword ? "Приховати пароль" : "Показати пароль"}
                      aria-pressed={showPassword}
                    >
                      {showPassword ? (
                        <EyeOff className="h-4 w-4" aria-hidden="true" />
                      ) : (
                        <Eye className="h-4 w-4" aria-hidden="true" />
                      )}
                    </Button>
                  </div>
                </Field>

                <Field id={accessCodeId} label="Службовий код доступу">
                  <IconInput icon={KeyRound}>
                    <Input
                      id={accessCodeId}
                      name="accessCode"
                      type="text"
                      inputMode="text"
                      autoComplete="one-time-code"
                      value={accessCode}
                      onChange={(event) => handleAccessCodeChange(event.target.value)}
                      placeholder="XXX-XXX"
                      maxLength={7}
                      className={cn("pl-10", submitted && !isAccessCodeValid && "border-red-400 bg-red-50/30")}
                      aria-invalid={Boolean(submitted && !isAccessCodeValid)}
                      required
                    />
                  </IconInput>
                </Field>

                <Button type="submit" className="h-12 w-full rounded-md text-[0.95rem] font-bold" disabled={isLoading}>
                  {isLoading ? (
                    <>
                      <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
                      Перевірка...
                    </>
                  ) : (
                    "Увійти до адмін-панелі"
                  )}
                </Button>

              </form>

              <div className="mt-8 flex justify-center">
                <a
                  href="#login"
                  className="inline-flex items-center gap-4 text-[0.9rem] font-medium text-slate-500 transition-colors hover:text-slate-900 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                >
                  <ArrowLeft className="h-5 w-5" aria-hidden="true" />
                  Повернутися до входу
                </a>
              </div>
            </CardContent>
          </Card>
        </motion.div>
      </main>
      <AppFooter />
    </div>
  );
}

function Field({ id, label, children }) {
  return (
    <div className="space-y-2">
      <label htmlFor={id} className="text-[0.82rem] font-medium text-slate-600">
        {label}
      </label>
      {children}
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
