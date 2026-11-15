import { describe, expect, it } from 'vitest';
import { formatCompact, formatDayHeading, formatMoney, formatRange, formatRelative, initials } from './format';

describe('formatMoney', () => {
  it('uses symbols for currencies that have a compact one', () => {
    expect(formatMoney(1500, 'EUR')).toBe('€1,500');
    expect(formatMoney(1500.5, 'USD')).toBe('$1,500.50');
    expect(formatMoney(400, 'GBP')).toBe('£400');
  });

  it('falls back to a trailing code for letter symbols', () => {
    expect(formatMoney(1500, 'RON')).toBe('1,500 RON');
  });
});

describe('formatRange', () => {
  it('formats closed, open-ended, fixed and missing ranges', () => {
    expect(formatRange(500, 2000, 'EUR')).toBe('€500 – €2,000');
    expect(formatRange(500, null, 'EUR')).toBe('From €500');
    expect(formatRange(null, 300, 'GBP')).toBe('Up to £300');
    expect(formatRange(600, 600, 'EUR')).toBe('€600');
    expect(formatRange(1500, 3000, 'RON')).toBe('1,500 – 3,000 RON');
    expect(formatRange(null, null, 'EUR')).toBeNull();
    expect(formatRange(100, 200, null)).toBeNull();
  });
});

describe('dates', () => {
  const now = new Date('2026-11-05T12:00:00Z');

  it('formats relative times', () => {
    expect(formatRelative('2026-11-05T11:59:40Z', now)).toBe('just now');
    expect(formatRelative('2026-11-05T11:45:00Z', now)).toBe('15 minutes ago');
    expect(formatRelative('2026-11-05T09:00:00Z', now)).toBe('3 hours ago');
    expect(formatRelative('2026-11-03T12:00:00Z', now)).toBe('2 days ago');
    expect(formatRelative('2026-09-01T12:00:00Z', now)).toBe('Sep 1');
    expect(formatRelative('2025-09-01T12:00:00Z', now)).toBe('Sep 1, 2025');
  });

  it('labels message days', () => {
    expect(formatDayHeading('2026-11-05T08:00:00', new Date('2026-11-05T20:00:00'))).toBe('Today');
    expect(formatDayHeading('2026-11-04T08:00:00', new Date('2026-11-05T20:00:00'))).toBe('Yesterday');
  });
});

describe('initials', () => {
  it('takes first and last initials', () => {
    expect(initials('Maya Chen')).toBe('MC');
    expect(initials('Lumen Coffee Roasters')).toBe('LR');
    expect(initials('acme')).toBe('A');
    expect(initials('  ')).toBe('?');
  });
});

describe('formatCompact', () => {
  it('abbreviates large audience numbers', () => {
    expect(formatCompact(950)).toBe('950');
    expect(formatCompact(12_400)).toBe('12.4K');
    expect(formatCompact(2_000_000)).toBe('2M');
  });
});
