import { describe, expect, it } from 'vitest';
import type { Image } from '../api/types';
import { excerpt, imageIdFromRef, imageMarkdown, insertAtCursor, resolveImageSrc } from './markdown';

const image: Image = {
  id: '0f8fad5b-d9cb-469f-a165-70867728950e',
  contentType: 'image/png',
  sizeBytes: 10,
  url: 'https://bucket.example/images/a.png?sig=1',
  urlExpiresAt: '2026-11-05T13:00:00Z',
  createdAt: '2026-11-05T12:00:00Z',
};

describe('image references', () => {
  it('recognises uploaded image references only', () => {
    expect(imageIdFromRef(`image:${image.id}`)).toBe(image.id);
    expect(imageIdFromRef(`image:${image.id.toUpperCase()}`)).toBe(image.id);
    expect(imageIdFromRef('https://evil.example/pixel.gif')).toBeNull();
    expect(imageIdFromRef(undefined)).toBeNull();
  });

  it('resolves references to signed URLs', () => {
    expect(resolveImageSrc(`image:${image.id}`, [image])).toBe(image.url);
    expect(resolveImageSrc(`image:${crypto.randomUUID()}`, [image])).toBeUndefined();
  });

  it('builds markdown for an uploaded image', () => {
    expect(imageMarkdown(image, 'Key art')).toBe(`![Key art](image:${image.id})`);
  });
});

describe('excerpt', () => {
  it('strips markdown and images', () => {
    const text = `# Launch\n\n![art](image:${image.id})\n\nWe need **streamers** for [our game](https://x.y).\n- two sessions`;
    expect(excerpt(text)).toBe('Launch We need streamers for our game. two sessions');
  });

  it('truncates on a word boundary', () => {
    const result = excerpt('word '.repeat(60), 40);
    expect(result.length).toBeLessThanOrEqual(41);
    expect(result.endsWith('word…')).toBe(true);
  });
});

describe('insertAtCursor', () => {
  it('puts an image on its own paragraph and returns the new cursor', () => {
    const result = insertAtCursor('Intro text', '![](image:x)', 10, 10);
    expect(result.text).toBe('Intro text\n\n![](image:x)\n');
    expect(result.cursor).toBe(result.text.length);
  });

  it('replaces a selection', () => {
    expect(insertAtCursor('a REPLACE b', 'X', 2, 9).text).toBe('a \n\nX\n b');
  });
});
