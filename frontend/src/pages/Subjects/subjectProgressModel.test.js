import test from "node:test";
import assert from "node:assert/strict";
import {
  calculateAverage, getGradeForecast, getPeriodOptions, getSubjectPeriodView, getSubjectStats,
} from "./subjectProgressModel.js";

const subject = {
  grades: [
    { date: "2026-09-03", grade: 10 }, { date: "2026-09-10", grade: 8 },
    { date: "2026-05-10", grade: 9 },
  ],
  attendance: [
    { date: "2026-09-03", status: "PRESENT" },
    { date: "2026-09-10", status: "EXCUSED_ABSENCE" },
  ],
  homework: [
    { assignedDate: "2026-09-03", status: "ASSIGNED" },
    { assignedDate: "2026-09-10", status: "SUBMITTED_LATE" },
  ],
};

test("calculates grade average from server records", () => {
  assert.equal(calculateAverage(subject.grades), 9);
  assert.equal(calculateAverage([]), null);
});

test("filters ISO dates by academic year and month", () => {
  const view = getSubjectPeriodView(subject, "2026/2027", "2026-09");
  assert.equal(view.grades.length, 2);
  assert.equal(view.attendance.length, 2);
  assert.equal(view.homework.length, 2);
  assert.equal(getSubjectPeriodView(subject, "2025/2026", "YEAR").grades.length, 1);
});

test("summarizes backend attendance and homework statuses", () => {
  const stats = getSubjectStats(getSubjectPeriodView(subject, "2026/2027", "2026-09"));
  assert.equal(stats.missed, 1);
  assert.equal(stats.activeHomework, 1);
  assert.equal(stats.completedLateHomework, 1);
  assert.equal(stats.average, 9);
});

test("builds period options and grade forecast without prototype data", () => {
  assert.ok(getPeriodOptions("2026/2027").some((item) => item.value === "2026-09"));
  assert.deepEqual(getGradeForecast(subject, "2026/2027", "2026-09"), {
    thematic: "9,00", semester: "9,00", annual: "9,00",
  });
});
