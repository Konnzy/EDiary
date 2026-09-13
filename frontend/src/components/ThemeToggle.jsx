import { useState } from "react";
import { Moon, Sun } from "lucide-react";
import { readTheme, setTheme } from "@/lib/theme.js";

export function ThemeToggle() {
  const [theme, updateTheme] = useState(readTheme);
  const dark = theme === "dark";

  function toggle() {
    const next = dark ? "light" : "dark";
    setTheme(next);
    updateTheme(next);
  }

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={dark ? "Увімкнути світлу тему" : "Увімкнути темну тему"}
      aria-pressed={dark}
      title={dark ? "Світла тема" : "Темна тема"}
      className="fixed bottom-4 right-4 z-40 flex h-11 w-11 items-center justify-center rounded-md border border-slate-300 bg-white text-slate-700 shadow-lg transition-colors hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 sm:bottom-5 sm:right-5"
    >
      {dark ? <Sun className="h-5 w-5" aria-hidden="true" /> : <Moon className="h-5 w-5" aria-hidden="true" />}
    </button>
  );
}
