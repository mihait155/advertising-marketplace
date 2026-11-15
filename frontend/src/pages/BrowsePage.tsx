import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useEffect, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router';
import { postingsApi } from '../api/endpoints';
import type { PostingCategory, PostingStatus } from '../api/types';
import { useCurrentUser } from '../auth/AuthContext';
import { Button } from '../components/Button';
import { EmptyState, LoadingState } from '../components/EmptyState';
import { Field, Input, Select } from '../components/Field';
import { Notice, errorMessage } from '../components/Notice';
import { PageHeader } from '../components/PageHeader';
import { Pagination } from '../components/Pagination';
import { PostingList } from '../components/PostingList';
import { Tabs } from '../components/Tabs';
import { useCurrencies } from '../lib/queries';
import styles from './BrowsePage.module.css';

const SORTS = [
  { value: 'createdAt,desc', label: 'Newest first' },
  { value: 'pay,desc', label: 'Highest pay' },
  { value: 'pay,asc', label: 'Lowest pay' },
];

const STATUSES: { value: PostingStatus | ''; label: string }[] = [
  { value: 'OPEN', label: 'Open' },
  { value: 'CLOSED', label: 'Closed' },
  { value: '', label: 'Any status' },
];

const DESCRIPTIONS: Record<PostingCategory, string> = {
  CUSTOMER_POSTING: 'Advertisers looking for marketers to promote their product or brand.',
  MARKETER_POSTING: 'Marketers offering promotion to their audience.',
};

