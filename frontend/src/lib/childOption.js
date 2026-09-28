export function childOptionLabel(child) {
  const name = child.lastName && child.firstName
    ? [child.lastName, child.firstName].filter((part) => part?.trim()).join(" ")
    : child.fullName?.trim().split(/\s+/).slice(0, 2).join(" ")
      || [child.lastName, child.firstName, child.patronymic].filter((part) => part?.trim()).join(" ");
  return `${name} · ${child.className || "Клас не призначено"}`;
}
