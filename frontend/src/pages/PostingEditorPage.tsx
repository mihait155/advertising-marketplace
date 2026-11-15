import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { postingsApi, type PostingInput } from '../api/endpoints';
import type { Image, Posting, PostingCategory } from '../api/types';
import { useCurrentUser } from '../auth/AuthContext';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { EmptyState, LoadingState } from '../components/EmptyState';
import { Field, FieldRow, Input, Select, Textarea } from '../components/Field';
import { ArrowLeftIcon } from '../components/Icons';
import { ImageUploadButton } from '../components/ImageUploadButton';
import { Markdown } from '../components/Markdown';
import { Notice, errorMessage } from '../components/Notice';
import { PageHeader } from '../components/PageHeader';
import { fieldErrors, formError } from '../lib/errors';
import { categoryLabel } from '../lib/format';
import { imageMarkdown, insertAtCursor } from '../lib/markdown';
import { useCurrencies } from '../lib/queries';
import styles from './PostingEditorPage.module.css';

const TITLE_MAX = 200;
const CATEGORY_HELP: Record<PostingCategory, string> = {
  CUSTOMER_POSTING: 'A campaign asks marketers to promote your product. Marketers respond with offers.',
  MARKETER_POSTING: 'A service describes the promotion you offer. Advertisers respond with offers.',
};

export function PostingEditorPage() {
  const { id } = useParams();
  const user = useCurrentUser();
  const existing = useQuery({ queryKey: ['posting', id], queryFn: () => postingsApi.get(id!), enabled: Boolean(id) });

  if (id && existing.isPending) {
    return <LoadingState />;
  }
  if (id && existing.isError) {
    return <Notice tone="error">{errorMessage(existing.error)}</Notice>;
  }
  if (existing.data && existing.data.creator.id !== user.id && user.userType !== 'ADMIN') {
    return <EmptyState title="You can’t edit this posting" description="Only its creator can make changes." />;
  }
  if (existing.data?.status === 'CLOSED') {
    return <EmptyState title="This posting is closed" description="Reopen it before making changes." />;
  }
  return <PostingEditor key={id ?? 'new'} posting={existing.data} />;
}

