import { navigate } from "@/lib/navigation.js";
import { Link } from "react-router-dom";
import { useId, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { Eye, EyeOff, GraduationCap, Loader2, Lock, User } from "lucide-react";
import { Alert } from "@/components/ui/alert.jsx";
import { Button } from "@/components/ui/button.jsx";
import { Card, CardContent, CardHeader } from "@/components/ui/card.jsx";
import { Checkbox } from "@/components/ui/checkbox.jsx";
import { Input } from "@/components/ui/input.jsx";
import { ApiError, authApi } from "@/lib/api.js";

export function LoginForm() {
  const loginId = useId();
  const passwordId = useId();
  const rememberId = useId();

  const [login, setLogin] = useState("");
  const [password, setPassword] = useState("");
  const [rememberMe, setRememberMe] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [authError, setAuthError] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setIsLoading(true);
    setAuthError(false);

    try {
      const response = await authApi.login({ login: login.trim(), password });
      navigate({
        TEACHER: "teacher",
        PARENT: "parent",
        STUDENT: "student",
      }[response.role] || "login");
    } catch (error) {
      setAuthError(error instanceof ApiError ? error.message : "Не вдалося виконати вхід.");
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
      <Card aria-labelledby="login-title" className="px-7 py-7 sm:px-9 sm:py-8">
        <CardHeader className="items-center text-center">
          <div className="mb-3 flex h-16 w-16 items-center justify-center text-slate-900" aria-hidden="true">
            <GraduationCap className="h-14 w-14 stroke-slate-900" strokeWidth={2.1} />
          </div>
          <h1 id="login-title" className="text-[1.38rem] font-bold leading-tight tracking-normal text-slate-800">
            Електронний щоденник
          </h1>
          <p className="pt-2 text-[0.9rem] font-normal text-slate-500">
            Вхід до системи
          </p>
        </CardHeader>

        <CardContent className="mt-5">
          <form className="space-y-4" onSubmit={handleSubmit} noValidate>
            <AnimatePresence initial={false}>
              {authError ? (
                <motion.div
                  initial={{ opacity: 0, y: -8, height: 0 }}
                  animate={{ opacity: 1, y: 0, height: "auto" }}
                  exit={{ opacity: 0, y: -8, height: 0 }}
                  transition={{ duration: 0.24, ease: "easeOut" }}
                >
                  <Alert>{authError}</Alert>
                </motion.div>
              ) : null}
            </AnimatePresence>

            <div className="space-y-2">
              <label htmlFor={loginId} className="text-[0.82rem] font-medium text-slate-600">
                Логін
              </label>
              <div className="relative">
                <User
                  className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
                  aria-hidden="true"
                />
                <Input
                  id={loginId}
                  name="login"
                  type="text"
                  autoComplete="username"
                  value={login}
                  onChange={(event) => setLogin(event.target.value)}
                  placeholder="Введіть ваш логін"
                  className="pl-10"
                  aria-invalid={authError}
                  aria-describedby={authError ? "login-error" : undefined}
                  required
                />
              </div>
            </div>

            <div className="space-y-2">
              <label htmlFor={passwordId} className="text-[0.82rem] font-medium text-slate-600">
                Пароль
              </label>
              <div className="relative">
                <Lock
                  className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
                  aria-hidden="true"
                />
                <Input
                  id={passwordId}
                  name="password"
                  type={showPassword ? "text" : "password"}
                  autoComplete="current-password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder="••••••••"
                  className="pl-10 pr-12"
                  aria-invalid={authError}
                  aria-describedby={authError ? "login-error" : undefined}
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
            </div>

            <p id="login-error" className="sr-only">
              Невірний логін або пароль. Спробуйте ще раз.
            </p>

            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <Checkbox
                  id={rememberId}
                  checked={rememberMe}
                  onCheckedChange={setRememberMe}
                />
                <label
                  htmlFor={rememberId}
                  className="cursor-pointer select-none text-[0.82rem] text-slate-600 transition-colors hover:text-slate-900"
                >
                  Запам’ятати мене
                </label>
              </div>
              <Link
                to="/forgot-password"
                className="text-[0.82rem] font-medium text-slate-500 underline-offset-4 transition-all duration-200 hover:text-primary hover:underline focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                Забули пароль?
              </Link>
            </div>

            <Button type="submit" className="h-12 w-full rounded-md text-[0.95rem] font-bold" disabled={isLoading}>
              {isLoading ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
                  Вхід...
                </>
              ) : (
                "Увійти"
              )}
            </Button>

          </form>

          <div className="mt-6 border-t border-slate-200 pt-5">
            <p className="text-center text-[0.82rem] text-slate-500">
              Ще не маєте акаунту?{" "}
              <Link
                to="/request-registration"
                className="font-semibold text-slate-700 underline-offset-4 transition-all duration-200 hover:text-primary hover:underline focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                Подати заявку
              </Link>
            </p>
          </div>
        </CardContent>
      </Card>
    </motion.div>
  );
}
