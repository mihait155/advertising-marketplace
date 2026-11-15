import type { OfferStatus, PostingCategory, PostingStatus, UserType } from '../api/types';

const formatters = new Map<string, Intl.NumberFormat>();

function formatter(key: string, options: Intl.NumberFormatOptions) {
  let cached = formatters.get(key);
  if (!cached) {
    cached = new Intl.NumberFormat('en', options);
    formatters.set(key, cached);
  }
  return cached;
}

function digits(amount: number) {
  return Number.isInteger(amount) ? 0 : 2;
}

function compactSymbol(currency: string) {
  const symbol = formatter(`symbol:${currency}`, { style: 'currency', currency, currencyDisplay: 'narrowSymbol' })
    .formatToParts(0)
    .find((part) => part.type === 'currency')?.value;
  return symbol && !/^[A-Za-z]/.test(symbol) ? symbol : null;
}

function formatNumber(amount: number) {
  const fraction = digits(amount);
  return formatter(`number:${fraction}`, { minimumFractionDigits: fraction, maximumFractionDigits: fraction }).format(
    amount,
  );
}

export function formatMoney(amount: number, currency: string) {
  const fraction = digits(amount);
  if (compactSymbol(currency)) {
    return formatter(`money:${currency}:${fraction}`, {
      style: 'currency',
      currency,
      currencyDisplay: 'narrowSymbol',
      minimumFractionDigits: fraction,
      maximumFractionDigits: fraction,
    }).format(amount);
  }
  return `${formatNumber(amount)} ${currency}`;
}

export function formatRange(from: number | null, to: number | null, currency: string | null) {
  if (!currency || (from === null && to === null)) {
    return null;
  }
  if (from !== null && to !== null) {
    if (from === to) {
      return formatMoney(from, currency);
    }
    return compactSymbol(currency)
      ? `${formatMoney(from, currency)} – ${formatMoney(to, currency)}`
      : `${formatNumber(from)} – ${formatNumber(to)} ${currency}`;
  }
  return from !== null ? `From ${formatMoney(from, currency)}` : `Up to ${formatMoney(to as number, currency)}`;
}

const relative = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });
const shortDate = new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric' });
const longDate = new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', year: 'numeric' });
const dateTime = new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' });
const time = new Intl.DateTimeFormat('en', { hour: 'numeric', minute: '2-digit' });
const weekday = new Intl.DateTimeFormat('en', { weekday: 'long' });

export function formatRelative(iso: string, now: Date = new Date()) {
  const date = new Date(iso);
  const seconds = Math.round((date.getTime() - now.getTime()) / 1000);
  const abs = Math.abs(seconds);
  if (abs < 45) {
    return 'just now';
  }
  if (abs < 3600) {
    return relative.format(Math.round(seconds / 60), 'minute');
  }
  if (abs < 86_400) {
    return relative.format(Math.round(seconds / 3600), 'hour');
  }
  if (abs < 7 * 86_400) {
    return relative.format(Math.round(seconds / 86_400), 'day');
  }
  return date.getFullYear() === now.getFullYear() ? shortDate.format(date) : longDate.format(date);
}

export function formatDateTime(iso: string) {
  return dateTime.format(new Date(iso));
}

export function formatTime(iso: string) {
  return time.format(new Date(iso));
}

export function formatDayHeading(iso: string, now: Date = new Date()) {
  const date = new Date(iso);
  const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const days = Math.round((startOfDay(now) - startOfDay(date)) / 86_400_000);
  if (days === 0) {
    return 'Today';
  }
  if (days === 1) {
    return 'Yesterday';
  }
  if (days < 7) {
    return weekday.format(date);
  }
  return date.getFullYear() === now.getFullYear() ? shortDate.format(date) : longDate.format(date);
}

export const userTypeLabel: Record<UserType, string> = {
  MARKETER: 'Marketer',
  CUSTOMER: 'Advertiser',
  ADMIN: 'Admin',
};

export const categoryLabel: Record<PostingCategory, string> = {
  CUSTOMER_POSTING: 'Campaign',
  MARKETER_POSTING: 'Service',
};

export const postingStatusLabel: Record<PostingStatus, string> = {
  DRAFT: 'Draft',
  OPEN: 'Open',
  CLOSED: 'Closed',
};

export const offerStatusLabel: Record<OfferStatus, string> = {
  PENDING: 'Pending',
  ACCEPTED: 'Accepted',
  REJECTED: 'Declined',
  WITHDRAWN: 'Withdrawn',
};

export function initials(name: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) {
    return '?';
  }
  return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}

export function formatBytes(bytes: number) {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${Math.round(bytes / 1024)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

const compact = new Intl.NumberFormat('en', { notation: 'compact', maximumFractionDigits: 1 });

export function formatCompact(value: number) {
  return compact.format(value);
}

export const dealStatusLabel: Record<'IN_PROGRESS' | 'DELIVERED' | 'COMPLETED' | 'CANCELLED', string> = {
  IN_PROGRESS: 'In progress',
  DELIVERED: 'Awaiting approval',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
};

export const providerLabel: Record<'TWITCH' | 'YOUTUBE', string> = {
  TWITCH: 'Twitch',
  YOUTUBE: 'YouTube',
};

export const followerNoun: Record<'TWITCH' | 'YOUTUBE', string> = {
  TWITCH: 'followers',
  YOUTUBE: 'subscribers',
};
