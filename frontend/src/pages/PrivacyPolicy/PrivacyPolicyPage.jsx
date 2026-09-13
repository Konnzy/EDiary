import { motion } from "framer-motion";
import {
  Bell,
  Database,
  Eye,
  FileLock2,
  ShieldCheck,
  UserRoundCheck,
} from "lucide-react";
import { AuthShell } from "@/components/AuthShell.jsx";
import { Button } from "@/components/ui/button.jsx";
import { Card, CardContent, CardHeader } from "@/components/ui/card.jsx";

const privacySections = [
  {
    icon: Database,
    title: "Які дані обробляються",
    description:
      "Система може зберігати ПІБ, дату народження, контактні дані, роль, відомості про клас, розклад, оцінки, відвідування, домашні завдання та службову інформацію облікового запису.",
  },
  {
    icon: UserRoundCheck,
    title: "Мета обробки",
    description:
      "Дані використовуються для підтвердження особи, надання доступу відповідно до ролі, організації навчального процесу, комунікації та формування навчальної інформації.",
  },
  {
    icon: Eye,
    title: "Доступ до інформації",
    description:
      "Користувачі отримують доступ лише до даних, необхідних для їхньої ролі. Батьки та законні представники можуть переглядати інформацію тільки про прив’язаних до їхнього профілю дітей.",
  },
  {
    icon: FileLock2,
    title: "Захист і зберігання",
    description:
      "Для захисту даних застосовуються розмежування доступу, хешування паролів, серверні сесії та перевірка введеної інформації. Дані зберігаються протягом строку, потрібного для роботи системи й ведення навчальної історії.",
  },
  {
    icon: Bell,
    title: "Електронні повідомлення",
    description:
      "Електронна пошта використовується для надсилання результатів розгляду заявки, даних для входу, відновлення доступу та важливих системних сповіщень.",
  },
  {
    icon: ShieldCheck,
    title: "Права користувача",
    description:
      "Користувач може звернутися до адміністрації навчального закладу для уточнення, виправлення або отримання інформації про обробку своїх персональних даних.",
  },
];

const lastUpdated = "10 вересня 2026 року";

export function PrivacyPolicyPage() {
  return (
    <AuthShell wide>
      <motion.div
        initial={{ opacity: 0, y: 18, scale: 0.985 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.42, delay: 0.08, ease: [0.22, 1, 0.36, 1] }}
      >
        <Card
          aria-labelledby="privacy-title"
          className="flex max-h-[calc(100vh-6.75rem)] flex-col px-6 py-6 sm:px-8 sm:py-7"
        >
          <CardHeader className="shrink-0 text-center">
            <div
              className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-slate-100 text-slate-700"
              aria-hidden="true"
            >
              <FileLock2 className="h-6 w-6" strokeWidth={2} />
            </div>
            <h1 id="privacy-title" className="text-[1.45rem] font-bold leading-tight text-slate-800 sm:text-[1.6rem]">
              Політика конфіденційності
            </h1>
            <p className="pt-1 text-[0.84rem] leading-5 text-slate-500">
              Основні правила обробки та захисту персональних даних в електронному щоденнику
            </p>
          </CardHeader>

          <CardContent className="mt-5 min-h-0 overflow-y-auto pr-1">
            <div className="space-y-3">
              {privacySections.map((item) => {
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
              Подаючи заявку на реєстрацію, ви підтверджуєте, що ознайомилися з цією політикою та погоджуєтеся на обробку зазначених даних для роботи електронного щоденника.
            </div>

            <div className="mt-3 text-center text-[0.8rem] text-slate-500">
              Остання зміна: {lastUpdated}
            </div>

            <div className="mt-5">
              <Button
                type="button"
                className="h-11 w-full rounded-md text-[0.9rem] font-bold"
                onClick={() => {
                  window.location.hash = "request-registration";
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
