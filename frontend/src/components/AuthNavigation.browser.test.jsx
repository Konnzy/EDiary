import { createRoot } from "react-dom/client";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { afterEach, expect, test, vi } from "vitest";
import { page } from "vitest/browser";
import App from "@/App.jsx";
import { authApi, adminAuthApi, adminUsersApi, userProfileApi } from "@/lib/api.js";
import { setNavigationRouter } from "@/lib/navigation.js";

let root, router, container;
const originalUrl=window.location.href;
afterEach(()=>{root?.unmount();router?.dispose();container?.remove();vi.restoreAllMocks();window.history.replaceState(null,"",originalUrl);});
function render(path) {
  container=document.createElement("div");document.body.append(container);
  router=createMemoryRouter([{path:"*",element:<App />}],{initialEntries:[path]});
  setNavigationRouter(router); root=createRoot(container);root.render(<RouterProvider router={router} />);
}

for(const administrator of [false,true]) {
  test(`password recovery keeps the correct return route (${administrator ? "admin" : "user"})`,async()=>{
    const request=vi.spyOn(authApi,"forgotPassword").mockResolvedValue({message:"Інструкцію надіслано"});
    render(administrator?"/admin/forgot-password":"/forgot-password");
    const input=page.getByRole("textbox");
    await input.fill("invalid");
    await page.getByRole("button",{name:/Надіслати/}).click();
    await expect.poll(()=>container.querySelector('[role="alert"]')?.textContent).toContain("Некоректний формат електронної пошти");
    expect(request).not.toHaveBeenCalled();
    await input.fill("teacher@example.com");
    await page.getByRole("button",{name:/Надіслати/}).click();
    await expect.element(page.getByRole("status")).toHaveTextContent("Інструкцію надіслано");
    const back=page.getByRole("link",{name:/Повернутися/});
    await expect.element(back).toHaveAttribute("href",administrator?"/admin":"/login");
    await back.click();
    await expect.poll(()=>router.state.location.pathname).toBe(administrator?"/admin":"/login");
  });

  test(`reset link reads the token from a path URL (${administrator ? "admin" : "user"})`,async()=>{
    const token="a".repeat(43);
    const reset=vi.spyOn(authApi,"resetPassword").mockResolvedValue({message:"Пароль оновлено"});
    render(`${administrator?"/admin":""}/reset-password?token=${token}`);
    await page.getByLabelText("Новий пароль",{exact:true}).fill("NewPass123");
    await page.getByLabelText("Підтвердження пароля",{exact:true}).fill("NewPass123");
    await page.getByRole("button",{name:/Зберегти|Змінити пароль/}).click();
    await expect.element(page.getByRole("status")).toHaveTextContent("Пароль оновлено");
    expect(reset).toHaveBeenCalledWith(token,"NewPass123");
    await expect.element(page.getByRole("link",{name:/Повернутися/})).toHaveAttribute("href",administrator?"/admin":"/login");
  });
}

test("an invalid reset link cannot submit a password",async()=>{
  const reset=vi.spyOn(authApi,"resetPassword");
  render("/reset-password?token=invalid");
  await expect.element(page.getByText(/Посилання недійсне/)).toBeVisible();
  await expect.element(page.getByRole("button",{name:/Зберегти|Змінити пароль/})).toBeDisabled();
  expect(reset).not.toHaveBeenCalled();
});

for(const role of ["ADMIN","TEACHER"]) {
  test(`temporary-password logout returns ${role} to the right login`,async()=>{
    vi.spyOn(authApi,"getSession").mockResolvedValue({role,login:"test",mustChangePassword:true});
    vi.spyOn(userProfileApi,"find").mockResolvedValue({role});
    const logout=vi.spyOn(role==="ADMIN"?adminAuthApi:authApi,"logout").mockResolvedValue(null);
    render("/change-temporary-password");
    await page.getByRole("button",{name:"Вийти із системи"}).click();
    await expect.poll(()=>router.state.location.pathname).toBe(role==="ADMIN"?"/admin":"/login");
    expect(logout).toHaveBeenCalledOnce();
  });
}
