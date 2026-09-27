import {
  CalendarDays,
  Home,
  Mail,
  NotebookText,
  User,
  Users,
} from "lucide-react";

export const teacherNavigationItems = [
  { label: "Головна", icon: Home, route: "teacher" },
  { label: "Розклад", icon: CalendarDays, route: "teacher/schedule" },
  { label: "Журнал", icon: NotebookText, route: "teacher/journal" },
  { label: "Учні", icon: Users, route: "teacher/students" },
  { label: "Повідомлення", shortLabel: "Повідомл.", icon: Mail, route: "teacher/messages" },
  { label: "Профіль", icon: User, route: "teacher/profile" },
];
