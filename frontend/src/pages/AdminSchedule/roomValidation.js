export const ROOM_MAX_LENGTH = 20;

const ROOM_PATTERN = /^[\p{L}\p{N}]+(?:[ -][\p{L}\p{N}]+)*$/u;

export function normalizeRoom(value) {
  return value.trim().replace(/\s+/g, " ").replace(/\s*-\s*/g, "-");
}

export function isValidRoom(value) {
  const normalized = normalizeRoom(value);
  return normalized.length > 0 && normalized.length <= ROOM_MAX_LENGTH && ROOM_PATTERN.test(normalized);
}
