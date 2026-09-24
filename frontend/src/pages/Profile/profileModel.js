export const MAX_AVATAR_BYTES = 5 * 1024 * 1024;
export const AVATAR_TYPES = ["image/jpeg", "image/png"];

const roleLabels = { STUDENT: "Учень", PARENT: "Батьки", TEACHER: "Вчитель" };

function formatDate(value) {
  if (!value) return "Не вказано";
  const [year, month, day] = value.slice(0, 10).split("-");
  return `${day}.${month}.${year}`;
}

function display(value, fallback = "Не вказано") {
  return value || fallback;
}

export function formatPhoneForDisplay(value) {
  if (!value) return value;
  const digits = value.replace(/\D/g, "");
  let subscriberNumber;

  if (digits.startsWith("380")) {
    subscriberNumber = digits.slice(3);
  } else if (digits.length === 10 && digits.startsWith("0")) {
    subscriberNumber = digits.slice(1);
  } else if (digits.length === 9) {
    subscriberNumber = digits;
  } else {
    return value;
  }

  const groups = [2, 3, 2, 2];
  const parts = [];
  let offset = 0;
  for (const size of groups) {
    if (offset >= subscriberNumber.length) break;
    parts.push(subscriberNumber.slice(offset, offset + size));
    offset += size;
  }
  return [`+380`, ...parts].join(" ");
}

export function mapProfileResponse(response) {
  const { role, firstName, lastName, patronymic, login, email, phone, education, professional, children } = response;
  return {
    role,
    fullName: [lastName, firstName, patronymic].filter(Boolean).join(" "),
    roleLabel: roleLabels[role],
    initials: `${firstName?.[0] || ""}${lastName?.[0] || ""}`,
    avatarVersion: response.avatarVersion,
    notifications: response.notifications,
    personal: {
      firstName, lastName, patronymic, login, email, phone: formatPhoneForDisplay(phone),
      parentEmail: education?.parentEmail,
      parentPhone: formatPhoneForDisplay(education?.parentPhone),
    },
    education: education && {
      className: display(education.className, "Не призначено"),
      registeredAt: formatDate(response.registeredAt),
      teacherName: display(education.teacherName),
      teacherEmail: display(education.teacherEmail),
      teacherPhone: display(formatPhoneForDisplay(education.teacherPhone)),
    },
    professional: professional && {
      position: professional.position,
      subjects: professional.subjects,
      classes: professional.classes,
      homeroomClass: professional.homeroomClasses.length
        ? professional.homeroomClasses.join(", ") : "",
      registeredAt: formatDate(response.registeredAt),
    },
    children: children?.map((child) => ({
      id: String(child.id),
      fullName: child.fullName,
      className: display(child.className, "Не призначено"),
      teacherName: display(child.teacherName),
      teacherEmail: display(child.teacherEmail),
      teacherPhone: display(formatPhoneForDisplay(child.teacherPhone)),
    })) || [],
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
  if (!/^\+?[0-9 ()-]{10,20}$/.test(normalized) || digits.length < 10 || digits.length > 15) return "Введіть коректний номер телефону.";
  return "";
}

export function validatePasswordChange(currentPassword, newPassword, confirmation) {
  if (!currentPassword) return "Введіть поточний пароль.";
  if (!newPassword) return "Введіть новий пароль.";
  if (newPassword.length < 8) return "Новий пароль має містити щонайменше 8 символів.";
  if (new TextEncoder().encode(newPassword).length > 72) return "Новий пароль надто довгий: максимум 72 байти у UTF-8.";
  if (!/\p{L}/u.test(newPassword) || !/\d/.test(newPassword)) {
    return "Новий пароль має містити літери та цифри.";
  }
  if (currentPassword === newPassword) return "Новий пароль має відрізнятися від поточного.";
  if (newPassword !== confirmation) return "Паролі не збігаються.";
  return "";
}
