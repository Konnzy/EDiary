import assert from "node:assert/strict";
import test from "node:test";
import {
  allowRouteNavigation,
  isGuardedActionBlocked,
  isRouteNavigationBlocked,
  registerNavigationGuard,
} from "./navigationGuard.js";

test("blocks route changes while a guard is active", () => {
  const requests = [];
  const unregister = registerNavigationGuard((request) => {
    requests.push(request);
    return false;
  });

  assert.equal(isRouteNavigationBlocked("admin/home", "admin/schedule"), true);
  assert.deepEqual(requests, [{ route: "admin/home" }]);
  unregister();
});

test("allows one confirmed route change", () => {
  const unregister = registerNavigationGuard(() => false);

  allowRouteNavigation("admin/home");
  assert.equal(isRouteNavigationBlocked("admin/home", "admin/schedule"), false);
  assert.equal(isRouteNavigationBlocked("admin/users", "admin/schedule"), true);
  unregister();
});

test("guards actions such as logout", () => {
  const action = () => {};
  const unregister = registerNavigationGuard((request) => request.action !== action);

  assert.equal(isGuardedActionBlocked(action), true);
  unregister();
  assert.equal(isGuardedActionBlocked(action), false);
});
