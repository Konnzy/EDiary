export const resultLabels = { PASSED: "зар.", NOT_PASSED: "не зар.", EXEMPTED: "зв." };
export const defaultAssessmentPolicy = { mode: "NUMERIC", allowPassed: false, allowExempted: false };
export const isFinalWork = type => ["THEMATIC", "SEMESTER", "YEAR"].includes(type);
export function resultOptions(policy = defaultAssessmentPolicy, type) {
  if (!isFinalWork(type)) return [];
  return [...(policy.mode === "PASS_FAIL" || policy.allowPassed ? ["PASSED"] : []), ...(policy.mode === "PASS_FAIL" ? ["NOT_PASSED"] : []), ...(policy.allowExempted ? ["EXEMPTED"] : [])];
}
export function gradeDisplay(grade) { return grade?.score ?? resultLabels[grade?.result] ?? ""; }
