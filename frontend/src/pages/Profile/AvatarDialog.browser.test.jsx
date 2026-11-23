import { createRoot } from "react-dom/client";
import { useState } from "react";
import { afterEach, expect, test, vi } from "vitest";
import { page, userEvent } from "vitest/browser";
import { learnerTeachersApi } from "@/lib/api.js";
import { UserTeachersPage } from "@/pages/Teachers/UserTeachersPage.jsx";
import { AvatarDialog } from "./AvatarDialog.jsx";
import { UserProfilePage } from "./UserProfilePage.jsx";
import { encodeAvatar, readAvatarDimensions } from "./avatarCrop.js";

let root;
let container;

afterEach(() => {
  root?.unmount();
  container?.remove();
  vi.restoreAllMocks();
});

function renderDialog(onSave) {
  function Harness() {
    const [open, setOpen] = useState(false);
    return <>
      <button onClick={() => setOpen(true)}>Змінити фото</button>
      {open && <AvatarDialog onSave={onSave} onClose={() => setOpen(false)} />}
    </>;
  }
  render(<Harness />);
}

function render(component) {
  container = document.createElement("div");
  document.body.append(container);
  root = createRoot(container);
  root.render(component);
}

async function photo(width, height, type = "image/png") {
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const context = canvas.getContext("2d");
  context.fillStyle = "red";
  context.fillRect(0, 0, width / 2, height);
  context.fillStyle = "blue";
  context.fillRect(width / 2, 0, width / 2, height);
  return new File([await new Promise((resolve) => canvas.toBlob(resolve, type))], type === "image/png" ? "photo.png" : "photo.jpg", { type });
}

function choose(file) {
  const input = document.querySelector('dialog input[type="file"]');
  const transfer = new DataTransfer();
  transfer.items.add(file);
  input.files = transfer.files;
  input.dispatchEvent(new Event("change", { bubbles: true }));
}
const saveButton = () => page.getByRole("dialog").getByRole("button", { name: "Зберегти", exact: true });
const alertText = () => document.querySelector('dialog [role="alert"]')?.textContent ?? "";

test("crops and saves a 4K photo as a compact JPEG after a retry", async () => {
  const upload = vi.fn().mockRejectedValueOnce(new Error("Помилка мережі")).mockResolvedValue(undefined);
  renderDialog(upload);
  const trigger = page.getByRole("button", { name: "Змінити фото" });
  await trigger.click();
  await expect.element(saveButton()).toBeDisabled();
  choose(await photo(3840, 2160));
  const zoom = page.getByRole("slider");
  await expect.element(zoom).toHaveValue("1");
  await page.getByRole("button", { name: "Збільшити масштаб" }).click();
  await expect.element(zoom).toHaveValue("1.1");
  zoom.element().focus();
  await userEvent.keyboard("{End}");
  await expect.element(page.getByRole("button", { name: "Збільшити масштаб" })).toBeDisabled();
  await page.getByRole("button", { name: "Зменшити масштаб" }).click();
  await expect.element(zoom).toHaveValue("2.9");
  const crop = page.getByRole("group", { name: "Кадрування фото" });
  const image = crop.element().querySelector("img");
  const left = image.style.left;
  crop.element().focus();
  await userEvent.keyboard("{Shift>}{ArrowRight}{/Shift}{ArrowDown}");
  await expect.poll(() => image.style.left).not.toBe(left);

  await saveButton().click();
  await expect.poll(alertText).toContain("Помилка мережі");
  expect(upload).toHaveBeenCalledTimes(1);
  const jpg = upload.mock.calls[0][0];
  expect(jpg.type).toBe("image/jpeg");
  expect(jpg.size).toBeLessThanOrEqual(500 * 1024);
  expect(readAvatarDimensions(await jpg.arrayBuffer(), jpg.type)).toEqual([512, 512]);
  await saveButton().click();
  await expect.element(page.getByRole("dialog")).not.toBeInTheDocument();
  await expect.element(trigger).toHaveFocus();
  expect(upload).toHaveBeenCalledTimes(2);
});

