import assert from "node:assert/strict";
import test from "node:test";
import { COPY_MODES, copySchedule, countCopiedLessons, countLessons } from "./scheduleCopy.js";

const empty = () => ({ MONDAY: [], TUESDAY: [] });
const lesson = (id, lessonNumber, subject) => ({ id, lessonNumber, subject });

test("fills only free lesson slots and preserves existing lessons", () => {
  const source = { MONDAY: [lesson(1, 1, "Математика"), lesson(2, 2, "Фізика")], TUESDAY: [] };
  const target = { MONDAY: [lesson(3, 1, "Українська мова")], TUESDAY: [] };
  let id = 10;

  const result = copySchedule(source, target, COPY_MODES.EMPTY_SLOTS, () => ++id);

  assert.deepEqual(result.MONDAY.map((item) => item.subject), ["Українська мова", "Фізика"]);
  assert.equal(result.MONDAY[0], target.MONDAY[0]);
  assert.equal(result.MONDAY[1].id, 11);
  assert.equal(countCopiedLessons(source, target, COPY_MODES.EMPTY_SLOTS), 1);
});

test("replaces the target schedule with independent lesson copies", () => {
  const source = { MONDAY: [lesson(1, 1, "Математика")], TUESDAY: [lesson(2, 3, "Хімія")] };
  const target = { MONDAY: [lesson(3, 2, "Фізика")], TUESDAY: [] };
  let id = 20;

  const result = copySchedule(source, target, COPY_MODES.REPLACE, () => ++id);

  assert.deepEqual(result.MONDAY.map((item) => item.subject), ["Математика"]);
  assert.deepEqual(result.TUESDAY.map((item) => item.subject), ["Хімія"]);
  assert.notEqual(result.MONDAY[0].id, source.MONDAY[0].id);
  assert.equal(countCopiedLessons(source, target, COPY_MODES.REPLACE), 2);
  assert.equal(countLessons(result), 2);
});

test("handles an empty source schedule", () => {
  assert.deepEqual(copySchedule(empty(), empty(), COPY_MODES.EMPTY_SLOTS, () => "id"), empty());
  assert.equal(countLessons(empty()), 0);
});
