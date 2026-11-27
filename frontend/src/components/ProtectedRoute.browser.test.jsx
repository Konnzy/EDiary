import { createRoot } from "react-dom/client";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { afterEach, expect, test, vi } from "vitest";
import { page } from "vitest/browser";
import { ProtectedRoute } from "@/App.jsx";
import { authApi, userProfileApi } from "@/lib/api.js";
import { setNavigationRouter } from "@/lib/navigation.js";

let root, router, container;
afterEach(() => { root?.unmount(); router?.dispose(); container?.remove(); vi.restoreAllMocks(); });

function render(role) {
  container = document.createElement("div"); document.body.append(container);
  router = createMemoryRouter([
    {path:"/protected", element:<ProtectedRoute allowedRole={role} loginRoute="login"><h1>Дозволено</h1></ProtectedRoute>},
    {path:"/login",element:<h1>Вхід</h1>},
    {path:"/admin/home",element:<h1>Головна адміністратора</h1>},
    {path:"/change-temporary-password",element:<h1>Зміна пароля</h1>},
  ],{initialEntries:["/protected"]});
  setNavigationRouter(router);
  root=createRoot(container); root.render(<RouterProvider router={router} />);
}

for (const role of ["ADMIN", "TEACHER", "STUDENT", "PARENT"]) {
  test(`allows an authenticated ${role} on their route`, async () => {
    vi.spyOn(authApi,"getSession").mockResolvedValue({role,mustChangePassword:false});
    vi.spyOn(userProfileApi,"find").mockResolvedValue({role});
    render(role);
    await expect.element(page.getByRole("heading",{name:"Дозволено"})).toBeVisible();
  });
}

test("redirects an unauthenticated visitor to login", async () => {
  vi.spyOn(authApi,"getSession").mockRejectedValue(new Error("Not authenticated"));
  render("ADMIN");
  await expect.element(page.getByRole("heading",{name:"Вхід",exact:true})).toBeVisible();
});

test("redirects a user with a different role to their home", async () => {
  vi.spyOn(authApi,"getSession").mockResolvedValue({role:"ADMIN",mustChangePassword:false});
  render("STUDENT");
  await expect.element(page.getByRole("heading",{name:"Головна адміністратора"})).toBeVisible();
});

test("requires changing a temporary password before opening protected pages", async () => {
  vi.spyOn(authApi,"getSession").mockResolvedValue({role:"TEACHER",mustChangePassword:true});
  render("TEACHER");
  await expect.element(page.getByRole("heading",{name:"Зміна пароля"})).toBeVisible();
});
