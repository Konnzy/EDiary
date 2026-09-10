import { AppFooter } from "@/components/AppFooter.jsx";
import { AuthInfoPanel } from "@/components/AuthInfoPanel.jsx";

export function AuthShell({ children, wide = false }) {
  return (
    <div className="flex h-screen flex-col overflow-hidden bg-slate-50 text-slate-700">
      <main className="grid min-h-0 flex-1 overflow-y-auto lg:grid-cols-2 lg:bg-[linear-gradient(90deg,#f2f4f7_0%,#f2f4f7_49.9%,#f8fafc_50%,#f8fafc_100%)]">
        <AuthInfoPanel />

        <section className="flex min-h-0 items-start justify-center px-4 py-4 sm:px-6 lg:px-8">
          <div className={wide ? "my-auto w-full max-w-[610px]" : "my-auto w-full max-w-[430px]"}>
            {children}
          </div>
        </section>
      </main>
      <AppFooter />
    </div>
  );
}
