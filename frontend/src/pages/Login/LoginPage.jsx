import { AuthShell } from "@/components/AuthShell.jsx";
import { LoginForm } from "@/pages/Login/LoginForm.jsx";

export function LoginPage() {
  return (
    <AuthShell>
      <LoginForm />
    </AuthShell>
  );
}
