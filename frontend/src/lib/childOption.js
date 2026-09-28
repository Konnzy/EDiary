export function childOptionLabel(child) {
  const fullName = child.fullName || [child.lastName, child.firstName, child.patronymic]
    .filter((part) => part?.trim()).join(" ");
  return `${fullName} · ${child.className || "Клас не призначено"}`;
}
