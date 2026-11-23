const MAX_PIXELS = 20_000_000;
const AVATAR_SIDE = 512;
const MAX_AVATAR_OUTPUT_BYTES = 500 * 1024;

// Read dimensions before letting the browser allocate a decoded bitmap.
export function readAvatarDimensions(buffer, type) {
  const view = new DataView(buffer);
  if (type === "image/png" && view.byteLength >= 24 &&
      view.getUint32(0) === 0x89504e47 && view.getUint32(4) === 0x0d0a1a0a &&
      view.getUint32(8) === 13 && view.getUint32(12) === 0x49484452) {
    return [view.getUint32(16), view.getUint32(20)];
  }
  if (type !== "image/jpeg" || view.byteLength < 4 || view.getUint16(0) !== 0xffd8) return [];

  let offset = 2;
  while (offset + 3 < view.byteLength && view.getUint8(offset++) === 0xff) {
    while (offset < view.byteLength && view.getUint8(offset) === 0xff) offset++;
    if (offset + 2 >= view.byteLength) break;
    const marker = view.getUint8(offset++);
    if (marker === 0xda || marker === 0xd9) break;
    const length = view.getUint16(offset);
    if (length < 2 || offset + length > view.byteLength) break;
    // SOF markers store the frame size, excluding Huffman and coding tables.
    if (marker >= 0xc0 && marker <= 0xcf && ![0xc4, 0xc8, 0xcc].includes(marker) && length >= 8) {
      return [view.getUint16(offset + 5), view.getUint16(offset + 3)];
    }
    offset += length;
  }
  return [];
}

export function validateAvatarDimensions(width, height) {
  if (!width || !height) return "Не вдалося визначити розміри зображення.";
  if (width * height > MAX_PIXELS) return "Роздільна здатність фото не має перевищувати 20 мегапікселів.";
  return "";
}

export function getAvatarCrop(width, height, zoom, position) {
  const side = Math.min(width, height) / zoom;
  const scaledWidth = width / side;
  const scaledHeight = height / side;
  const clamp = (value, limit) => Math.min(limit, Math.max(-limit, value));
  const x = clamp(position.x, (scaledWidth - 1) / 2);
  const y = clamp(position.y, (scaledHeight - 1) / 2);
  return { x, y, scaledWidth, scaledHeight, side, left: (width - side) / 2 - x * side, top: (height - side) / 2 - y * side };
}

export async function encodeAvatar(image, crop) {
  const canvas = document.createElement("canvas");
  canvas.width = canvas.height = AVATAR_SIDE;
  const context = canvas.getContext("2d", { alpha: false });
  if (!context) throw new Error("Не вдалося підготувати фото.");
  context.fillStyle = "#fff";
  context.fillRect(0, 0, AVATAR_SIDE, AVATAR_SIDE);
  context.imageSmoothingQuality = "high";
  context.drawImage(image, crop.left, crop.top, crop.side, crop.side, 0, 0, AVATAR_SIDE, AVATAR_SIDE);
  for (const quality of [0.9, 0.75, 0.6]) {
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, "image/jpeg", quality));
    if (blob?.type === "image/jpeg" && blob.size <= MAX_AVATAR_OUTPUT_BYTES) {
      return new File([blob], "avatar.jpg", { type: blob.type });
    }
  }
  throw new Error("Не вдалося стиснути фото до 500 КБ.");
}