function PostingEditor({ posting }: { posting?: Posting }) {
  const user = useCurrentUser();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const currencies = useCurrencies();
  const textarea = useRef<HTMLTextAreaElement>(null);

  const defaultCategory: PostingCategory = user.userType === 'CUSTOMER' ? 'CUSTOMER_POSTING' : 'MARKETER_POSTING';
  const [category, setCategory] = useState<PostingCategory>(posting?.category ?? defaultCategory);
  const [title, setTitle] = useState(posting?.title ?? '');
  const [text, setText] = useState(posting?.text ?? '');
  const [payFrom, setPayFrom] = useState(posting?.payFrom != null ? String(posting.payFrom) : '');
  const [payTo, setPayTo] = useState(posting?.payTo != null ? String(posting.payTo) : '');
  const [currency, setCurrency] = useState(posting?.payCurrency ?? 'EUR');
  const [images, setImages] = useState<Image[]>(posting?.images ?? []);
  const [mode, setMode] = useState<'write' | 'preview'>('write');
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [pendingCursor, setPendingCursor] = useState<number | null>(null);

  useEffect(() => {
    if (pendingCursor !== null && textarea.current) {
      textarea.current.focus();
      textarea.current.setSelectionRange(pendingCursor, pendingCursor);
      setPendingCursor(null);
    }
  }, [pendingCursor]);

  const input = (): PostingInput => {
    const hasPay = Boolean(payFrom || payTo);
    return {
      category,
      title,
      text,
      payFrom: payFrom ? Number(payFrom) : null,
      payTo: payTo ? Number(payTo) : null,
      payCurrency: hasPay ? currency : null,
    };
  };

  const save = useMutation({
    mutationFn: (status: 'DRAFT' | 'OPEN' | null) =>
      posting ? postingsApi.update(posting.id, input()) : postingsApi.create({ ...input(), status: status ?? 'DRAFT' }),
    onSuccess: (saved) => {
      queryClient.setQueryData(['posting', saved.id], saved);
      queryClient.invalidateQueries({ queryKey: ['postings'] });
      navigate(`/postings/${saved.id}`);
    },
  });
  const errors = fieldErrors(save.error);
  const message = formError(save.error);

  const insertImage = (image: Image) => {
    setImages((current) => [...current, image]);
    const element = textarea.current;
    const start = element?.selectionStart ?? text.length;
    const end = element?.selectionEnd ?? text.length;
    const result = insertAtCursor(text, imageMarkdown(image), start, end);
    setText(result.text);
    setMode('write');
    setPendingCursor(result.cursor);
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate(posting ? null : 'OPEN');
  };

  return (
    <form onSubmit={submit} noValidate className={styles.page}>
      <Link to={posting ? `/postings/${posting.id}` : '/my/postings'} className={styles.back}>
        <ArrowLeftIcon /> {posting ? 'Back to posting' : 'My postings'}
      </Link>
      <PageHeader
        title={posting ? 'Edit posting' : `New ${categoryLabel[category].toLowerCase()}`}
        description={CATEGORY_HELP[category]}
      />
      {message && <Notice tone="error">{message}</Notice>}
      <Card className={styles.card}>
        {user.userType === 'ADMIN' && !posting && (
          <Field label="Type" error={errors.category}>
            {(props) => (
              <Select {...props} value={category} onChange={(e) => setCategory(e.target.value as PostingCategory)}>
                <option value="CUSTOMER_POSTING">Campaign (from an advertiser)</option>
                <option value="MARKETER_POSTING">Service (from a marketer)</option>
              </Select>
            )}
          </Field>
        )}
        <Field label="Title" error={errors.title} hint={`${title.length}/${TITLE_MAX}`}>
          {(props) => (
            <Input
              {...props}
              value={title}
              maxLength={TITLE_MAX}
              placeholder={
                category === 'CUSTOMER_POSTING'
                  ? 'e.g. Gaming streamers for a product launch'
                  : 'e.g. Sponsored segment on my weekly stream'
              }
              onChange={(e) => setTitle(e.target.value)}
            />
          )}
        </Field>
        <div className={styles.editor}>
          <div className={styles.editorBar}>
            <div className={styles.modes} role="tablist" aria-label="Editor mode">
              {(['write', 'preview'] as const).map((value) => (
                <button
                  key={value}
                  type="button"
                  role="tab"
                  aria-selected={mode === value}
                  className={styles.mode}
                  onClick={() => setMode(value)}
                >
                  {value === 'write' ? 'Write' : 'Preview'}
                </button>
              ))}
            </div>
            <ImageUploadButton onUploaded={insertImage} onError={setUploadError} />
          </div>
          {uploadError && (
            <div className={styles.uploadError}>
              <Notice tone="error">{uploadError}</Notice>
            </div>
          )}
          {mode === 'write' ? (
            <Textarea
              ref={textarea}
              aria-label="Description"
              aria-invalid={errors.text ? true : undefined}
              className={styles.textarea}
              rows={14}
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="Describe the audience, deliverables, timeline and anything else that matters."
            />
          ) : (
            <div className={styles.preview}>
              {text.trim() ? (
                <Markdown text={text} images={images} />
              ) : (
                <p className="subtle">Nothing to preview yet.</p>
              )}
            </div>
          )}
          <p className={styles.editorHint}>
            {errors.text ? (
              <span className={styles.error}>{errors.text}</span>
            ) : (
              'Supports Markdown: **bold**, lists, links and headings. Images are virus-scanned before they’re stored.'
            )}
          </p>
        </div>
      </Card>
      <Card className={styles.card}>
        <div>
          <h3 className={styles.sectionTitle}>Pay</h3>
          <p className="muted">Optional. Leave a side empty for an open-ended range like “from €500”.</p>
        </div>
        <FieldRow>
          <Field label="From" error={errors.payFrom ?? errors.payRangeOrdered}>
            {(props) => (
              <Input
                {...props}
                type="number"
                min="0"
                step="0.01"
                inputMode="decimal"
                value={payFrom}
                onChange={(e) => setPayFrom(e.target.value)}
              />
            )}
          </Field>
          <Field label="To" error={errors.payTo}>
            {(props) => (
              <Input
                {...props}
                type="number"
                min="0"
                step="0.01"
                inputMode="decimal"
                value={payTo}
                onChange={(e) => setPayTo(e.target.value)}
              />
            )}
          </Field>
          <Field label="Currency" error={errors.payCurrency ?? errors.payCurrencyConsistent}>
            {(props) => (
              <Select {...props} value={currency} onChange={(e) => setCurrency(e.target.value)}>
                {(currencies.data ?? [{ code: 'EUR', name: 'Euro' }]).map((c) => (
                  <option key={c.code} value={c.code}>
                    {c.code} · {c.name}
                  </option>
                ))}
              </Select>
            )}
          </Field>
        </FieldRow>
      </Card>
      <div className={styles.footer}>
        {posting ? (
          <Button type="submit" variant="primary" loading={save.isPending}>
            Save changes
          </Button>
        ) : (
          <>
            <Button
              loading={save.isPending && save.variables === 'DRAFT'}
              disabled={save.isPending}
              onClick={() => save.mutate('DRAFT')}
            >
              Save as draft
            </Button>
            <Button
              type="submit"
              variant="primary"
              loading={save.isPending && save.variables === 'OPEN'}
              disabled={save.isPending}
            >
              Publish
            </Button>
          </>
        )}
      </div>
    </form>
  );
}
