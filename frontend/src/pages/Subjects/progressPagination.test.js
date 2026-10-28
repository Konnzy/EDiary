import test from "node:test";
import assert from "node:assert/strict";
import { paginateProgressRows, sortProgressRows, getSubjectStats } from "./subjectProgressModel.js";

const rows = Array.from({ length: 45 }, (_, index) => ({ id: index + 1, grade: index < 20 ? 12 : 6, date: `2026-${index < 30 ? "09" : "10"}-${String(index % 30 + 1).padStart(2, "0")}` }));

test("paginates twenty rows with an exact count, final page and safe bounds", () => {
  assert.equal(paginateProgressRows(rows, 1).rows.length, 20);
  assert.deepEqual(paginateProgressRows(rows, 2).rows.map(row => row.id), rows.slice(20, 40).map(row => row.id));
  const last = paginateProgressRows(rows, 99);
  assert.equal(last.page, 3);
  assert.equal(last.start, 41);
  assert.equal(last.end, 45);
  assert.equal(last.total, 45);
  assert.equal(last.rows.length, 5);
  assert.equal(paginateProgressRows(rows.slice(0, 20), 1).totalPages, 1);
  assert.equal(paginateProgressRows([], 3).start, 0);
  assert.equal(paginateProgressRows(rows, 0).page, 1);
});

test("sorts the whole list before taking a page and does not change source data", () => {
  const sorted = sortProgressRows(rows, { column: "grade", direction: "asc" });
  assert.ok(paginateProgressRows(sorted, 1).rows.every(row => row.grade === 6));
  assert.equal(rows[0].grade, 12);
  const dates = sortProgressRows(rows, { column: "date", direction: "desc" });
  assert.equal(dates[0].date, "2026-10-15");
});

test("period statistics are calculated from the whole list rather than a page", () => {
  const subject = { grades: rows, attendance: [], homework: [] };
  assert.equal(getSubjectStats(subject).average, 8.7);
  assert.equal(getSubjectStats({ ...subject, grades: paginateProgressRows(rows, 1).rows }).average, 12);
});
