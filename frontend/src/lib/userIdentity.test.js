import test from "node:test";
import assert from "node:assert/strict";
import { userIdentities } from "./userIdentity.js";

test("uses surname and first name in every user header", () => {
  assert.deepEqual(
    Object.values(userIdentities).map(({ displayName }) => displayName),
    ["Петренко Іван", "Іваненко Олена", "Коваленко Олександр"],
  );
});