export function BrowsePage() {
  const user = useCurrentUser();
  const [params, setParams] = useSearchParams();
  const currencies = useCurrencies();

  const defaultCategory: PostingCategory = user.userType === 'CUSTOMER' ? 'MARKETER_POSTING' : 'CUSTOMER_POSTING';
  const category = (params.get('category') as PostingCategory | null) ?? defaultCategory;
  const status = (params.has('status') ? params.get('status') : 'OPEN') as PostingStatus | '';
  const sort = params.get('sort') ?? 'createdAt,desc';
  const page = Number(params.get('page') ?? 0);
  const minPay = params.get('minPay') ?? '';
  const maxPay = params.get('maxPay') ?? '';
  const currency = params.get('currency') ?? 'EUR';

  const [draftMin, setDraftMin] = useState(minPay);
  const [draftMax, setDraftMax] = useState(maxPay);
  const [draftCurrency, setDraftCurrency] = useState(currency);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const hasPayFilter = Boolean(minPay || maxPay);

  useEffect(() => {
    setDraftMin(minPay);
    setDraftMax(maxPay);
    setDraftCurrency(currency);
  }, [minPay, maxPay, currency]);

  const update = (changes: Record<string, string | null>, resetPage = true) => {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(changes)) {
      if (value === null) {
        next.delete(key);
      } else {
        next.set(key, value);
      }
    }
    if (resetPage) {
      next.delete('page');
    }
    setParams(next);
  };

  const query = useQuery({
    queryKey: ['postings', 'browse', { category, status, sort, page, minPay, maxPay, currency }],
    queryFn: () =>
      postingsApi.list({
        category,
        status: status || undefined,
        sort,
        page,
        size: 20,
        minPay: minPay ? Number(minPay) : undefined,
        maxPay: maxPay ? Number(maxPay) : undefined,
        currency: minPay || maxPay ? currency : undefined,
      }),
    placeholderData: keepPreviousData,
  });

  const applyPay = (event: FormEvent) => {
    event.preventDefault();
    update({
      minPay: draftMin || null,
      maxPay: draftMax || null,
      currency: draftMin || draftMax ? draftCurrency : null,
    });
  };

  const total = query.data?.page.totalElements;

  return (
    <>
      <PageHeader title="Browse" description={DESCRIPTIONS[category]} />
      <Tabs
        label="Posting type"
        value={category}
        onChange={(value) => update({ category: value })}
        items={[
          { value: 'CUSTOMER_POSTING', label: 'Campaigns' },
          { value: 'MARKETER_POSTING', label: 'Services' },
        ]}
      />
      <div className={styles.layout}>
        <button
          type="button"
          className={styles.filtersToggle}
          aria-expanded={filtersOpen}
          aria-controls="browse-filters"
          onClick={() => setFiltersOpen((open) => !open)}
        >
          Filters
          {(hasPayFilter || status !== 'OPEN') && <span className={styles.activeDot} aria-label="active" />}
        </button>
        <aside
          id="browse-filters"
          className={`${styles.filters} ${filtersOpen ? styles.filtersOpen : ''}`}
          aria-label="Filters"
        >
          <form className={styles.group} onSubmit={applyPay}>
            <p className={styles.groupTitle}>Pay</p>
            <div className={styles.payInputs}>
              <Field label="Min">
                {(props) => (
                  <Input
                    {...props}
                    inputMode="decimal"
                    type="number"
                    min="0"
                    placeholder="0"
                    value={draftMin}
                    onChange={(e) => setDraftMin(e.target.value)}
                  />
                )}
              </Field>
              <Field label="Max">
                {(props) => (
                  <Input
                    {...props}
                    inputMode="decimal"
                    type="number"
                    min="0"
                    placeholder="Any"
                    value={draftMax}
                    onChange={(e) => setDraftMax(e.target.value)}
                  />
                )}
              </Field>
            </div>
            <Field label="Currency" hint="Postings in other currencies are converted at today’s ECB rate.">
              {(props) => (
                <Select {...props} value={draftCurrency} onChange={(e) => setDraftCurrency(e.target.value)}>
                  {(currencies.data ?? [{ code: 'EUR', name: 'Euro' }]).map((c) => (
                    <option key={c.code} value={c.code}>
                      {c.code} · {c.name}
                    </option>
                  ))}
                </Select>
              )}
            </Field>
            <div className={styles.payActions}>
              <Button type="submit" size="sm">
                Apply
              </Button>
              {hasPayFilter && (
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => update({ minPay: null, maxPay: null, currency: null })}
                >
                  Clear
                </Button>
              )}
            </div>
          </form>
          <div className={styles.group}>
            <p className={styles.groupTitle}>Status</p>
            <div className={styles.options} role="radiogroup" aria-label="Status">
              {STATUSES.map((option) => (
                <label key={option.value || 'any'} className={styles.option}>
                  <input
                    type="radio"
                    name="status"
                    checked={status === option.value}
                    onChange={() => update({ status: option.value })}
                  />
                  {option.label}
                </label>
              ))}
            </div>
          </div>
        </aside>
        <section className={styles.results} aria-live="polite">
          <div className={styles.resultsBar}>
            <p className={styles.count}>
              {total === undefined ? ' ' : `${total} ${total === 1 ? 'posting' : 'postings'}`}
            </p>
            <label className={styles.sort}>
              <span className="muted">Sort</span>
              <Select value={sort} onChange={(e) => update({ sort: e.target.value })} aria-label="Sort">
                {SORTS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </Select>
            </label>
          </div>
          {query.isPending ? (
            <LoadingState />
          ) : query.isError ? (
            <Notice tone="error">{errorMessage(query.error)}</Notice>
          ) : query.data.content.length === 0 ? (
            <EmptyState
              title="No postings match"
              description={
                hasPayFilter || status !== 'OPEN'
                  ? 'Try widening the pay range or changing the status filter.'
                  : 'Nothing has been posted here yet. Check back soon.'
              }
            />
          ) : (
            <>
              <PostingList postings={query.data.content} showStatus="notOpen" />
              <Pagination
                page={page}
                totalPages={query.data.page.totalPages}
                onChange={(next) => update({ page: String(next) }, false)}
              />
            </>
          )}
        </section>
      </div>
    </>
  );
}
