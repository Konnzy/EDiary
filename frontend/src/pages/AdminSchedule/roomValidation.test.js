import assert from "node:assert/strict";
import test from "node:test";
import { isValidRoom, normalizeRoom } from "./roomValidation.js";

test("accepts numeric and descriptive room names", () => {
  for (const room of ["121", "121-А", "12Б", "Спортзал", "Актова зала", "Computer Lab-1"]) {
    assert.equal(isValidRoom(room), true, room);
  }
});

test("normalizes extra spaces around room parts", () => {
  assert.equal(normalizeRoom("  121  -  А  "), "121-А");
  assert.equal(normalizeRoom("  Актова   зала "), "Актова зала");
});

test("rejects special characters, dangling separators and overlong values", () => {
  for (const room of ["", "121/А", "<121>", "121-", "-121", "Дуже довга назва кабінету 121"]) {
    assert.equal(isValidRoom(room), false, room);
  }
});
