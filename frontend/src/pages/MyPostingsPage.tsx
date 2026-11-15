import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { postingsApi } from '../api/endpoints';
import type { PostingStatus } from '../api/types';
import { useCurrentUser } from '../auth/AuthContext';
import { LinkButton } from '../components/Button';
import { EmptyState, LoadingState } from '../components/EmptyState';
import { PlusIcon } from '../components/Icons';
import { Notice, errorMessage } from '../components/Notice';
import { PageHeader } from '../components/PageHeader';
import { Pagination } from '../components/Pagination';
import { PostingList } from '../components/PostingList';
import { Tabs } from '../components/Tabs';

type Filter = PostingStatus | 'ALL';

const EMPTY: Record<Filter, string> = {
  ALL: 'You haven’t created any postings yet.',
  DRAFT: 'No drafts. Postings you save without publishing show up here.',
  OPEN: 'Nothing is open right now.',
  CLOSED: 'No closed postings.',
};

export function MyPostingsPage() {
  const user = useCurrentUser();
  const [filter, setFilter] = useState<Filter>('ALL');
  const [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: ['postings', 'mine', filter, page],
    queryFn: () =>
      postingsApi.list({ creatorId: user.id, status: filter === 'ALL' ? undefined : filter, page, size: 20 }),
    placeholderData: keepPreviousData,
  });

  return (
    <>
      <PageHeader
        title="My postings"
        description="Drafts are only visible to you until you publish them."
        actions={
          <LinkButton to="/postings/new" variant="primary" icon={<PlusIcon />}>
            New posting
          </LinkButton>
        }
      />
      <Tabs
        label="Status"
        value={filter}
        onChange={(value) => {
          setFilter(value);
          setPage(0);
        }}
        items={[
          { value: 'ALL', label: 'All' },
          { value: 'DRAFT', label: 'Drafts' },
          { value: 'OPEN', label: 'Open' },
          { value: 'CLOSED', label: 'Closed' },
        ]}
      />
      {query.isPending ? (
        <LoadingState />
      ) : query.isError ? (
        <Notice tone="error">{errorMessage(query.error)}</Notice>
      ) : query.data.content.length === 0 ? (
        <EmptyState
          title="Nothing here"
          description={EMPTY[filter]}
          action={filter === 'ALL' && <LinkButton to="/postings/new">Create a posting</LinkButton>}
        />
      ) : (
        <>
          <PostingList postings={query.data.content} showStatus="always" showCategory={user.userType === 'ADMIN'} />
          <Pagination page={page} totalPages={query.data.page.totalPages} onChange={setPage} />
        </>
      )}
    </>
  );
}
