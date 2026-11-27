import { navigate } from "@/lib/navigation.js";
import { motion } from "framer-motion";
import {
  Bell,
  CheckCircle2,
  Database,
  FileText,
  Lock,
  ShieldCheck,
  UserCheck,
} from "lucide-react";
import { AuthShell } from "@/components/AuthShell.jsx";
import { Button } from "@/components/ui/button.jsx";
import { Card, CardContent, CardHeader } from "@/components/ui/card.jsx";

const terms = [
  {
    icon: ShieldCheck,
    title: "Доступ до системи",
    description:
      "Обліковий запис створюється після перевірки даних навчальним закладом. Користувач відповідає за актуальність інформації, поданої у заявці.",
  },
  {
    icon: Lock,
    title: "Конфіденційність",
    description:
      "Персональні дані використовуються лише для організації навчального процесу, комунікації та адміністрування електронного щоденника.",
  },
  {
    icon: CheckCircle2,
    title: "Правила користування",
    description:
      "Заборонено передавати доступ третім особам, змінювати чужі дані або використовувати систему не за призначенням.",
  },
  {
    icon: Database,
    title: "Обробка персональних даних",
    description:
      "Дані обробляються для ідентифікації користувача, надання доступу до освітніх сервісів та виконання вимог законодавства України.",
  },
  {
    icon: Bell,
    title: "Email-сповіщення",
    description:
      "Система може надсилати листи щодо статусу заявки, відновлення доступу, важливих змін у розкладі або службових повідомлень.",
  },
  {
    icon: UserCheck,
    title: "Відповідальність користувача",
    description:
      "Користувач зобов’язаний зберігати конфіденційність облікових даних і повідомляти адміністрацію про підозрілу активність.",
  },
];

const lastUpdated = "10 вересня 2026 року";

export function TermsOfUsePage() {
  return (
    <AuthShell wide>
      <motion.div
        initial={{ opacity: 0, y: 18, scale: 0.985 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.42, delay: 0.08, ease: [0.22, 1, 0.36, 1] }}
      >
        <Card
          aria-labelledby="terms-title"
          className="flex max-h-[calc(100vh-6.75rem)] flex-col px-6 py-6 sm:px-8 sm:py-7"
        >
          <CardHeader className="shrink-0 text-center">
            <div
              className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-slate-100 text-slate-700"
              aria-hidden="true"
            >
              <FileText className="h-6 w-6" strokeWidth={2} />
            </div>
            <h1 id="terms-title" className="text-[1.45rem] font-bold leading-tight text-slate-800 sm:text-[1.6rem]">
              Умови користування
            </h1>
            <p className="pt-1 text-[0.84rem] leading-5 text-slate-500">
              Основні правила доступу та роботи з електронним щоденником навчального закладу
            </p>
          </CardHeader>

          <CardContent className="mt-5 min-h-0 overflow-y-auto pr-1">
            <div className="space-y-3">
              {terms.map((item) => {
                const Icon = item.icon;

                return (
                  <section
                    key={item.title}
                    className="flex gap-3 rounded-lg border border-slate-200 bg-slate-50/70 px-3.5 py-3"
                  >
                    <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-600">
                      <Icon className="h-[18px] w-[18px]" aria-hidden="true" />
                    </div>
                    <div>
                      <h2 className="text-[0.95rem] font-semibold text-slate-800">{item.title}</h2>
                      <p className="mt-1 text-[0.82rem] leading-5 text-slate-600">
                        {item.description}
                      </p>
                    </div>
                  </section>
                );
              })}
            </div>

            <div className="mt-5 rounded-md border border-slate-300 bg-slate-100/75 px-4 py-3 text-[0.82rem] leading-5 text-slate-600">
              Натискаючи «Я погоджуюсь з умовами користування», ви підтверджуєте ознайомлення з правилами використання системи та погоджуєтесь на обробку персональних даних відповідно до законодавства України.
            </div>

            <div className="mt-3 text-center text-[0.8rem] text-slate-500">
              Остання зміна: {lastUpdated}
            </div>

            <div className="mt-5">
              <Button
                type="button"
                className="h-11 w-full rounded-md text-[0.9rem] font-bold"
                onClick={() => {
                  navigate("request-registration");
                }}
              >
                Зрозуміло
              </Button>
            </div>
          </CardContent>
        </Card>
      </motion.div>
    </AuthShell>
  );
}
