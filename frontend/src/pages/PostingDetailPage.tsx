import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { conversationsApi, offersApi, postingsApi } from '../api/endpoints';
import type { Offer, Posting, PostingStatus } from '../api/types';
import { useCurrentUser } from '../auth/AuthContext';
import { Avatar } from '../components/Avatar';
import { CategoryBadge, OfferStatusBadge, PostingStatusBadge } from '../components/Badge';
import { Button, LinkButton } from '../components/Button';
import { Card } from '../components/Card';
import { Dialog } from '../components/Dialog';
import { LoadingState } from '../components/EmptyState';
import { ArrowLeftIcon, MessageIcon } from '../components/Icons';
import { Field, Input } from '../components/Field';
import { Markdown } from '../components/Markdown';
import { Notice, errorMessage } from '../components/Notice';
import { OfferForm } from '../components/OfferForm';
import { OffererStats } from '../components/OffererStats';
import { UserLink } from '../components/UserLink';
import { NotFoundPage } from './NotFoundPage';
import { ApiError } from '../api/client';
import { categoryLabel, formatDateTime, formatRange, formatRelative, userTypeLabel } from '../lib/format';
import styles from './PostingDetailPage.module.css';

const STATUS_ACTIONS: Record<
  PostingStatus,
  { label: string; target: PostingStatus; variant: 'primary' | 'secondary' } | null
> = {
  DRAFT: { label: 'Publish', target: 'OPEN', variant: 'primary' },
  OPEN: { label: 'Close posting', target: 'CLOSED', variant: 'secondary' },
  CLOSED: { label: 'Reopen', target: 'OPEN', variant: 'secondary' },
};

export function PostingDetailPage() {
  const { id = '' } = useParams();
  const user = useCurrentUser();
  const query = useQuery({ queryKey: ['posting', id], queryFn: () => postingsApi.get(id) });

  if (query.isPending) {
    return <LoadingState />;
  }
  if (query.isError) {
    if (query.error instanceof ApiError && query.error.status === 404) {
      return <NotFoundPage />;
    }
    return <Notice tone="error">{errorMessage(query.error)}</Notice>;
  }

  const posting = query.data;
  const isOwner = posting.creator.id === user.id;
  const pay = formatRange(posting.payFrom, posting.payTo, posting.payCurrency);

  return (
    <article>
      <Link to="/postings" className={styles.back}>
        <ArrowLeftIcon /> Browse
      </Link>
      <header className={styles.header}>
        <div className={styles.badges}>
          <CategoryBadge category={posting.category} />
          {posting.status !== 'OPEN' && <PostingStatusBadge status={posting.status} />}
        </div>
        <h1 className={styles.title}>{posting.title}</h1>
        <div className={styles.byline}>
          <Avatar name={posting.creator.fullName} size={28} />
          <span>
            <strong>
              <UserLink user={posting.creator} />
            </strong>
            {posting.creator.userType && <span className="muted"> · {userTypeLabel[posting.creator.userType]}</span>}
          </span>
          <span className={styles.dot}>·</span>
          <time className="muted" dateTime={posting.createdAt} title={formatDateTime(posting.createdAt)}>
            Posted {formatRelative(posting.createdAt)}
          </time>
        </div>
      </header>
      <div className={styles.layout}>
        <div className={styles.main}>
          <Card>
            <Markdown text={posting.text} images={posting.images} />
          </Card>
          {isOwner && <ReceivedOffers posting={posting} />}
        </div>
        <aside className={styles.aside}>
          <Card>
            <p className={styles.label}>Pay</p>
            <p className={styles.pay}>{pay ?? 'Not listed'}</p>
            {posting.updatedAt !== posting.createdAt && (
              <p className={styles.updated}>Updated {formatRelative(posting.updatedAt)}</p>
            )}
          </Card>
          {isOwner ? <OwnerActions posting={posting} /> : <ResponderPanel posting={posting} />}
        </aside>
      </div>
    </article>
  );
}

