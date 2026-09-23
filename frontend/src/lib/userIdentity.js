export const roleLabels = {
  STUDENT: "Учень",
  PARENT: "Батьки",
  TEACHER: "Вчитель",
};

export function formatShortName(profile) {
  return [profile?.lastName, profile?.firstName].filter(Boolean).join(" ");
}

export function profileInitials(profile) {
  return `${profile?.firstName?.[0] || ""}${profile?.lastName?.[0] || ""}`;
}
