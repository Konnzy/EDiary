import test from "node:test";
import assert from "node:assert/strict";
import { formatShortName, profileInitials, roleLabels } from "./userIdentity.js";

test("uses surname and first name in every user header", () => {
  assert.equal(formatShortName({ lastName: "Петренко", firstName: "Іван", patronymic: "Олександрович" }), "Петренко Іван");
  assert.equal(profileInitials({ lastName: "Петренко", firstName: "Іван" }), "ІП");
  assert.equal(roleLabels.STUDENT, "Учень");
});
