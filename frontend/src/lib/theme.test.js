import assert from "node:assert/strict";
import test from "node:test";
import { readTheme, setTheme, THEME_STORAGE_KEY } from "./theme.js";

test("persists and restores the selected theme", () => {
  const originalWindow = globalThis.window;
  const originalDocument = globalThis.document;
  const values = new Map();
  const classes = new Set();

  globalThis.window = {
    localStorage: {
      getItem: (key) => values.get(key) ?? null,
      setItem: (key, value) => values.set(key, value),
    },
  };
  globalThis.document = {
    documentElement: {
      classList: { toggle: (name, enabled) => enabled ? classes.add(name) : classes.delete(name) },
      style: {},
    },
  };

  try {
    assert.equal(readTheme(), "light");
    setTheme("dark");
    assert.equal(values.get(THEME_STORAGE_KEY), "dark");
    assert.equal(readTheme(), "dark");
    assert.equal(classes.has("dark"), true);
    assert.equal(document.documentElement.style.colorScheme, "dark");

    setTheme("light");
    assert.equal(readTheme(), "light");
    assert.equal(classes.has("dark"), false);
    assert.equal(document.documentElement.style.colorScheme, "light");
  } finally {
    globalThis.window = originalWindow;
    globalThis.document = originalDocument;
  }
});
