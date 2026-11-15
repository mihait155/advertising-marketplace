import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router';
import { ApiError } from '../api/client';
import { dealsApi } from '../api/endpoints';
import type { Deal, Delivery, ReviewState } from '../api/types';
import { Avatar } from '../components/Avatar';
import { DealStatusBadge, DeliveryStatusBadge } from '../components/Badge';
import { Button, LinkButton } from '../components/Button';
import { Card } from '../components/Card';
import { Dialog } from '../components/Dialog';
import { LoadingState } from '../components/EmptyState';
import { Field, Textarea } from '../components/Field';
import { ArrowLeftIcon, MessageIcon } from '../components/Icons';
import { Notice, errorMessage } from '../components/Notice';
import { StarInput, Stars } from '../components/Stars';
import { UserLink } from '../components/UserLink';
import { fieldErrors, formError } from '../lib/errors';
import { formatDateTime, formatMoney, formatRelative, userTypeLabel } from '../lib/format';
import { NotFoundPage } from './NotFoundPage';
import styles from './DealDetailPage.module.css';

export function DealDetailPage() {
  const { id = '' } = useParams();
  const query = useQuery({ queryKey: ['deal', id], queryFn: () => dealsApi.get(id) });

  if (query.isPending) {
    return <LoadingState />;
  }
  if (query.isError) {
    if (query.error instanceof ApiError && query.error.status === 404) {
      return <NotFoundPage />;
    }
    return <Notice tone="error">{errorMessage(query.error)}</Notice>;
  }
  return <DealView deal={query.data} />;
}

function useDealMutation<T>(fn: (input: T) => Promise<Deal>, onDone?: () => void) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (deal) => {
      queryClient.setQueryData(['deal', deal.id], deal);
      queryClient.invalidateQueries({ queryKey: ['deals'] });
      queryClient.invalidateQueries({ queryKey: ['conversations'] });
      onDone?.();
    },
  });
}

function DealView({ deal }: { deal: Deal }) {
  const counterpart = deal.myRole === 'MARKETER' ? deal.customer : deal.marketer;
  const latest = deal.deliveries[0];
  const steps = [
    { label: 'Agreed', at: deal.createdAt, done: true },
    { label: 'Delivered', at: deal.deliveredAt, done: deal.status === 'DELIVERED' || deal.status === 'COMPLETED' },
    { label: 'Completed', at: deal.completedAt, done: deal.status === 'COMPLETED' },
  ];

  return (
    <article>
      <Link to="/deals" className={styles.back}>
        <ArrowLeftIcon /> Deals
      </Link>
      <header className={styles.header}>
        <DealStatusBadge status={deal.status} />
        <h1 className={styles.title}>{deal.postingTitle}</h1>
        <p className="muted">
          {deal.myRole ? `You’re the ${deal.myRole === 'MARKETER' ? 'marketer' : 'advertiser'} · ` : ''}
          with <UserLink user={counterpart} /> · started {formatRelative(deal.createdAt)}
          {deal.postingId && (
            <>
              {' · '}
              <Link to={`/postings/${deal.postingId}`}>View posting</Link>
            </>
          )}
        </p>
      </header>
      <div className={styles.layout}>
        <div className={styles.main}>
          {deal.status !== 'CANCELLED' && (
            <ol className={styles.steps} aria-label="Progress">
              {steps.map((step) => (
                <li key={step.label} className={`${styles.step} ${step.done ? styles.done : ''}`}>
                  <span className={styles.dot} aria-hidden />
                  <span className={styles.stepLabel}>{step.label}</span>
                  {step.done && step.at && <span className={styles.stepAt}>{formatRelative(step.at)}</span>}
                </li>
              ))}
            </ol>
          )}
          <NextStep deal={deal} latest={latest} />
          {deal.status === 'COMPLETED' && deal.reviews && <ReviewSection deal={deal} state={deal.reviews} />}
          {deal.deliveries.length > 0 && (
            <section className={styles.section}>
              <h2 className={styles.sectionTitle}>Deliveries</h2>
              <ul className={styles.deliveries}>
                {deal.deliveries.map((delivery) => (
                  <DeliveryItem key={delivery.id} delivery={delivery} />
                ))}
              </ul>
            </section>
          )}
        </div>
        <aside className={styles.aside}>
          <Card>
            <p className={styles.label}>Agreed amount</p>
            <p className={styles.amount}>{formatMoney(deal.amount, deal.currency)}</p>
          </Card>
          <Card className={styles.parties}>
            {[deal.marketer, deal.customer].map((party) => (
              <div key={party.fullName + party.userType} className={styles.party}>
                <Avatar name={party.fullName} size={32} />
                <div>
                  <UserLink user={party} className={styles.partyName} />
                  <p className="subtle">{party.userType ? userTypeLabel[party.userType] : ''}</p>
                </div>
              </div>
            ))}
            {deal.conversationId && (
              <LinkButton block to={`/messages/${deal.conversationId}`} icon={<MessageIcon />}>
                Open conversation
              </LinkButton>
            )}
          </Card>
          {deal.status === 'IN_PROGRESS' && deal.myRole && <CancelDeal deal={deal} />}
        </aside>
      </div>
    </article>
  );
}

