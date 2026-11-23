import test from "node:test";
import assert from "node:assert/strict";
import {
  mapProfileResponse,
  validateAvatarFile,
  validatePasswordChange,
  validatePhone,
} from "./profileModel.js";
import { getPasswordStrength } from "../../lib/passwordStrength.js";

test("accepts only JPG or PNG avatars up to 5 MB", () => {
  assert.equal(validateAvatarFile({ type: "image/png", size: 1024 }), "");
  assert.equal(validateAvatarFile({ type: "image/jpeg", size: 5 * 1024 * 1024 }), "");
  assert.match(validateAvatarFile({ type: "image/gif", size: 1024 }), /JPG/);
  assert.match(validateAvatarFile({ type: "image/webp", size: 1024 }), /JPG/);
  assert.match(validateAvatarFile({ type: "image/jpeg", size: 5 * 1024 * 1024 + 1 }), /5 МБ/);
});

test("validates editable parent and teacher phone", () => {
  assert.equal(validatePhone("+380 67 123 45 67"), "");
  assert.match(validatePhone("123"), /коректний/);
});

test("validates password changes", () => {
  assert.equal(validatePasswordChange("OldPass123", "NewPass456", "NewPass456"), "");
  assert.match(validatePasswordChange("", "NewPass456", "NewPass456"), /поточний/);
  assert.match(validatePasswordChange("OldPass123", "short", "short"), /8 символів/);
  assert.match(validatePasswordChange("OldPass123", "NewPassword", "NewPassword"), /літери та цифри/);
  assert.match(validatePasswordChange("OldPass123", "NewPass456", "OtherPass456"), /не збігаються/);
});

test("calculates the same password strength levels used by password reset", () => {
  assert.equal(getPasswordStrength("").label, "Не визначена");
  assert.equal(getPasswordStrength("letters").label, "Низька");
  assert.equal(getPasswordStrength("Letters123").label, "Середня");
  assert.equal(getPasswordStrength("Letters123!").label, "Висока");
});

test("maps the current user's role data without example content", () => {
  const profile = mapProfileResponse({
    role: "PARENT", firstName: "Олена", lastName: "Іваненко", patronymic: "Петрівна",
    login: "parent.ivanenko", email: "parent@example.com", phone: "+380671234567",
    avatarVersion: null, notifications: { newGrade: false },
    children: [{ id: 42, fullName: "Іваненко Артем", className: null, teacherName: null }],
  });
  assert.equal(profile.fullName, "Іваненко Олена Петрівна");
  assert.equal(profile.children[0].id, "42");
  assert.equal(profile.children[0].className, "Клас не призначено");
  assert.equal(profile.notifications.newGrade, false);
});
