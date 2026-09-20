export const PROFILE_STORAGE_KEY = "ediary.user-profile.prototype.v1";
export const MAX_AVATAR_BYTES = 5 * 1024 * 1024;
export const AVATAR_TYPES = ["image/jpeg", "image/png"];

export const profileData = {
  STUDENT: {
    fullName: "Петренко Іван Олександрович",
    roleLabel: "Учень",
    initials: "ІП",
    personal: {
      firstName: "Іван",
      lastName: "Петренко",
      patronymic: "Олександрович",
      login: "student.test",
      email: "m.petrenko@example.com",
      phone: "+380 67 123 45 67",
    },
    education: {
      className: "9-А",
      registeredAt: "02.09.2024",
      teacherName: "Коваленко Олександр Іванович",
      teacherEmail: "o.kovalenko@school.ua",
      teacherPhone: "+380 67 765 43 21",
    },
  },
  PARENT: {
    fullName: "Іваненко Олена Петрівна",
    roleLabel: "Батьки",
    initials: "ОІ",
    personal: {
      firstName: "Олена",
      lastName: "Іваненко",
      patronymic: "Петрівна",
      login: "parent.ivanenko",
      email: "parent.ivanenko@school.edu.ua",
      phone: "+380 67 222 00 01",
    },
    children: [
      {
        id: "artem-ivanenko",
        fullName: "Іваненко Артем",
        className: "9-А",
        teacherName: "Коваленко Олександр Іванович",
        teacherEmail: "o.kovalenko@school.ua",
        teacherPhone: "+380 67 765 43 21",
      },
      {
        id: "sofia-ivanenko",
        fullName: "Іваненко Софія",
        className: "5-Б",
        teacherName: "Бондар Наталія Романівна",
        teacherEmail: "n.bondar@school.ua",
        teacherPhone: "+380 67 321 09 87",
      },
    ],
  },
  TEACHER: {
    fullName: "Коваленко Олександр Іванович",
    roleLabel: "Вчитель",
    initials: "ОК",
    personal: {
      firstName: "Олександр",
      lastName: "Коваленко",
      patronymic: "Іванович",
      login: "teacher.test",
      email: "o.kovalenko@school.ua",
      phone: "+380 67 765 43 21",
    },
    professional: {
      position: "Вчитель математики",
      subjects: ["Математика", "Алгебра", "Геометрія"],
      classes: ["9-А", "9-Б", "10-А"],
      homeroomClass: "9-А клас",
      registeredAt: "26.08.2024",
    },
  },
};

export const defaultNotificationSettings = {
  STUDENT: {
    newGrade: true,
    newHomework: true,
    absence: true,
  },
  PARENT: {
    newGrade: true,
    newHomework: true,
    absence: true,
  },
  TEACHER: {
    newMessage: true,
    scheduleChange: true,
  },
};

export function readProfilePreferences(role) {
  const fallback = {
    avatar: "",
    phone: profileData[role]?.personal.phone || "",
    selectedChildId: profileData[role]?.children?.[0]?.id || "",
    notifications: defaultNotificationSettings[role] || {},
  };

  try {
    const saved = JSON.parse(window.localStorage.getItem(`${PROFILE_STORAGE_KEY}.${role}`) || "null");
    return normalizeProfilePreferences(saved, fallback);
  } catch {
    return fallback;
  }
}

export function writeProfilePreferences(role, preferences) {
  window.localStorage.setItem(
    `${PROFILE_STORAGE_KEY}.${role}`,
    JSON.stringify(normalizeProfilePreferences(preferences, {
      avatar: "",
      phone: profileData[role]?.personal.phone || "",
      selectedChildId: profileData[role]?.children?.[0]?.id || "",
      notifications: defaultNotificationSettings[role] || {},
    })),
  );
}

export function normalizeProfilePreferences(value, fallback) {
  const source = value && typeof value === "object" ? value : {};
  const notifications = source.notifications && typeof source.notifications === "object"
    ? source.notifications
    : {};

  return {
    avatar: typeof source.avatar === "string" ? source.avatar : fallback.avatar,
    phone: typeof source.phone === "string" ? source.phone : fallback.phone,
    selectedChildId: typeof source.selectedChildId === "string" ? source.selectedChildId : fallback.selectedChildId,
    notifications: Object.fromEntries(
      Object.entries(fallback.notifications).map(([key, defaultValue]) => [
        key,
        typeof notifications[key] === "boolean" ? notifications[key] : defaultValue,
      ]),
    ),
  };
}

export function validateAvatarFile(file) {
  if (!file) return "Оберіть файл зображення.";
  if (!AVATAR_TYPES.includes(file.type)) return "Підтримуються лише файли JPG або PNG.";
  if (file.size > MAX_AVATAR_BYTES) return "Розмір зображення не має перевищувати 5 МБ.";
  return "";
}

export function normalizePhone(value) {
  return value.trim().replace(/\s+/g, " ");
}

export function validatePhone(value) {
  const normalized = normalizePhone(value);
  if (!normalized) return "Введіть номер телефону.";
  const digits = normalized.replace(/\D/g, "");
  if (digits.length < 10 || digits.length > 15) return "Введіть коректний номер телефону.";
  return "";
}

export function validatePasswordChange(currentPassword, newPassword, confirmation) {
  if (!currentPassword) return "Введіть поточний пароль.";
  if (!newPassword) return "Введіть новий пароль.";
  if (newPassword.length < 8) return "Новий пароль має містити щонайменше 8 символів.";
  if (new TextEncoder().encode(newPassword).length > 72) return "Новий пароль надто довгий: максимум 72 байти у UTF-8.";
  if (!/[a-zA-Zа-яА-ЯіІїЇєЄґҐ]/.test(newPassword) || !/\d/.test(newPassword)) {
    return "Новий пароль має містити літери та цифри.";
  }
  if (currentPassword === newPassword) return "Новий пароль має відрізнятися від поточного.";
  if (newPassword !== confirmation) return "Паролі не збігаються.";
  return "";
}