function NextStep({ deal, latest }: { deal: Deal; latest?: Delivery }) {
  const firstName = (name: string) => name.split(' ')[0];
  if (deal.status === 'CANCELLED') {
    return (
      <Notice tone="warning" title={`Cancelled ${deal.cancelledAt ? formatRelative(deal.cancelledAt) : ''}`}>
        {deal.cancelledBy?.fullName ?? 'Someone'}: {deal.cancelReason}
      </Notice>
    );
  }
  if (deal.status === 'IN_PROGRESS') {
    if (deal.myRole === 'MARKETER') {
      return (
        <>
          {latest?.status === 'CHANGES_REQUESTED' && (
            <Notice tone="warning" title="Changes requested">
              {latest.feedback}
            </Notice>
          )}
          <DeliveryForm deal={deal} />
        </>
      );
    }
    return (
      <Card>
        <p className={styles.waiting}>
          Waiting for {firstName(deal.marketer.fullName)} to deliver. You’ll get a message in your conversation when
          they do.
        </p>
      </Card>
    );
  }
  if (deal.status === 'DELIVERED') {
    if (deal.myRole === 'CUSTOMER') {
      return <ApproveCard deal={deal} latest={latest} />;
    }
    return (
      <Card>
        <p className={styles.waiting}>
          Delivered. Waiting for {firstName(deal.customer.fullName)} to approve
          {deal.autoCompleteAt && <> — it’s approved automatically on {formatDateTime(deal.autoCompleteAt)}</>}.
        </p>
      </Card>
    );
  }
  return null;
}

function DeliveryForm({ deal }: { deal: Deal }) {
  const [links, setLinks] = useState('');
  const [note, setNote] = useState('');
  const deliver = useDealMutation(() =>
    dealsApi.deliver(
      deal.id,
      links
        .split('\n')
        .map((link) => link.trim())
        .filter(Boolean),
      note,
    ),
  );
  const errors = fieldErrors(deliver.error);
  const linkError = Object.entries(errors).find(([key]) => key.startsWith('links'))?.[1];

  return (
    <Card>
      <form
        className={styles.form}
        onSubmit={(event: FormEvent) => {
          event.preventDefault();
          deliver.mutate(undefined);
        }}
        noValidate
      >
        <div>
          <h2 className={styles.sectionTitle}>Submit your delivery</h2>
          <p className="muted">Share links to the published content. {deal.customer.fullName} will review them.</p>
        </div>
        {formError(deliver.error) && <Notice tone="error">{formError(deliver.error)}</Notice>}
        <Field label="Links" error={linkError} hint="One per line, e.g. the VOD, video or post URL.">
          {(props) => (
            <Textarea
              {...props}
              rows={3}
              value={links}
              placeholder="https://www.twitch.tv/videos/…"
              onChange={(e) => setLinks(e.target.value)}
            />
          )}
        </Field>
        <Field label="Note" optional error={errors.note}>
          {(props) => <Textarea {...props} rows={3} value={note} onChange={(e) => setNote(e.target.value)} />}
        </Field>
        <div className={styles.actions}>
          <Button type="submit" variant="primary" loading={deliver.isPending} disabled={!links.trim()}>
            Submit for approval
          </Button>
        </div>
      </form>
    </Card>
  );
}