function OwnerActions({ posting }: { posting: Posting }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [confirmDelete, setConfirmDelete] = useState(false);
  const action = STATUS_ACTIONS[posting.status];

  const statusMutation = useMutation({
    mutationFn: (target: PostingStatus) => postingsApi.changeStatus(posting.id, target),
    onSuccess: (updated) => {
      queryClient.setQueryData(['posting', posting.id], updated);
      queryClient.invalidateQueries({ queryKey: ['postings'] });
    },
  });
  const deleteMutation = useMutation({
    mutationFn: () => postingsApi.remove(posting.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['postings'] });
      navigate('/my/postings');
    },
  });

  return (
    <Card className={styles.actionsCard}>
      <p className={styles.label}>Manage</p>
      {posting.status === 'DRAFT' && (
        <p className={styles.help}>Only you can see this draft. Publish it to start receiving offers.</p>
      )}
      {posting.status === 'CLOSED' && (
        <p className={styles.help}>Closed postings stay visible but don’t accept new offers.</p>
      )}
      {statusMutation.error && <Notice tone="error">{errorMessage(statusMutation.error)}</Notice>}
      {action && (
        <Button
          block
          variant={action.variant}
          loading={statusMutation.isPending}
          onClick={() => statusMutation.mutate(action.target)}
        >
          {action.label}
        </Button>
      )}
      {posting.status !== 'CLOSED' && (
        <LinkButton block to={`/postings/${posting.id}/edit`}>
          Edit posting
        </LinkButton>
      )}
      <Button block variant="ghost" className={styles.deleteButton} onClick={() => setConfirmDelete(true)}>
        Delete
      </Button>
      <Dialog
        open={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        title="Delete this posting?"
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmDelete(false)}>
              Cancel
            </Button>
            <Button variant="danger" loading={deleteMutation.isPending} onClick={() => deleteMutation.mutate()}>
              Delete posting
            </Button>
          </>
        }
      >
        <p>The posting, its offers and its conversations will be removed. This can’t be undone.</p>
        {deleteMutation.error && <Notice tone="error">{errorMessage(deleteMutation.error)}</Notice>}
      </Dialog>
    </Card>
  );
}

function ResponderPanel({ posting }: { posting: Posting }) {
  const user = useCurrentUser();
  const navigate = useNavigate();
  const [editing, setEditing] = useState(false);
  const offerMaker = posting.category === 'CUSTOMER_POSTING' ? 'MARKETER' : 'CUSTOMER';
  const eligible = user.userType === offerMaker;
  const offers = useQuery({
    queryKey: ['offers', 'posting', posting.id],
    queryFn: () => offersApi.forPosting(posting.id),
    enabled: eligible,
  });
  const contact = useMutation({
    mutationFn: () => conversationsApi.start(posting.id),
    onSuccess: (conversation) => navigate(`/messages/${conversation.id}`),
  });

  if (!eligible) {
    return (
      <Card>
        <p className={styles.help}>
          {user.userType === 'ADMIN'
            ? 'You’re viewing this posting as an admin.'
            : `Only ${userTypeLabel[offerMaker].toLowerCase()}s can respond to ${categoryLabel[posting.category].toLowerCase()}s.`}
        </p>
      </Card>
    );
  }

  const myOffers = offers.data?.content ?? [];
  const pending = myOffers.find((offer) => offer.status === 'PENDING');
  const latest = pending ?? myOffers[0];

  return (
    <Card className={styles.actionsCard}>
      {latest && !editing ? (
        <YourOffer
          offer={latest}
          canEdit={latest.status === 'PENDING' && posting.status === 'OPEN'}
          onEdit={() => setEditing(true)}
        />
      ) : posting.status === 'OPEN' ? (
        <>
          <p className={styles.label}>{pending ? 'Update your offer' : 'Make an offer'}</p>
          <OfferForm
            posting={posting}
            offer={editing ? pending : undefined}
            onDone={() => setEditing(false)}
            onCancel={editing ? () => setEditing(false) : undefined}
          />
        </>
      ) : (
        <p className={styles.help}>This posting is closed and isn’t accepting offers.</p>
      )}
      {latest && !pending && posting.status === 'OPEN' && !editing && (
        <Button block variant="primary" onClick={() => setEditing(true)}>
          Make a new offer
        </Button>
      )}
      <div className={styles.divider} />
      {contact.error && <Notice tone="error">{errorMessage(contact.error)}</Notice>}
      <Button
        block
        icon={<MessageIcon />}
        loading={contact.isPending}
        onClick={() => contact.mutate()}
        disabled={posting.status !== 'OPEN' && myOffers.length === 0}
      >
        Message {posting.creator.fullName.split(' ')[0]}
      </Button>
    </Card>
  );
}

