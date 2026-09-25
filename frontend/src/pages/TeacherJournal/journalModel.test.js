import assert from "node:assert/strict";
import test from "node:test";
import { dateParts, homeworkStatuses, normalizeLessonForm, workTypes } from "./journalModel.js";

test("journal options use backend enum values", () => {
  assert.deepEqual(workTypes.map((item) => item.value), [
    "CLASSWORK", "CONTROL", "HOMEWORK", "INDEPENDENT", "PRACTICAL", "LABORATORY", "THEMATIC", "SEMESTER", "YEAR",
  ]);
  assert.deepEqual(homeworkStatuses.map((item) => item.value), [
    "", "ASSIGNED", "OVERDUE", "COMPLETED", "SUBMITTED_LATE",
  ]);
});

test("formats journal date without browser locale dependence", () => {
  assert.deepEqual(dateParts("2026-09-03"), { weekday: "Четвер", day: "03", month: "09" });
});

test("opens an empty lesson from nullable API fields", () => {
  assert.deepEqual(normalizeLessonForm({ workType: "CLASSWORK", topic: null, homework: null, homeworkDeadline: null }), {
    workType: "CLASSWORK", topic: "", homework: "", homeworkDeadline: "",
  });
});

test("offers semester and annual work types for the whole lesson", () => {
  assert.ok(workTypes.some((item) => item.value === "SEMESTER" && item.label === "Семестрова"));
  assert.ok(workTypes.some((item) => item.value === "YEAR" && item.label === "Річна"));
});
