export function AppFooter({ className = "" }) {
  return (
    <footer
      className={`border-t border-slate-200/80 py-3 text-center text-[0.76rem] text-slate-400 ${className}`}
    >
      © 2026 Електронний щоденник. Всі права захищено.
    </footer>
  );
}