function ApproveCard({ deal, latest }: { deal: Deal; latest?: Delivery }) {
  const [asking, setAsking] = useState(false);
  const [feedback, setFeedback] = useState('');
  const approve = useDealMutation(() => dealsApi.approve(deal.id));
  const changes = useDealMutation(
    () => dealsApi.requestChanges(deal.id, feedback),
    () => setAsking(false),
  );

  return (
    <Card className={styles.form}>
      <div>
        <h2 className={styles.sectionTitle}>Review the delivery</h2>
        <p className="muted">
          Check the links below. If you don’t respond, the delivery is approved automatically
          {deal.autoCompleteAt && <> on {formatDateTime(deal.autoCompleteAt)}</>}.
        </p>
      </div>
      {latest && <DeliveryLinks delivery={latest} />}
      {(approve.error || changes.error) && <Notice tone="error">{errorMessage(approve.error ?? changes.error)}</Notice>}
      <div className={styles.actions}>
        <Button onClick={() => setAsking(true)}>Request changes</Button>
        <Button variant="primary" loading={approve.isPending} onClick={() => approve.mutate(undefined)}>
          Approve delivery
        </Button>
      </div>
      <Dialog
        open={asking}
        onClose={() => setAsking(false)}
        title="Request changes"
        footer={
          <>
            <Button variant="ghost" onClick={() => setAsking(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              loading={changes.isPending}
              disabled={!feedback.trim()}
              onClick={() => changes.mutate(undefined)}
            >
              Send back
            </Button>
          </>
        }
      >
        <Field label="What needs to change?">
          {(props) => <Textarea {...props} rows={4} value={feedback} onChange={(e) => setFeedback(e.target.value)} />}
        </Field>
      </Dialog>
    </Card>
  );
}

function DeliveryLinks({ delivery }: { delivery: Delivery }) {
  return (
    <div className={styles.deliveryBody}>
      <ul className={styles.links}>
        {delivery.links.map((link) => (
          <li key={link}>
            <a href={link} target="_blank" rel="noreferrer nofollow">
              {link}
            </a>
          </li>
        ))}
      </ul>
      {delivery.note && <p className={styles.note}>{delivery.note}</p>}
    </div>
  );
}

function DeliveryItem({ delivery }: { delivery: Delivery }) {
  return (
    <li className={styles.delivery}>
      <div className={styles.deliveryHead}>
        <span className="muted">Submitted {formatRelative(delivery.submittedAt)}</span>
        <DeliveryStatusBadge status={delivery.status} />
      </div>
      <DeliveryLinks delivery={delivery} />
      {delivery.feedback && <p className={styles.feedback}>Feedback: {delivery.feedback}</p>}
    </li>
  );
}

function ReviewSection({ deal, state }: { deal: Deal; state: ReviewState }) {
  const [rating, setRating] = useState(0);
  const [text, setText] = useState('');
  const queryClient = useQueryClient();
  const counterpart = deal.myRole === 'MARKETER' ? deal.customer : deal.marketer;
  const submit = useMutation({
    mutationFn: () => dealsApi.review(deal.id, rating, text),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['deal', deal.id] });
      queryClient.invalidateQueries({ queryKey: ['profile'] });
    },
  });

  return (
    <section className={styles.section}>
      <h2 className={styles.sectionTitle}>Reviews</h2>
      <Card className={styles.form}>
        {state.mine ? (
          <div>
            <p className={styles.label}>Your review</p>
            <p className={styles.reviewLine}>
              <Stars value={state.mine.rating} /> {state.mine.text}
            </p>
          </div>
        ) : state.canReview ? (
          <form
            className={styles.form}
            onSubmit={(event: FormEvent) => {
              event.preventDefault();
              submit.mutate();
            }}
          >
            <p>How was working with {counterpart.fullName}?</p>
            <StarInput value={rating} onChange={setRating} />
            <Field label="Review" optional>
              {(props) => <Textarea {...props} rows={3} value={text} onChange={(e) => setText(e.target.value)} />}
            </Field>
            {submit.error && <Notice tone="error">{errorMessage(submit.error)}</Notice>}
            <div className={styles.actions}>
              <Button type="submit" variant="primary" disabled={rating === 0} loading={submit.isPending}>
                Submit review
              </Button>
            </div>
            <p className="subtle">
              Reviews are hidden until you’ve both reviewed
              {state.revealAt && <>, or until {formatDateTime(state.revealAt)}</>}, so neither of you can respond to the
              other’s review.
            </p>
          </form>
        ) : (
          <p className="muted">The review window for this deal has closed.</p>
        )}
        <div className={styles.divider} />
        {state.theirs ? (
          <div>
            <p className={styles.label}>{counterpart.fullName}’s review of you</p>
            <p className={styles.reviewLine}>
              <Stars value={state.theirs.rating} /> {state.theirs.text}
            </p>
          </div>
        ) : (
          <p className="muted">
            {state.theirsSubmitted
              ? `${counterpart.fullName} has reviewed you. You’ll see it once you leave yours.`
              : `${counterpart.fullName} hasn’t left a review yet.`}
          </p>
        )}
      </Card>
    </section>
  );
}

function CancelDeal({ deal }: { deal: Deal }) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState('');
  const cancel = useDealMutation(
    () => dealsApi.cancel(deal.id, reason),
    () => setOpen(false),
  );
  return (
    <>
      <Button variant="ghost" className={styles.cancel} onClick={() => setOpen(true)}>
        Cancel deal
      </Button>
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title="Cancel this deal?"
        footer={
          <>
            <Button variant="ghost" onClick={() => setOpen(false)}>
              Keep deal
            </Button>
            <Button
              variant="danger"
              disabled={!reason.trim()}
              loading={cancel.isPending}
              onClick={() => cancel.mutate(undefined)}
            >
              Cancel deal
            </Button>
          </>
        }
      >
        <p>The other side will see your reason in the conversation.</p>
        <Field label="Reason">
          {(props) => <Textarea {...props} rows={3} value={reason} onChange={(e) => setReason(e.target.value)} />}
        </Field>
        {cancel.error && <Notice tone="error">{errorMessage(cancel.error)}</Notice>}
      </Dialog>
    </>
  );
}
