import type { Image } from '../api/types';

const IMAGE_REF = /^image:([0-9a-f-]{36})$/i;

export function imageIdFromRef(src: string | undefined) {
  const match = src ? IMAGE_REF.exec(src) : null;
  return match ? match[1].toLowerCase() : null;
}

export function resolveImageSrc(src: string | undefined, images: Image[]) {
  const id = imageIdFromRef(src);
  if (!id) {
    return undefined;
  }
  return images.find((image) => image.id.toLowerCase() === id)?.url;
}

export function imageMarkdown(image: Image, caption = '') {
  return `![${caption}](image:${image.id})`;
}

export function excerpt(text: string, maxLength = 180) {
  const plain = text
    .replace(/!\[[^\]]*]\([^)]*\)/g, '')
    .replace(/\[([^\]]+)]\([^)]*\)/g, '$1')
    .replace(/^\s{0,3}(#{1,6}|>|[-*+]|\d+\.)\s+/gm, '')
    .replace(/[*_`~]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
  if (plain.length <= maxLength) {
    return plain;
  }
  const cut = plain.slice(0, maxLength);
  return `${cut.slice(0, Math.max(cut.lastIndexOf(' '), maxLength - 20)).trimEnd()}…`;
}

export function insertAtCursor(value: string, insertion: string, selectionStart: number, selectionEnd: number) {
  const before = value.slice(0, selectionStart);
  const after = value.slice(selectionEnd);
  const prefix = before.length > 0 && !before.endsWith('\n') ? '\n\n' : '';
  const suffix = after.startsWith('\n') ? '' : '\n';
  const text = `${before}${prefix}${insertion}${suffix}${after}`;
  return { text, cursor: before.length + prefix.length + insertion.length + suffix.length };
}
