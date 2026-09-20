export function getPasswordScore(password) {
  let score = 0;

  if (password.length >= 8) score += 1;
  if (/[a-zA-Zа-яА-ЯіІїЇєЄґҐ]/.test(password)) score += 1;
  if (/\d/.test(password)) score += 1;
  if (/[^a-zA-Zа-яА-ЯіІїЇєЄґҐ\d]/.test(password)) score += 1;

  return score;
}

export function getPasswordStrength(password) {
  const score = getPasswordScore(password);

  if (score >= 4) return { label: "Висока", width: "w-full", color: "bg-emerald-500" };
  if (score >= 3) return { label: "Середня", width: "w-2/3", color: "bg-amber-400" };
  if (score >= 1) return { label: "Низька", width: "w-1/3", color: "bg-red-400" };
  return { label: "Не визначена", width: "w-0", color: "bg-transparent" };
}
