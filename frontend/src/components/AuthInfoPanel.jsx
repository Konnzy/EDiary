import { BarChart3, CalendarDays, MessageCircle, Pencil } from "lucide-react";
import { motion } from "framer-motion";

const features = [
  {
    icon: BarChart3,
    title: "Аналітика успішності",
    description: "Відстежуйте прогрес у реальному часі",
  },
  {
    icon: CalendarDays,
    title: "Актуальний розклад",
    description: "Завжди знайте про наступний урок",
  },
  {
    icon: MessageCircle,
    title: "Швидкий зв'язок",
    description: "Прямий чат з вчителями та батьками",
  },
];

export function AuthInfoPanel() {
  return (
    <motion.section
      className="hidden items-center justify-center px-8 py-12 lg:flex"
      initial={{ opacity: 0, x: -14 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ duration: 0.42, ease: "easeOut" }}
      aria-labelledby="intro-title"
    >
      <div className="w-full max-w-[440px]">
        <div className="mb-7 flex justify-center" aria-hidden="true">
          <Pencil
            className="h-[92px] w-[92px] fill-slate-600 stroke-slate-400"
            strokeWidth={2}
          />
        </div>

        <div className="text-center">
          <h2 id="intro-title" className="text-[2rem] font-bold leading-tight text-slate-800">
            Ваш шлях до знань
          </h2>
          <p className="mx-auto mt-4 max-w-[360px] text-[1.02rem] leading-7 text-slate-600">
            Зручний інструмент для моніторингу успішності, та спілкування з викладачами в одному місці.
          </p>
        </div>

        <div className="mt-10 space-y-5">
          {features.map((feature, index) => {
            const Icon = feature.icon;

            return (
              <motion.div
                key={feature.title}
                className="flex items-center gap-5 rounded-xl border border-slate-200/85 bg-white px-5 py-4 shadow-[0_12px_32px_-26px_rgba(15,23,42,0.45)] transition-[border-color,background-color,box-shadow] duration-200 hover:border-slate-300 hover:bg-slate-50 hover:shadow-[0_18px_38px_-28px_rgba(15,23,42,0.55)]"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.28, delay: 0.12 + index * 0.06 }}
                whileHover={{ y: -2, transition: { duration: 0.18, ease: "easeOut" } }}
              >
                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-slate-600">
                  <Icon className="h-5 w-5" strokeWidth={2.2} aria-hidden="true" />
                </div>
                <div>
                  <h3 className="text-[0.98rem] font-semibold text-slate-700">
                    {feature.title}
                  </h3>
                  <p className="mt-0.5 text-[0.92rem] leading-5 text-slate-500">
                    {feature.description}
                  </p>
                </div>
              </motion.div>
            );
          })}
        </div>
      </div>
    </motion.section>
  );
}
