import { Link } from 'react-router';
import type { Posting } from '../api/types';
import { formatRange, formatRelative, userTypeLabel } from '../lib/format';
import { excerpt } from '../lib/markdown';
import { CategoryBadge, PostingStatusBadge } from './Badge';
import styles from './PostingList.module.css';

export function PostingList({
  postings,
  showCategory,
  showStatus,
}: {
  postings: Posting[];
  showCategory?: boolean;
  showStatus?: 'always' | 'notOpen';
}) {
  return (
    <ul className={styles.list}>
      {postings.map((posting) => {
        const pay = formatRange(posting.payFrom, posting.payTo, posting.payCurrency);
        const statusVisible = showStatus === 'always' || (showStatus === 'notOpen' && posting.status !== 'OPEN');
        return (
          <li key={posting.id} className={styles.item}>
            <div className={styles.main}>
              <div className={styles.titleRow}>
                <Link to={`/postings/${posting.id}`} className={styles.title}>
                  {posting.title}
                </Link>
                {showCategory && <CategoryBadge category={posting.category} />}
                {statusVisible && <PostingStatusBadge status={posting.status} />}
              </div>
              <p className={styles.meta}>
                {posting.creator.fullName}
                {posting.creator.userType && <> · {userTypeLabel[posting.creator.userType]}</>}
                <span className={styles.dot}>·</span>
                <time dateTime={posting.createdAt} title={new Date(posting.createdAt).toLocaleString()}>
                  {formatRelative(posting.createdAt)}
                </time>
              </p>
              <p className={styles.excerpt}>{excerpt(posting.text)}</p>
            </div>
            <div className={styles.side}>
              {pay ? <span className={styles.pay}>{pay}</span> : <span className={styles.noPay}>Pay not listed</span>}
              {posting.images.length > 0 && (
                <span className={styles.images}>
                  {posting.images.length} {posting.images.length === 1 ? 'image' : 'images'}
                </span>
              )}
            </div>
          </li>
        );
      })}
    </ul>
  );
}
