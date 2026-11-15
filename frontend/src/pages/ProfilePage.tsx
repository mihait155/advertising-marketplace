import { useQuery } from '@tanstack/react-query';
import { useParams } from 'react-router';
import { ApiError } from '../api/client';
import { profilesApi } from '../api/endpoints';
import { Avatar } from '../components/Avatar';
import { Badge } from '../components/Badge';
import { Card } from '../components/Card';
import { EmptyState, LoadingState } from '../components/EmptyState';
import { Notice, errorMessage } from '../components/Notice';
import { Stars } from '../components/Stars';
import { UserLink } from '../components/UserLink';
import { followerNoun, formatCompact, formatRelative, providerLabel, userTypeLabel } from '../lib/format';
import { NotFoundPage } from './NotFoundPage';
import styles from './ProfilePage.module.css';

const memberSince = new Intl.DateTimeFormat('en', { month: 'long', year: 'numeric' });

export function ProfilePage() {
  const { id = '' } = useParams();
  const query = useQuery({ queryKey: ['profile', id], queryFn: () => profilesApi.get(id) });

  if (query.isPending) {
    return <LoadingState />;
  }
  if (query.isError) {
    if (query.error instanceof ApiError && query.error.status === 404) {
      return <NotFoundPage />;
    }
    return <Notice tone="error">{errorMessage(query.error)}</Notice>;
  }
  const profile = query.data;

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <Avatar name={profile.fullName} size={64} />
        <div>
          <h1>{profile.fullName}</h1>
          <p className="muted">
            {userTypeLabel[profile.userType]} · Member since {memberSince.format(new Date(profile.memberSince))}
          </p>
        </div>
      </header>

      <div className={styles.stats}>
        <Card className={styles.stat}>
          <p className={styles.statLabel}>Rating</p>
          {profile.ratingCount > 0 ? (
            <p className={styles.statValue}>
              {profile.ratingAverage.toFixed(1)} <Stars value={profile.ratingAverage} size={16} />
              <span className={styles.statNote}>
                {profile.ratingCount} {profile.ratingCount === 1 ? 'review' : 'reviews'}
              </span>
            </p>
          ) : (
            <p className={styles.statEmpty}>No reviews yet</p>
          )}
        </Card>
        <Card className={styles.stat}>
          <p className={styles.statLabel}>Completed deals</p>
          <p className={styles.statValue}>{profile.completedDeals}</p>
        </Card>
        {profile.userType === 'MARKETER' && (
          <Card className={styles.stat}>
            <p className={styles.statLabel}>Verified reach</p>
            {profile.verifiedReach > 0 ? (
              <p className={styles.statValue}>{formatCompact(profile.verifiedReach)}</p>
            ) : (
              <p className={styles.statEmpty}>No connected accounts</p>
            )}
          </Card>
        )}
      </div>

      {profile.accounts.length > 0 && (
        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Audience</h2>
          <ul className={styles.accounts}>
            {profile.accounts.map((account) => (
              <li key={account.id} className={styles.account}>
                {account.avatarUrl ? (
                  <img src={account.avatarUrl} alt="" className={styles.accountAvatar} />
                ) : (
                  <Avatar name={account.displayName} size={40} />
                )}
                <div className={styles.accountMain}>
                  <p className={styles.accountName}>
                    <a href={account.profileUrl} target="_blank" rel="noreferrer">
                      {account.displayName}
                    </a>
                    <Badge tone="outline">{providerLabel[account.provider]}</Badge>
                    <Badge tone="accent">Verified</Badge>
                  </p>
                  <p className="subtle">
                    Connected via {providerLabel[account.provider]} OAuth · numbers updated{' '}
                    {formatRelative(account.lastSyncedAt)}
                  </p>
                </div>
                <div className={styles.accountNumbers}>
                  <p className={styles.followers}>{formatCompact(account.followers)}</p>
                  <p className="subtle">{followerNoun[account.provider]}</p>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Reviews</h2>
        {profile.recentReviews.length === 0 ? (
          <EmptyState title="No reviews yet" description="Reviews appear after completed deals." />
        ) : (
          <ul className={styles.reviews}>
            {profile.recentReviews.map((review) => (
              <li key={review.id} className={styles.review}>
                <div className={styles.reviewHead}>
                  <Stars value={review.rating} />
                  <span className="subtle">{formatRelative(review.createdAt)}</span>
                </div>
                {review.text && <p className={styles.reviewText}>{review.text}</p>}
                <p className="subtle">
                  <UserLink user={review.reviewer} /> · {review.postingTitle}
                </p>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
