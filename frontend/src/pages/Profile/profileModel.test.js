import test from "node:test";
import assert from "node:assert/strict";
import {
  normalizeProfilePreferences,
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

test("normalizes stored preferences without losing defaults", () => {
  const fallback = {
    avatar: "",
    phone: "+380 00 000 00 00",
    selectedChildId: "child-1",
    notifications: { newGrade: true, newHomework: true, absence: true },
  };
  assert.deepEqual(normalizeProfilePreferences({ notifications: { newGrade: false } }, fallback), {
    avatar: "",
    phone: "+380 00 000 00 00",
    selectedChildId: "child-1",
    notifications: { newGrade: false, newHomework: true, absence: true },
  });
});

test("normalizes the teacher notification preferences independently", () => {
  const fallback = {
    avatar: "",
    phone: "+380 67 765 43 21",
    selectedChildId: "",
    notifications: { newMessage: true, scheduleChange: true },
  };
  assert.deepEqual(normalizeProfilePreferences({ notifications: { newMessage: false } }, fallback).notifications, {
    newMessage: false,
    scheduleChange: true,
  });
});
