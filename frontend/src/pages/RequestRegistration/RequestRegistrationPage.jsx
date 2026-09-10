import { AuthShell } from "@/components/AuthShell.jsx";
import { RequestRegistrationForm } from "@/pages/RequestRegistration/RequestRegistrationForm.jsx";

export function RequestRegistrationPage() {
  return (
    <AuthShell wide>
      <RequestRegistrationForm />
    </AuthShell>
  );
}
