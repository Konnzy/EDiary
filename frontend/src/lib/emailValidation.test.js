import { test } from "node:test";
import assert from "node:assert/strict";
import { hasEmailFormat } from "./emailValidation.js";

test("accepts normal mailboxes and subdomains", () => {
  for (const address of ["teacher@example.com", "student+school@sub.example.org"]) assert.equal(hasEmailFormat(address),true,address);
});
test("rejects missing parts, whitespace, multiple at signs and incomplete domains", () => {
  for (const address of ["", "@example.com", "user@", "a@b@c.org", "first last@example.com", "user@example", "user@example.", "user@.com"]) {
    assert.equal(hasEmailFormat(address),false,address);
  }
});
