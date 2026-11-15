import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';
import { offersApi } from '../api/endpoints';
import type { OfferStatus } from '../api/types';
import { useCurrentUser } from '../auth/AuthContext';
import { OfferStatusBadge, PostingStatusBadge } from '../components/Badge';
import { Button, LinkButton } from '../components/Button';
import { EmptyState, LoadingState } from '../components/EmptyState';
import { Notice, errorMessage } from '../components/Notice';
import { PageHeader } from '../components/PageHeader';
import { Pagination } from '../components/Pagination';
import { Tabs } from '../components/Tabs';
import { formatRange, formatRelative } from '../lib/format';
import { UserLink } from '../components/UserLink';
import styles from './MyOffersPage.module.css';

type Filter = OfferStatus | 'ALL';

export function MyOffersPage() {
  const user = useCurrentUser();
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<Filter>('PENDING');
  const [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: ['offers', 'mine', filter, page],
    queryFn: () => offersApi.mine(filter === 'ALL' ? undefined : filter, page),
    placeholderData: keepPreviousData,
  });
  const withdraw = useMutation({
    mutationFn: (id: string) => offersApi.changeStatus(id, 'WITHDRAWN'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['offers'] }),
  });

  const browseHint =
    user.userType === 'MARKETER'
      ? 'Browse campaigns from advertisers and send your first offer.'
      : 'Browse services from marketers and send your first offer.';

  return (
    <>
      <PageHeader title="My offers" description="Offers you’ve sent on other people’s postings." />
      <Tabs
        label="Status"
        value={filter}
        onChange={(value) => {
          setFilter(value);
          setPage(0);
        }}
        items={[
          { value: 'PENDING', label: 'Pending' },
          { value: 'ACCEPTED', label: 'Accepted' },
          { value: 'REJECTED', label: 'Declined' },
          { value: 'WITHDRAWN', label: 'Withdrawn' },
          { value: 'ALL', label: 'All' },
        ]}
      />
      {withdraw.error && <Notice tone="error">{errorMessage(withdraw.error)}</Notice>}
      {query.isPending ? (
        <LoadingState />
      ) : query.isError ? (
        <Notice tone="error">{errorMessage(query.error)}</Notice>
      ) : query.data.content.length === 0 ? (
        <EmptyState
          title={filter === 'PENDING' ? 'No pending offers' : 'No offers here'}
          description={filter === 'PENDING' || filter === 'ALL' ? browseHint : undefined}
          action={(filter === 'PENDING' || filter === 'ALL') && <LinkButton to="/postings">Browse postings</LinkButton>}
        />
      ) : (
        <>
          <ul className={styles.list}>
            {query.data.content.map((offer) => (
              <li key={offer.id} className={styles.item}>
                <div className={styles.main}>
                  <div className={styles.titleRow}>
                    <Link to={`/postings/${offer.posting.id}`} className={styles.title}>
                      {offer.posting.title}
                    </Link>
                    {offer.posting.status !== 'OPEN' && <PostingStatusBadge status={offer.posting.status} />}
                  </div>
                  <p className={styles.meta}>
                    To <UserLink user={offer.posting.creator} /> · sent {formatRelative(offer.createdAt)}
                  </p>
                  <p className={styles.text}>{offer.text}</p>
                </div>
                <div className={styles.side}>
                  <span className={styles.price}>{formatRange(offer.priceFrom, offer.priceTo, offer.currency)}</span>
                  <OfferStatusBadge status={offer.status} />
                  {offer.status === 'PENDING' && (
                    <Button
                      size="sm"
                      variant="ghost"
                      loading={withdraw.isPending && withdraw.variables === offer.id}
                      onClick={() => withdraw.mutate(offer.id)}
                    >
                      Withdraw
                    </Button>
                  )}
                  {offer.dealId && (
                    <LinkButton size="sm" variant="primary" to={`/deals/${offer.dealId}`}>
                      View deal
                    </LinkButton>
                  )}
                </div>
              </li>
            ))}
          </ul>
          <Pagination page={page} totalPages={query.data.page.totalPages} onChange={setPage} />
        </>
      )}
    </>
  );
}
