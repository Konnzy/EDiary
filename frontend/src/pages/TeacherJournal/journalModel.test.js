import assert from "node:assert/strict";
import test from "node:test";
import {
  calculateAverage,
  contextKey,
  createInitialJournalState,
  gradeKey,
  lessonDates,
  normalizeLessonEntry,
  normalizeGradeEntry,
  resolveHomeworkStatus,
} from "./journalModel.js";

test("builds lesson dates only for configured weekdays", () => {
  const dates = lessonDates("2026-09", [1, 4]);
  assert.equal(dates[0], "2026-09-03");
  assert.equal(dates.at(-1), "2026-09-28");
  assert.equal(dates.length, 8);
});

test("calculates an average only from numeric grades", () => {
  const context = contextKey("9-a", "mathematics", "2026-09");
  const dates = ["2026-09-03", "2026-09-07", "2026-09-10"];
  const state = { grades: {
    [gradeKey(context, "student", dates[0])]: { grade: "10", attendanceStatus: "ABSENT" },
    [gradeKey(context, "student", dates[1])]: { grade: "", attendanceStatus: "ABSENT" },
    [gradeKey(context, "student", dates[2])]: { grade: "8", attendanceStatus: "PRESENT" },
  } };
  assert.equal(calculateAverage(state, context, "student", dates), "9.0");
});

test("migrates legacy grade markers to a separate attendance status", () => {
  assert.deepEqual(normalizeGradeEntry({ value: "10", comment: "Добре" }), {
    grade: "10",
    attendanceStatus: "PRESENT",
    homeworkStatus: "",
    comment: "Добре",
  });
  assert.deepEqual(normalizeGradeEntry({ value: "Н", comment: "" }), {
    grade: "",
    attendanceStatus: "ABSENT",
    homeworkStatus: "",
    comment: "",
  });
  assert.deepEqual(normalizeGradeEntry({ value: "Зв.", comment: "" }), {
    grade: "",
    attendanceStatus: "RELEASED",
    homeworkStatus: "",
    comment: "",
  });
});

test("normalizes homework metadata and derives an unfinished homework status", () => {
  const lesson = normalizeLessonEntry({
    workType: "CLASSWORK",
    topic: "Тема",
    homework: "Вправа 1",
    homeworkDeadline: "2026-09-10",
  });

  assert.equal(lesson.homeworkDeadline, "2026-09-10");
  assert.equal(resolveHomeworkStatus({}, lesson, new Date("2026-09-11T12:00:00")), "OVERDUE");
  assert.equal(resolveHomeworkStatus({ homeworkStatus: "COMPLETED_LATE" }, lesson), "COMPLETED_LATE");
  assert.equal(resolveHomeworkStatus({}, { ...lesson, homework: "" }), "");
});

test("creates a populated initial prototype", () => {
  const state = createInitialJournalState();
  assert.ok(Object.keys(state.grades).length > 0);
  assert.ok(Object.keys(state.lessons).length > 0);
  assert.ok(Object.values(state.grades).every((entry) => "grade" in entry && "attendanceStatus" in entry && "homeworkStatus" in entry));
  assert.ok(Object.values(state.lessons).every((entry) => "homeworkDeadline" in entry));
});