function YourOffer({ offer, canEdit, onEdit }: { offer: Offer; canEdit: boolean; onEdit: () => void }) {
  const queryClient = useQueryClient();
  const withdraw = useMutation({
    mutationFn: () => offersApi.changeStatus(offer.id, 'WITHDRAWN'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['offers'] }),
  });
  return (
    <div className={styles.yourOffer}>
      <div className={styles.yourOfferHeader}>
        <p className={styles.label}>Your offer</p>
        <OfferStatusBadge status={offer.status} />
      </div>
      <p className={styles.offerPrice}>{formatRange(offer.priceFrom, offer.priceTo, offer.currency)}</p>
      <p className={styles.offerText}>{offer.text}</p>
      <p className={styles.updated}>Sent {formatRelative(offer.createdAt)}</p>
      {offer.dealId && (
        <LinkButton block size="sm" variant="primary" to={`/deals/${offer.dealId}`}>
          View deal
        </LinkButton>
      )}
      {withdraw.error && <Notice tone="error">{errorMessage(withdraw.error)}</Notice>}
      {canEdit && (
        <div className={styles.inlineActions}>
          <Button size="sm" onClick={onEdit}>
            Revise
          </Button>
          <Button size="sm" variant="ghost" loading={withdraw.isPending} onClick={() => withdraw.mutate()}>
            Withdraw
          </Button>
        </div>
      )}
    </div>
  );
}

