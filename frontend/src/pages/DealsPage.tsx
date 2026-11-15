import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';
import { dealsApi } from '../api/endpoints';
import type { DealStatus } from '../api/types';
import { DealStatusBadge } from '../components/Badge';
import { EmptyState, LoadingState } from '../components/EmptyState';
import { Notice, errorMessage } from '../components/Notice';
import { PageHeader } from '../components/PageHeader';
import { Pagination } from '../components/Pagination';
import { Tabs } from '../components/Tabs';
import { formatMoney, formatRelative } from '../lib/format';
import styles from './MyOffersPage.module.css';

type Filter = DealStatus | 'ALL';

export function DealsPage() {
  const [filter, setFilter] = useState<Filter>('ALL');
  const [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: ['deals', filter, page],
    queryFn: () => dealsApi.list(filter === 'ALL' ? undefined : filter, page),
    placeholderData: keepPreviousData,
  });

  return (
    <>
      <PageHeader title="Deals" description="Accepted offers, from delivery to approval and reviews." />
      <Tabs
        label="Deal status"
        value={filter}
        onChange={(value) => {
          setFilter(value);
          setPage(0);
        }}
        items={[
          { value: 'ALL', label: 'All' },
          { value: 'IN_PROGRESS', label: 'In progress' },
          { value: 'DELIVERED', label: 'Awaiting approval' },
          { value: 'COMPLETED', label: 'Completed' },
          { value: 'CANCELLED', label: 'Cancelled' },
        ]}
      />
      {query.isPending ? (
        <LoadingState />
      ) : query.isError ? (
        <Notice tone="error">{errorMessage(query.error)}</Notice>
      ) : query.data.content.length === 0 ? (
        <EmptyState
          title="No deals here"
          description="A deal starts when an advertiser accepts a marketer’s offer, or the other way around."
        />
      ) : (
        <>
          <ul className={styles.list}>
            {query.data.content.map((deal) => {
              const counterpart = deal.myRole === 'MARKETER' ? deal.customer : deal.marketer;
              return (
                <li key={deal.id} className={styles.item}>
                  <div className={styles.main}>
                    <div className={styles.titleRow}>
                      <Link to={`/deals/${deal.id}`} className={styles.title}>
                        {deal.postingTitle}
                      </Link>
                    </div>
                    <p className={styles.meta}>
                      With {counterpart.fullName} · started {formatRelative(deal.createdAt)}
                    </p>
                  </div>
                  <div className={styles.side}>
                    <span className={styles.price}>{formatMoney(deal.amount, deal.currency)}</span>
                    <DealStatusBadge status={deal.status} />
                  </div>
                </li>
              );
            })}
          </ul>
          <Pagination page={page} totalPages={query.data.page.totalPages} onChange={setPage} />
        </>
      )}
    </>
  );
}
