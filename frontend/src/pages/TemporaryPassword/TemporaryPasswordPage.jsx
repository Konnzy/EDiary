import { useContext, useState } from "react";
import { AuthShell } from "@/components/AuthShell.jsx";
import { Alert } from "@/components/ui/alert.jsx";
import { Button } from "@/components/ui/button.jsx";
import { Card, CardContent, CardHeader } from "@/components/ui/card.jsx";
import { Input } from "@/components/ui/input.jsx";
import { adminAuthApi, adminUsersApi, authApi, userProfileApi } from "@/lib/api.js";
import { CurrentUserContext } from "@/lib/currentUser.jsx";

export function TemporaryPasswordPage({ onChanged }) {
  const { session } = useContext(CurrentUserContext);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [busy, setBusy] = useState(null);
  const [error, setError] = useState("");
  const administrator = session.role === "ADMIN";
  const loginRoute = administrator ? "#admin" : "#login";

  async function changePassword(event) {
    event.preventDefault();
    if (busy) return;

    if (newPassword !== confirmation) {
      setError("Новий пароль і його підтвердження не збігаються.");
      return;
    }

    setBusy("change");
    setError("");
    try {
      const change = administrator ? adminUsersApi.changeOwnPassword : userProfileApi.changePassword;
      await change(currentPassword, newPassword);
      onChanged(session.role);
    } catch (error) {
      if (error.status === 401) {
        window.location.replace(loginRoute);
        return;
      }
      setError(error.fieldErrors?.currentPassword || error.fieldErrors?.newPassword ||
        error.message || "Не вдалося змінити пароль.");
    } finally {
      setBusy(null);
    }
  }

  async function logout() {
    if (busy) return;
    setBusy("logout");
    setError("");
    try {
      await (administrator ? adminAuthApi : authApi).logout();
      window.location.replace(loginRoute);
    } catch (error) {
      if (error.status === 401) {
        window.location.replace(loginRoute);
        return;
      }
      setError(error.message || "Не вдалося вийти із системи.");
    } finally {
      setBusy(null);
    }
  }

  return (
    <AuthShell>
      <Card aria-labelledby="temporary-password-title" className="px-7 py-8 sm:px-8">
        <CardHeader className="items-center text-center">
          <h1 id="temporary-password-title" className="text-xl font-bold text-slate-900">
            Зміна тимчасового пароля
          </h1>
          <p className="mt-2 text-sm text-slate-600">Щоб відкрити кабінет, встановіть новий пароль.</p>
        </CardHeader>
        <CardContent className="mt-6">
          <form onSubmit={changePassword} className="space-y-4">
            {error && <Alert>{error}</Alert>}
            <fieldset disabled={Boolean(busy)} className="space-y-4">
              <div className="space-y-1">
                <label htmlFor="temporary-password" className="text-sm font-medium">Тимчасовий пароль</label>
                <Input id="temporary-password" name="currentPassword" type="password" autoComplete="current-password"
                  required autoFocus value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} />
              </div>
              <div className="space-y-1">
                <label htmlFor="new-permanent-password" className="text-sm font-medium">Новий пароль</label>
                <Input id="new-permanent-password" name="newPassword" type="password" autoComplete="new-password"
                  aria-describedby="new-password-requirements" required minLength={8}
                  value={newPassword} onChange={(event) => setNewPassword(event.target.value)} />
                <p id="new-password-requirements" className="text-xs text-slate-600">
                  Щонайменше 8 символів, серед яких є літери та цифри.
                </p>
              </div>
              <div className="space-y-1">
                <label htmlFor="confirm-permanent-password" className="text-sm font-medium">Підтвердження пароля</label>
                <Input id="confirm-permanent-password" name="confirmPassword" type="password" autoComplete="new-password"
                  required value={confirmation} onChange={(event) => setConfirmation(event.target.value)} />
              </div>
              <Button type="submit" className="w-full">
                {busy === "change" ? "Збереження…" : "Змінити пароль"}
              </Button>
            </fieldset>
            <Button type="button" variant="ghost" onClick={logout} disabled={Boolean(busy)} className="w-full">
              {busy === "logout" ? "Вихід…" : "Вийти із системи"}
            </Button>
          </form>
        </CardContent>
      </Card>
    </AuthShell>
  );
}
