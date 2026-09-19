import test from "node:test";
import assert from "node:assert/strict";
import {
  calculateAverage,
  getGradeForecast,
  getPeriodOptions,
  getSubjectPeriodView,
  getSubjectStats,
  subjectProgress,
} from "./subjectProgressModel.js";

test("calculates and rounds the average grade", () => {
  assert.equal(calculateAverage([{ grade: 8 }, { grade: 9 }, { grade: 10 }]), 9);
  assert.equal(calculateAverage([{ grade: 8 }, { grade: 9 }]), 8.5);
  assert.equal(calculateAverage([]), null);
});

test("builds subject summary statistics", () => {
  const stats = getSubjectStats({
    previousAverage: 8,
    grades: [{ grade: 9 }, { grade: 10 }],
    attendance: [{ status: "PRESENT" }, { status: "ABSENT" }, { status: "EXCUSED" }, { status: "PRESENT" }],
    homework: [{ status: "ACTIVE" }, { status: "ACTIVE" }, { status: "OVERDUE" }, { status: "COMPLETED" }, { status: "COMPLETED_LATE" }],
  });

  assert.deepEqual(stats, {
    average: 9.5,
    delta: 1.5,
    missed: 2,
    missedPercent: 50,
    attended: 2,
    totalLessons: 4,
    activeHomework: 2,
    overdueHomework: 1,
    completedHomework: 2,
    completedLateHomework: 1,
  });
});

test("builds monthly, semester and full-year period options", () => {
  const options = getPeriodOptions("2025/2026");

  assert.equal(options[0].value, "YEAR");
  assert.ok(options.some((item) => item.value === "SEMESTER_1"));
  assert.ok(options.some((item) => item.value === "SEMESTER_2"));
  assert.ok(options.some((item) => item.value === "2025-09" && item.label === "Вересень 2025"));
  assert.ok(options.some((item) => item.value === "2026-05" && item.label === "Травень 2026"));
});

test("filters subject records by academic year and period", () => {
  const mathematics = subjectProgress.find((item) => item.id === "mathematics");
  const september = getSubjectPeriodView(mathematics, "2026/2027", "2026-09");
  const previousYear = getSubjectPeriodView(mathematics, "2025/2026", "YEAR");
  const firstSemester = getSubjectPeriodView(mathematics, "2025/2026", "SEMESTER_1");

  assert.equal(september.grades.length, 5);
  assert.equal(september.attendance.length, 6);
  assert.equal(previousYear.grades.length, 4);
  assert.equal(firstSemester.grades.length, 2);
  assert.ok(previousYear.grades.every((item) => item.date.endsWith("2025") || item.date.endsWith("2026")));
});

test("forecasts thematic, semester and annual grades", () => {
  const mathematics = subjectProgress.find((item) => item.id === "mathematics");

  assert.deepEqual(getGradeForecast(mathematics, "2026/2027", "2026-09"), {
    thematic: "9,20",
    semester: "9,20",
    annual: "9,20",
  });
  assert.deepEqual(getGradeForecast(mathematics, "2025/2026", "YEAR"), {
    thematic: "9,00",
    semester: "8,50",
    annual: "8,50",
  });
});
