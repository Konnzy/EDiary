import { createRoot } from "react-dom/client";
import { createMemoryRouter, Link, RouterProvider, useLocation } from "react-router-dom";
import { afterEach, expect, test } from "vitest";
import { page } from "vitest/browser";
import { NavigationObserver } from "./NavigationObserver.jsx";
import { allowRouteNavigation, registerNavigationGuard } from "@/lib/navigationGuard.js";
import { navigate, setNavigationRouter } from "@/lib/navigation.js";

let root, container, router, unregister;
afterEach(() => { unregister?.(); unregister = null; root?.unmount(); router?.dispose(); container?.remove(); });

function Harness() {
  const location = useLocation();
  return <><NavigationObserver /><output aria-label="Адреса">{location.pathname + location.search}</output>
    <Link to="/parent/profile?addChild=1">Профіль</Link></>;
}
function render(initial = "/login") {
  container = document.createElement("div"); document.body.append(container);
  router = createMemoryRouter([{path:"*",element:<Harness />}],{initialEntries:[initial]});
  setNavigationRouter(router);
  root = createRoot(container); root.render(<RouterProvider router={router} />);
}

test("navigates without fragments, preserves query and supports back/forward", async () => {
  render();
  await page.getByRole("link",{name:"Профіль"}).click();
  await expect.element(page.getByLabelText("Адреса")).toHaveTextContent("/parent/profile?addChild=1");
  await router.navigate(-1);
  await expect.element(page.getByLabelText("Адреса")).toHaveTextContent("/login");
  await router.navigate(1);
  await expect.element(page.getByLabelText("Адреса")).toHaveTextContent("/parent/profile?addChild=1");
});

test("opens a nested route directly and blocks leaving an unsaved schedule", async () => {
  render("/admin/schedule?classId=2");
  await expect.element(page.getByLabelText("Адреса")).toHaveTextContent("/admin/schedule?classId=2");
  let pending;
  unregister = registerNavigationGuard(request => { pending = request.route; return false; });
  await page.getByRole("link",{name:"Профіль"}).click();
  await expect.poll(() => pending).toBe("parent/profile?addChild=1");
  await expect.element(page.getByLabelText("Адреса")).toHaveTextContent("/admin/schedule?classId=2");
  allowRouteNavigation(pending);
  await navigate(pending);
  await expect.element(page.getByLabelText("Адреса")).toHaveTextContent("/parent/profile?addChild=1");
});
