import { test } from "node:test";
import assert from "node:assert/strict";
import { getDiagnostics, newRequestId, reportApiFailure, reportRuntimeFailure, validRequestId } from "./diagnostics.js";

test("API diagnostics keep reference and omit query strings, payloads and duplicate business errors", () => {
  const original = console.error;
  console.error = () => {};
  try {
    const requestId = newRequestId();
    reportApiFailure({ requestId, method: "POST", path: "/auth/password/reset?token=secret-token", status: 500, code: "INTERNAL_ERROR" });
    const entry = getDiagnostics().at(-1);
    assert.equal(entry.requestId, requestId);
    assert.equal(entry.route, "/auth/password/reset");
    assert.ok(!JSON.stringify(entry).includes("secret-token"));
    const count = getDiagnostics().length;
    reportApiFailure({ requestId, method: "POST", path: "/auth/login", status: 401, code: "INVALID_CREDENTIALS" });
    assert.equal(getDiagnostics().length, count);
    assert.equal(validRequestId("bad\nsecret", requestId), requestId);
  } finally { console.error = original; }
});

test("runtime diagnostics omit error messages and deduplicate the same exception", () => {
  const original = console.error;
  console.error = () => {};
  try {
    const error = new Error("password=secret");
    const id = reportRuntimeFailure(error);
    const count = getDiagnostics().length;
    assert.ok(id);
    assert.equal(reportRuntimeFailure(error), id);
    assert.equal(getDiagnostics().length, count);
    assert.ok(!JSON.stringify(getDiagnostics()).includes("password=secret"));
  } finally { console.error = original; }
});
