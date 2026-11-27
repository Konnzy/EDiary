import { createRoot } from "react-dom/client";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { afterEach, expect, test, vi } from "vitest";
import { page } from "vitest/browser";
import { adminRegistrationApi, adminUsersApi } from "@/lib/api.js";
import { AdminRequestsPage } from "./AdminRequestsPage.jsx";

let root, router, container;
afterEach(() => {
  root?.unmount();
  router?.dispose();
  container?.remove();
  vi.restoreAllMocks();
});

const request = {
  id: 1, name: "Іван", surname: "Тест", role: "TEACHER", status: "PENDING",
  email: "teacher@example.com", phone: "+380671234567", birthDate: "1990-01-01",
  submittedAt: "2026-11-27T10:00:00", requestedSubject: { label: "Математика" },
};

for (const emailSent of [true, false]) {
  test(`approval preserves the created account and reports email delivery: ${emailSent}`, async () => {
    vi.spyOn(adminUsersApi, "findMe").mockResolvedValue({ name: "Адміністратор", surname: "Тест" });
    vi.spyOn(adminRegistrationApi, "findNotifications").mockResolvedValue({ pendingCount: 0, items: [] });
    const load = vi.spyOn(adminRegistrationApi, "findAll")
      .mockResolvedValueOnce({ content: [request], totalElements: 1, totalPages: 1, first: true, last: true })
      .mockResolvedValue({ content: [{ ...request, status: "APPROVED", createdUserLogin: "teacher.test" }], totalElements: 1, totalPages: 1, first: true, last: true });
    const message = emailSent ? "Заявку схвалено, лист надіслано" : "Акаунт створено, але лист не надіслано";
    const approve = vi.spyOn(adminRegistrationApi, "approve").mockResolvedValue({ emailSent, message });
    container = document.createElement("div");
    document.body.append(container);
    router = createMemoryRouter([{ path: "*", element: <AdminRequestsPage /> }], { initialEntries: ["/admin/requests"] });
    root = createRoot(container);
    root.render(<RouterProvider router={router} />);
    await page.getByRole("button", { name: "Схвалити", exact: true }).first().click();
    await page.getByRole("textbox", { name: "Логін користувача" }).fill("teacher.test");
    await page.getByRole("dialog").getByRole("button", { name: "Схвалити", exact: true }).click();
    await expect.poll(() => container.querySelector('[role="status"]')?.textContent).toBe(message);
    expect(container.querySelector('[role="status"]').className).toContain(emailSent ? "bg-emerald-50" : "bg-amber-50");
    await expect.element(page.getByText("Логін: teacher.test").first()).toBeVisible();
    expect(approve).toHaveBeenCalledExactlyOnceWith(1, "teacher.test");
    expect(load).toHaveBeenCalledTimes(2);
    expect(container.querySelector('[role="dialog"]')).toBeNull();
  });
}
