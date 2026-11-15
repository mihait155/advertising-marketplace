import { useQuery } from '@tanstack/react-query';
import { profilesApi } from '../api/endpoints';
import { formatCompact } from '../lib/format';
import { Stars } from './Stars';
import styles from './OffererStats.module.css';

export function OffererStats({ userId }: { userId: string }) {
  const profile = useQuery({
    queryKey: ['profile', userId],
    queryFn: () => profilesApi.get(userId),
    staleTime: 60_000,
  });
  if (!profile.data) {
    return null;
  }
  const { ratingAverage, ratingCount, verifiedReach, completedDeals } = profile.data;
  return (
    <span className={styles.stats}>
      {ratingCount > 0 ? (
        <span className={styles.item}>
          <Stars value={ratingAverage} size={12} /> {ratingAverage.toFixed(1)} ({ratingCount})
        </span>
      ) : (
        <span className={styles.item}>No reviews yet</span>
      )}
      {verifiedReach > 0 && <span className={styles.item}>{formatCompact(verifiedReach)} verified reach</span>}
      {completedDeals > 0 && (
        <span className={styles.item}>
          {completedDeals} {completedDeals === 1 ? 'deal' : 'deals'} completed
        </span>
      )}
    </span>
  );
}