function ReceivedOffers({ posting }: { posting: Posting }) {
  const queryClient = useQueryClient();
  const offers = useQuery({
    queryKey: ['offers', 'posting', posting.id],
    queryFn: () => offersApi.forPosting(posting.id),
    refetchOnMount: 'always',
  });
  const conversations = useQuery({
    queryKey: ['conversations', 'posting', posting.id],
    queryFn: () => conversationsApi.forPosting(posting.id),
  });
  const [accepting, setAccepting] = useState<Offer | null>(null);
  const decide = useMutation({
    mutationFn: ({ offer, status, amount }: { offer: Offer; status: 'ACCEPTED' | 'REJECTED'; amount?: number }) =>
      offersApi.changeStatus(offer.id, status, amount),
    onSuccess: () => {
      setAccepting(null);
      queryClient.invalidateQueries({ queryKey: ['offers'] });
      queryClient.invalidateQueries({ queryKey: ['conversations'] });
      queryClient.invalidateQueries({ queryKey: ['deals'] });
    },
  });
  const startAccept = (offer: Offer) => {
    if (offer.priceFrom === offer.priceTo) {
      decide.mutate({ offer, status: 'ACCEPTED', amount: offer.priceFrom });
    } else {
      decide.reset();
      setAccepting(offer);
    }
  };

  const list = offers.data?.content ?? [];
  const conversationWith = (userId: string | null) =>
    conversations.data?.content.find((conversation) => conversation.counterpart.id === userId);

  return (
    <section className={styles.offers}>
      <h2 className={styles.sectionTitle}>
        Offers <span className={styles.sectionCount}>{list.length}</span>
      </h2>
      {decide.error && !accepting && <Notice tone="error">{errorMessage(decide.error)}</Notice>}
      {offers.isPending ? (
        <LoadingState />
      ) : list.length === 0 ? (
        <Card>
          <p className="muted">
            {posting.status === 'DRAFT'
              ? 'Publish this posting to start receiving offers.'
              : 'No offers yet. You’ll be notified when someone responds.'}
          </p>
        </Card>
      ) : (
        <ul className={styles.offerList}>
          {list.map((offer) => {
            const conversation = conversationWith(offer.offerer.id);
            return (
              <li key={offer.id} className={styles.offerItem}>
                <div className={styles.offerHead}>
                  <Avatar name={offer.offerer.fullName} size={32} />
                  <div className={styles.offerWho}>
                    <p className={styles.offerName}>
                      <UserLink user={offer.offerer} />
                    </p>
                    {offer.offerer.id && <OffererStats userId={offer.offerer.id} />}
                    <p className={styles.updated}>{formatRelative(offer.createdAt)}</p>
                  </div>
                  <div className={styles.offerRight}>
                    <span className={styles.offerPrice}>
                      {formatRange(offer.priceFrom, offer.priceTo, offer.currency)}
                    </span>
                    <OfferStatusBadge status={offer.status} />
                  </div>
                </div>
                <p className={styles.offerText}>{offer.text}</p>
                <div className={styles.inlineActions}>
                  {offer.status === 'PENDING' && (
                    <>
                      <Button
                        size="sm"
                        variant="primary"
                        disabled={posting.status !== 'OPEN' || decide.isPending}
                        onClick={() => startAccept(offer)}
                      >
                        Accept
                      </Button>
                      <Button
                        size="sm"
                        disabled={decide.isPending}
                        onClick={() => decide.mutate({ offer, status: 'REJECTED' })}
                      >
                        Decline
                      </Button>
                    </>
                  )}
                  {offer.dealId && (
                    <LinkButton size="sm" variant="primary" to={`/deals/${offer.dealId}`}>
                      View deal
                    </LinkButton>
                  )}
                  {conversation && (
                    <LinkButton size="sm" variant="ghost" to={`/messages/${conversation.id}`} icon={<MessageIcon />}>
                      Reply
                    </LinkButton>
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      )}
      {accepting && (
        <AcceptOfferDialog
          offer={accepting}
          pending={decide.isPending}
          error={decide.error}
          onClose={() => setAccepting(null)}
          onConfirm={(amount) => decide.mutate({ offer: accepting, status: 'ACCEPTED', amount })}
        />
      )}
    </section>
  );
}

function AcceptOfferDialog({
  offer,
  pending,
  error,
  onClose,
  onConfirm,
}: {
  offer: Offer;
  pending: boolean;
  error: Error | null;
  onClose: () => void;
  onConfirm: (amount: number) => void;
}) {
  const [value, setValue] = useState(String(offer.priceFrom));
  const amount = Number(value);
  const valid = value.trim() !== '' && Number.isFinite(amount) && amount >= offer.priceFrom && amount <= offer.priceTo;
  return (
    <Dialog
      open
      onClose={onClose}
      title={`Accept ${offer.offerer.fullName.split(' ')[0]}’s offer`}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" loading={pending} disabled={!valid} onClick={() => onConfirm(amount)}>
            Accept and start deal
          </Button>
        </>
      }
    >
      <p>
        They offered {formatRange(offer.priceFrom, offer.priceTo, offer.currency)}. Agree on the final amount to start
        the deal.
      </p>
      <Field
        label={`Agreed amount (${offer.currency})`}
        error={value.trim() !== '' && !valid ? 'Must be within the offered range.' : undefined}
      >
        {(field) => (
          <Input
            {...field}
            type="number"
            inputMode="decimal"
            min={offer.priceFrom}
            max={offer.priceTo}
            step="any"
            value={value}
            onChange={(event) => setValue(event.target.value)}
          />
        )}
      </Field>
      {error && <Notice tone="error">{errorMessage(error)}</Notice>}
    </Dialog>
  );
}
