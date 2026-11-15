import type { ReactNode } from 'react';
import type { DealStatus, DeliveryStatus, OfferStatus, PostingCategory, PostingStatus } from '../api/types';
import { categoryLabel, dealStatusLabel, offerStatusLabel, postingStatusLabel } from '../lib/format';
import styles from './Badge.module.css';

export type Tone = 'neutral' | 'accent' | 'warning' | 'danger' | 'info' | 'outline';

export function Badge({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  return <span className={`${styles.badge} ${styles[tone]}`}>{children}</span>;
}

const postingTone: Record<PostingStatus, Tone> = { DRAFT: 'warning', OPEN: 'accent', CLOSED: 'neutral' };
const offerTone: Record<OfferStatus, Tone> = {
  PENDING: 'info',
  ACCEPTED: 'accent',
  REJECTED: 'neutral',
  WITHDRAWN: 'neutral',
};

export function PostingStatusBadge({ status }: { status: PostingStatus }) {
  return <Badge tone={postingTone[status]}>{postingStatusLabel[status]}</Badge>;
}

export function OfferStatusBadge({ status }: { status: OfferStatus }) {
  return <Badge tone={offerTone[status]}>{offerStatusLabel[status]}</Badge>;
}

export function CategoryBadge({ category }: { category: PostingCategory }) {
  return <Badge tone="outline">{categoryLabel[category]}</Badge>;
}

const dealTone: Record<DealStatus, Tone> = {
  IN_PROGRESS: 'info',
  DELIVERED: 'warning',
  COMPLETED: 'accent',
  CANCELLED: 'neutral',
};

export function DealStatusBadge({ status }: { status: DealStatus }) {
  return <Badge tone={dealTone[status]}>{dealStatusLabel[status]}</Badge>;
}

const deliveryLabel: Record<DeliveryStatus, { label: string; tone: Tone }> = {
  PENDING: { label: 'Awaiting review', tone: 'warning' },
  APPROVED: { label: 'Approved', tone: 'accent' },
  CHANGES_REQUESTED: { label: 'Changes requested', tone: 'danger' },
};

export function DeliveryStatusBadge({ status }: { status: DeliveryStatus }) {
  return <Badge tone={deliveryLabel[status].tone}>{deliveryLabel[status].label}</Badge>;
}
