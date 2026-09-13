import assert from "node:assert/strict";
import test from "node:test";
import { findNextLesson } from "./nextLesson.js";

function lesson(id, weekday, lessonNumber, startTime) {
  return { id, weekday, lessonNumber, startTime, endTime: "14:35" };
}

test("selects the next lesson instead of the one already in progress", () => {
  const schedules = {
    1: { lessons: [
      lesson(1, "MONDAY", 1, "08:00"),
      lesson(2, "MONDAY", 2, "08:55"),
    ] },
  };

  assert.equal(findNextLesson(schedules, 1, new Date("2026-09-14T08:30:00"))?.lesson.id, 2);
});

test("keeps a seventh lesson in its actual time slot", () => {
  const schedules = { 1: { lessons: [lesson(7, "TUESDAY", 7, "13:50")] } };
  const next = findNextLesson(schedules, 1, new Date("2026-09-15T09:00:00"));

  assert.equal(next?.lesson.lessonNumber, 7);
  assert.equal(next?.dayIndex, 1);
});

test("finds the next lesson after the week changes", () => {
  const schedules = {
    1: { lessons: [lesson(1, "MONDAY", 1, "08:00")] },
    2: { lessons: [lesson(7, "TUESDAY", 7, "13:50")] },
  };
  const now = new Date("2026-09-18T16:00:00");

  assert.equal(findNextLesson(schedules, 1, now), null);
  assert.deepEqual(findNextLesson(schedules, 1, now, true), {
    lesson: schedules[2].lessons[0], week: 2, dayIndex: 1,
  });
});

test("searches through an empty alternate week", () => {
  const schedules = {
    1: { lessons: [lesson(1, "MONDAY", 1, "08:00")] },
    2: { lessons: [] },
  };

  assert.equal(
    findNextLesson(schedules, 1, new Date("2026-09-20T12:00:00"), true)?.week,
    1,
  );
});
