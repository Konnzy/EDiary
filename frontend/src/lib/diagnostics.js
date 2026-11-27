import { getRoutePath } from "./navigation.js";
const entries = [];
const seenErrors = new WeakMap();
const uuidPattern = /^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i;
const errorTypes = new Set(["Error", "TypeError", "RangeError", "ReferenceError", "SyntaxError", "URIError", "EvalError", "AggregateError", "DOMException", "AbortError", "NetworkError"]);

export function newRequestId() {
  if (globalThis.crypto.randomUUID) return globalThis.crypto.randomUUID();
  const bytes = globalThis.crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  const hex = [...bytes].map(value => value.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

export function validRequestId(value, fallback) {
  return typeof value === "string" && uuidPattern.test(value) ? value.toLowerCase() : fallback;
}

function routeOnly(value) {
  return String(value || "").split(/[?#]/)[0].replace(/\/\d+(?=\/|$)/g, "/:id").replace(/[^a-zA-Z0-9_/:.-]/g, "").slice(0, 160);
}

function record(entry) {
  entries.push(Object.freeze(entry));
  if (entries.length > 50) entries.shift();
  console.error("[EDiary]", entry);
  return entry.requestId;
}

export function reportApiFailure({ requestId, method, path, status, code }) {
  if (status !== 0 && status < 500) return;
  record({ event: "api.failed", requestId: validRequestId(requestId, newRequestId()),
    method: /^[A-Z]{1,12}$/.test(method) ? method : "OTHER", route: routeOnly(path), status,
    code: /^[A-Z0-9_]{1,80}$/.test(code) ? code : "REQUEST_FAILED" });
}

export function reportRuntimeFailure(error) {
  if (error?.isDiagnosed) return error.requestId;
  if (error && typeof error === "object") {
    if (seenErrors.has(error)) return seenErrors.get(error);
  }
  const id = record({ event: "ui.failed", requestId: newRequestId(),
    route: routeOnly(typeof window === "undefined" ? "" : getRoutePath()),
    errorType: errorTypes.has(error?.name) ? error.name : "Error" });
  if (error && typeof error === "object") seenErrors.set(error, id);
  return id;
}

export function getDiagnostics() { return [...entries]; }

export function installDiagnostics() {
  if (window.__ediaryDiagnosticsInstalled) return;
  window.__ediaryDiagnosticsInstalled = true;
  window.addEventListener("error", event => reportRuntimeFailure(event.error));
  window.addEventListener("unhandledrejection", event => reportRuntimeFailure(event.reason));
}