test("rejects invalid formats and oversized pictures, and cancels without saving", async () => {
  const upload = vi.fn();
  renderDialog(upload);
  await page.getByRole("button", { name: "Змінити фото" }).click();
  choose(new File(["image"], "photo.gif", { type: "image/gif" }));
  await expect.poll(alertText).toContain("JPG або PNG");
  choose(new File([new Uint8Array(5 * 1024 * 1024 + 1)], "photo.png", { type: "image/png" }));
  await expect.poll(alertText).toContain("5 МБ");
  choose(await photo(5000, 5000));
  await expect.poll(alertText).toContain("20 мегапікселів");
  const small = await photo(40, 20, "image/jpeg");
  choose(new File([(await small.arrayBuffer()).slice(0, 12)], "photo.jpg", { type: small.type }));
  await expect.poll(alertText).toContain("розміри зображення");
  choose(small);
  await expect.element(saveButton()).toBeEnabled();
  await userEvent.keyboard("{Escape}");
  await expect.element(page.getByRole("dialog")).not.toBeInTheDocument();
  expect(upload).not.toHaveBeenCalled();
});

test("reports when the browser cannot encode the crop within the size limit", async () => {
  const canvas = document.createElement("canvas");
  vi.spyOn(HTMLCanvasElement.prototype, "toBlob").mockImplementation((callback) => callback(null));
  await expect(encodeAvatar(canvas, { left: 0, top: 0, side: 30 })).rejects.toThrow("500 КБ");
});

test("updates the teacher profile after uploading and removing its photo", async () => {
  const upload = vi.fn();
  const remove = vi.fn();
  const profile = {
    role: "TEACHER", firstName: "Леся", lastName: "Гнатюк", login: "teacher",
    email: "teacher@example.test", avatarVersion: null, notifications: {},
    professional: { position: "Вчитель", subjects: [], classes: [], homeroomClasses: [] },
  };
  vi.spyOn(window, "fetch").mockImplementation(async (url, options) => {
    const { pathname } = new URL(url, window.location.href);
    if (pathname.endsWith("/users/me")) return Response.json(profile);
    if (pathname.endsWith("/security/csrf")) return Response.json({ token: "test-csrf" });
    if (pathname.endsWith("/users/me/avatar") && options.method === "POST") {
      upload(options.body.get("file"));
      return Response.json({ ...profile, avatarVersion: 1 });
    }
    if (pathname.endsWith("/users/me/avatar") && options.method === "DELETE") {
      remove();
      return new Response(null, { status: 204 });
    }
    return Response.json({ items: [], unreadCount: 0 });
  });
  render(<UserProfilePage role="TEACHER" />);
  await page.getByRole("button", { name: "Змінити фото профілю" }).click();
  choose(await photo(24, 24, "image/jpeg"));
  await expect.element(saveButton()).toBeEnabled();
  await saveButton().click();
  await expect.element(page.getByText("Фото профілю оновлено.")).toBeVisible();
  expect(upload).toHaveBeenCalledTimes(1);
  expect(upload.mock.calls[0][0].type).toBe("image/jpeg");
  await page.getByRole("button", { name: "Видалити", exact: true }).click();
  await expect.element(page.getByText("Фото профілю видалено.")).toBeVisible();
  expect(remove).toHaveBeenCalledOnce();
});

test("shows the class teacher photo and restores initials if the image fails", async () => {
  expect(learnerTeachersApi.homeroomAvatarUrl(7)).toMatch(/\/student\/teachers\/7\/avatar$/);
  expect(learnerTeachersApi.homeroomAvatarUrl(7, 13)).toMatch(/\/parent\/children\/13\/teachers\/7\/avatar$/);
  const imageUrl = URL.createObjectURL(await photo(24, 24));
  const avatarUrl = vi.spyOn(learnerTeachersApi, "homeroomAvatarUrl").mockReturnValue(imageUrl);
  vi.spyOn(window, "fetch").mockImplementation(async (url) => {
    if (new URL(url, window.location.href).pathname.endsWith("/student/teachers")) {
      return Response.json({
        className: "9-А", teachers: [],
        homeroomTeacher: { id: 7, userId: 20, fullName: "Гнатюк Леся", hasAvatar: true, subjects: [] },
      });
    }
    return Response.json({ items: [], unreadCount: 0 });
  });
  try {
    render(<UserTeachersPage role="STUDENT" />);
    await expect.element(page.getByRole("heading", { name: "Гнатюк Леся" })).toBeVisible();
    const card = document.querySelector("article");
    const image = card.querySelector("img");
    await expect(image.decode()).resolves.toBeUndefined();
    expect(avatarUrl).toHaveBeenCalledWith(7, "");
    image.dispatchEvent(new Event("error"));
    await expect.poll(() => card.querySelector("img")).toBeNull();
    expect(card.textContent).toContain("ГЛ");
  } finally {
    URL.revokeObjectURL(imageUrl);
  }
});
