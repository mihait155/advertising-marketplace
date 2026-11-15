import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Fragment, useEffect, useLayoutEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { blocksApi, conversationsApi } from '../api/endpoints';
import type { Conversation, Image, Message } from '../api/types';
import { useCurrentUser } from '../auth/AuthContext';
import { Avatar } from '../components/Avatar';
import { CategoryBadge, PostingStatusBadge } from '../components/Badge';
import { Button } from '../components/Button';
import { Dialog } from '../components/Dialog';
import { EmptyState, LoadingState } from '../components/EmptyState';
import { Field, Textarea } from '../components/Field';
import { ArrowLeftIcon, CloseIcon, SendIcon } from '../components/Icons';
import { ImageUploadButton } from '../components/ImageUploadButton';
import { Notice, errorMessage } from '../components/Notice';
import { useRealtime } from '../realtime/RealtimeProvider';
import { formatDateTime, formatDayHeading, formatRelative, formatTime, userTypeLabel } from '../lib/format';
import { UserLink } from '../components/UserLink';
import styles from './MessagesPage.module.css';

const GROUP_WINDOW_MS = 5 * 60 * 1000;

export function MessagesPage() {
  const { conversationId } = useParams();
  const conversations = useQuery({ queryKey: ['conversations', 'list'], queryFn: () => conversationsApi.list() });

  return (
    <div className={`${styles.layout} ${conversationId ? styles.hasThread : ''}`}>
      <aside className={styles.sidebar}>
        <div className={styles.sidebarHeader}>
          <h1 className={styles.heading}>Messages</h1>
        </div>
        {conversations.isPending ? (
          <LoadingState />
        ) : conversations.isError ? (
          <div className={styles.sidebarNotice}>
            <Notice tone="error">{errorMessage(conversations.error)}</Notice>
          </div>
        ) : conversations.data.content.length === 0 ? (
          <div className={styles.sidebarEmpty}>
            <p className={styles.emptyTitle}>No conversations yet</p>
            <p className="muted">Message someone from a posting, or wait for replies to your postings.</p>
          </div>
        ) : (
          <ConversationList conversations={conversations.data.content} activeId={conversationId} />
        )}
      </aside>
      <section className={styles.threadPane}>
        {conversationId ? (
          <Thread key={conversationId} conversationId={conversationId} />
        ) : (
          <div className={styles.placeholder}>
            <EmptyState
              title="Select a conversation"
              description="Your negotiations, offers and messages for each posting live in one thread."
            />
          </div>
        )}
      </section>
    </div>
  );
}

function ConversationList({ conversations, activeId }: { conversations: Conversation[]; activeId?: string }) {
  return (
    <ul className={styles.conversations} aria-label="Conversations">
      {conversations.map((conversation) => {
        const last = conversation.lastMessage;
        const preview = !last
          ? 'No messages yet'
          : last.deleted
            ? 'Message deleted'
            : last.type === 'SYSTEM'
              ? last.text
              : (last.text ?? 'Sent an image');
        return (
          <li key={conversation.id}>
            <Link
              to={`/messages/${conversation.id}`}
              className={`${styles.conversation} ${conversation.id === activeId ? styles.activeConversation : ''}`}
              aria-current={conversation.id === activeId ? 'page' : undefined}
            >
              <Avatar name={conversation.otherParticipant.fullName} size={36} />
              <div className={styles.conversationBody}>
                <div className={styles.conversationTop}>
                  <span
                    className={`${styles.conversationName} ${conversation.unreadCount > 0 ? styles.unreadName : ''}`}
                  >
                    {conversation.otherParticipant.fullName}
                  </span>
                  <time className={styles.conversationTime} dateTime={conversation.lastMessageAt}>
                    {formatRelative(conversation.lastMessageAt)}
                  </time>
                </div>
                <p className={styles.conversationPosting}>{conversation.posting.title}</p>
                <div className={styles.conversationBottom}>
                  <p className={`${styles.preview} ${last?.type === 'SYSTEM' ? styles.systemPreview : ''}`}>
                    {preview}
                  </p>
                  {conversation.unreadCount > 0 && (
                    <span className={styles.unreadCount}>{conversation.unreadCount}</span>
                  )}
                </div>
              </div>
            </Link>
          </li>
        );
      })}
    </ul>
  );
}

function Thread({ conversationId }: { conversationId: string }) {
  const user = useCurrentUser();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { typingIn } = useRealtime();
  const scroller = useRef<HTMLDivElement>(null);
  const stickToBottom = useRef(true);
  const [reporting, setReporting] = useState<Message | null>(null);
  const [confirmBlock, setConfirmBlock] = useState(false);

  const conversation = useQuery({
    queryKey: ['conversation', conversationId],
    queryFn: () => conversationsApi.get(conversationId),
  });
  const messages = useInfiniteQuery({
    queryKey: ['messages', conversationId],
    queryFn: ({ pageParam }) => conversationsApi.messages(conversationId, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (page) => page.nextBefore,
  });

  const ordered = useMemo(
    () =>
      (messages.data?.pages ?? [])
        .flatMap((page) => page.messages)
        .slice()
        .reverse(),
    [messages.data],
  );

  const markRead = useMutation({
    mutationFn: () => conversationsApi.markRead(conversationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['unread'] });
      queryClient.invalidateQueries({ queryKey: ['conversations', 'list'] });
    },
  });

  const unread = conversation.data?.unreadCount ?? 0;
  useEffect(() => {
    if (unread > 0 && document.visibilityState === 'visible' && !markRead.isPending) {
      markRead.mutate();
    }
  }, [unread, ordered.length]);

  const lastId = ordered.at(-1)?.id;
  useLayoutEffect(() => {
    const element = scroller.current;
    if (element && stickToBottom.current) {
      element.scrollTop = element.scrollHeight;
    }
  }, [lastId]);

  const block = useMutation({
    mutationFn: (userId: string) => blocksApi.block(userId),
    onSuccess: () => setConfirmBlock(false),
  });

  if (conversation.isPending) {
    return <LoadingState />;
  }
  if (conversation.isError) {
    return (
      <div className={styles.placeholder}>
        <Notice tone="error">{errorMessage(conversation.error)}</Notice>
      </div>
    );
  }

  const data = conversation.data;
  const other = data.otherParticipant;
  const typing = typingIn(conversationId);
  const lastOwn = [...ordered]
    .reverse()
    .find((message) => message.type === 'USER' && message.sender?.id === user.id && !message.deleted);
  const seen = lastOwn && data.otherParticipantLastReadAt && data.otherParticipantLastReadAt >= lastOwn.createdAt;

  return (
    <div className={styles.thread}>
      <header className={styles.threadHeader}>
        <button
          type="button"
          className={styles.backButton}
          onClick={() => navigate('/messages')}
          aria-label="Back to conversations"
        >
          <ArrowLeftIcon />
        </button>
        <Avatar name={other.fullName} size={36} />
        <div className={styles.threadWho}>
          <p className={styles.threadName}>
            <UserLink user={other} />
            {other.userType && <span className={styles.threadType}>{userTypeLabel[other.userType]}</span>}
          </p>
          <p className={styles.threadPosting}>
            <Link to={`/postings/${data.posting.id}`}>{data.posting.title}</Link>
          </p>
        </div>
        <div className={styles.threadBadges}>
          <CategoryBadge category={data.posting.category} />
          {data.posting.status !== 'OPEN' && <PostingStatusBadge status={data.posting.status} />}
          {other.id && (
            <Button size="sm" variant="ghost" onClick={() => setConfirmBlock(true)}>
              Block
            </Button>
          )}
        </div>
      </header>

      <div
        ref={scroller}
        className={styles.messages}
        onScroll={(event) => {
          const element = event.currentTarget;
          stickToBottom.current = element.scrollHeight - element.scrollTop - element.clientHeight < 80;
        }}
      >
        {messages.hasNextPage && (
          <div className={styles.loadEarlier}>
            <Button
              size="sm"
              variant="ghost"
              loading={messages.isFetchingNextPage}
              onClick={() => messages.fetchNextPage()}
            >
              Load earlier messages
            </Button>
          </div>
        )}
        {messages.isPending ? (
          <LoadingState />
        ) : ordered.length === 0 ? (
          <p className={styles.threadEmpty}>Say hello and introduce yourself.</p>
        ) : (
          <MessageTimeline
            messages={ordered}
            currentUserId={user.id}
            onReport={setReporting}
            seenMessageId={seen ? lastOwn.id : null}
          />
        )}
      </div>

      <div className={styles.typing} aria-live="polite">
        {typing && `${other.fullName.split(' ')[0]} is typing…`}
      </div>

      {other.id ? (
        <Composer conversationId={conversationId} onSent={() => (stickToBottom.current = true)} />
      ) : (
        <div className={styles.composerDisabled}>
          This person deleted their account. You can still read the conversation.
        </div>
      )}

      <ReportDialog message={reporting} onClose={() => setReporting(null)} />
      <Dialog
        open={confirmBlock}
        onClose={() => setConfirmBlock(false)}
        title={`Block ${other.fullName}?`}
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmBlock(false)}>
              Cancel
            </Button>
            <Button variant="danger" loading={block.isPending} onClick={() => other.id && block.mutate(other.id)}>
              Block
            </Button>
          </>
        }
      >
        <p>
          Neither of you will be able to send messages or offers to the other. You can unblock them later in Settings.
        </p>
        {block.error && <Notice tone="error">{errorMessage(block.error)}</Notice>}
      </Dialog>
    </div>
  );
}

function MessageTimeline({
  messages,
  currentUserId,
  onReport,
  seenMessageId,
}: {
  messages: Message[];
  currentUserId: string;
  onReport: (message: Message) => void;
  seenMessageId: string | null;
}) {
  return (
    <div className={styles.timeline}>
      {messages.map((message, index) => {
        const previous = messages[index - 1];
        const newDay =
          !previous || new Date(previous.createdAt).toDateString() !== new Date(message.createdAt).toDateString();
        const grouped =
          !newDay &&
          previous.type === 'USER' &&
          message.type === 'USER' &&
          previous.sender?.id === message.sender?.id &&
          new Date(message.createdAt).getTime() - new Date(previous.createdAt).getTime() < GROUP_WINDOW_MS;
        return (
          <Fragment key={message.id}>
            {newDay && (
              <div className={styles.day}>
                <span>{formatDayHeading(message.createdAt)}</span>
              </div>
            )}
            {message.type === 'SYSTEM' ? (
              <div className={styles.system}>
                <span>{message.text}</span>
                <time dateTime={message.createdAt}>{formatTime(message.createdAt)}</time>
              </div>
            ) : (
              <MessageBubble
                message={message}
                own={message.sender?.id === currentUserId}
                grouped={grouped}
                onReport={onReport}
                seen={message.id === seenMessageId}
              />
            )}
          </Fragment>
        );
      })}
    </div>
  );
}

function MessageBubble({
  message,
  own,
  grouped,
  onReport,
  seen,
}: {
  message: Message;
  own: boolean;
  grouped: boolean;
  onReport: (message: Message) => void;
  seen: boolean;
}) {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(message.text ?? '');
  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['messages', message.conversationId] });
    queryClient.invalidateQueries({ queryKey: ['conversations', 'list'] });
  };
  const edit = useMutation({
    mutationFn: () => conversationsApi.editMessage(message.id, draft),
    onSuccess: () => {
      setEditing(false);
      invalidate();
    },
  });
  const remove = useMutation({ mutationFn: () => conversationsApi.deleteMessage(message.id), onSuccess: invalidate });

  return (
    <div className={`${styles.row} ${own ? styles.own : ''} ${grouped ? styles.grouped : ''}`}>
      <div className={styles.bubbleWrap}>
        {!grouped && !own && <p className={styles.sender}>{message.sender?.fullName}</p>}
        {message.deleted ? (
          <div className={`${styles.bubble} ${styles.deleted}`}>Message deleted</div>
        ) : editing ? (
          <div className={styles.editBox}>
            <Textarea
              aria-label="Edit message"
              rows={3}
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              autoFocus
            />
            {edit.error && <Notice tone="error">{errorMessage(edit.error)}</Notice>}
            <div className={styles.editActions}>
              <Button size="sm" variant="ghost" onClick={() => setEditing(false)}>
                Cancel
              </Button>
              <Button
                size="sm"
                variant="primary"
                loading={edit.isPending}
                disabled={!draft.trim()}
                onClick={() => edit.mutate()}
              >
                Save
              </Button>
            </div>
          </div>
        ) : (
          <div className={styles.bubble} title={formatDateTime(message.createdAt)}>
            {message.images.length > 0 && <Attachments images={message.images} />}
            {message.text && <p className={styles.text}>{message.text}</p>}
          </div>
        )}
        {!editing && (
          <div className={styles.messageMeta}>
            <time dateTime={message.createdAt}>{formatTime(message.createdAt)}</time>
            {message.editedAt && !message.deleted && <span>Edited</span>}
            {seen && <span>Seen</span>}
            {!message.deleted && (
              <span className={styles.messageActions}>
                {own ? (
                  <>
                    {message.text && (
                      <button type="button" onClick={() => setEditing(true)}>
                        Edit
                      </button>
                    )}
                    <button type="button" onClick={() => remove.mutate()} disabled={remove.isPending}>
                      Delete
                    </button>
                  </>
                ) : (
                  <button type="button" onClick={() => onReport(message)}>
                    Report
                  </button>
                )}
              </span>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function Attachments({ images }: { images: Image[] }) {
  return (
    <div className={`${styles.attachments} ${images.length === 1 ? styles.single : ''}`}>
      {images.map((image) => (
        <a key={image.id} href={image.url} target="_blank" rel="noreferrer">
          <img src={image.url} alt="Attachment" loading="lazy" />
        </a>
      ))}
    </div>
  );
}

function Composer({ conversationId, onSent }: { conversationId: string; onSent: () => void }) {
  const queryClient = useQueryClient();
  const { sendTyping } = useRealtime();
  const [text, setText] = useState('');
  const [attachments, setAttachments] = useState<Image[]>([]);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const textarea = useRef<HTMLTextAreaElement>(null);

  const send = useMutation({
    mutationFn: () =>
      conversationsApi.send(
        conversationId,
        text.trim(),
        attachments.map((image) => image.id),
      ),
    onSuccess: () => {
      setText('');
      setAttachments([]);
      onSent();
      queryClient.invalidateQueries({ queryKey: ['messages', conversationId] });
      queryClient.invalidateQueries({ queryKey: ['conversations', 'list'] });
      textarea.current?.focus();
    },
  });

  useLayoutEffect(() => {
    const element = textarea.current;
    if (element) {
      element.style.height = 'auto';
      element.style.height = `${Math.min(element.scrollHeight, 180)}px`;
    }
  }, [text]);

  const canSend = (text.trim().length > 0 || attachments.length > 0) && !send.isPending;
  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      if (canSend) {
        send.mutate();
      }
    }
  };

  return (
    <div className={styles.composer}>
      {(send.error || uploadError) && (
        <div className={styles.composerNotice}>
          <Notice tone="error">{uploadError ?? errorMessage(send.error)}</Notice>
        </div>
      )}
      {attachments.length > 0 && (
        <div className={styles.pending}>
          {attachments.map((image) => (
            <div key={image.id} className={styles.pendingItem}>
              <img src={image.url} alt="" />
              <button
                type="button"
                aria-label="Remove image"
                onClick={() => setAttachments((current) => current.filter((i) => i.id !== image.id))}
              >
                <CloseIcon />
              </button>
            </div>
          ))}
        </div>
      )}
      <div className={styles.composerRow}>
        <ImageUploadButton
          iconOnly
          label="Attach image"
          onError={setUploadError}
          onUploaded={(image) => setAttachments((current) => [...current, image])}
        />
        <textarea
          ref={textarea}
          className={styles.input}
          rows={1}
          placeholder="Write a message"
          aria-label="Message"
          value={text}
          onChange={(event) => {
            setText(event.target.value);
            if (event.target.value) {
              sendTyping(conversationId);
            }
          }}
          onKeyDown={onKeyDown}
        />
        <Button
          variant="primary"
          icon={<SendIcon />}
          disabled={!canSend}
          loading={send.isPending}
          onClick={() => send.mutate()}
        >
          Send
        </Button>
      </div>
    </div>
  );
}

function ReportDialog({ message, onClose }: { message: Message | null; onClose: () => void }) {
  const [reason, setReason] = useState('');
  const report = useMutation({
    mutationFn: () => conversationsApi.reportMessage(message!.id, reason),
    onSuccess: () => {
      setReason('');
    },
  });

  const close = () => {
    report.reset();
    setReason('');
    onClose();
  };

  return (
    <Dialog
      open={message !== null}
      onClose={close}
      title={report.isSuccess ? 'Thanks for reporting' : 'Report message'}
      footer={
        report.isSuccess ? (
          <Button onClick={close}>Close</Button>
        ) : (
          <>
            <Button variant="ghost" onClick={close}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={report.isPending}
              disabled={!reason.trim()}
              onClick={() => report.mutate()}
            >
              Send report
            </Button>
          </>
        )
      }
    >
      {report.isSuccess ? (
        <p>Our team will review it. You can also block this person from the conversation header.</p>
      ) : (
        <>
          {message?.text && <blockquote className={styles.reportQuote}>{message.text}</blockquote>}
          <Field label="What’s wrong with this message?">
            {(props) => (
              <Textarea
                {...props}
                rows={3}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Spam, harassment, scam, asking to pay off-platform…"
              />
            )}
          </Field>
          {report.error && <Notice tone="error">{errorMessage(report.error)}</Notice>}
        </>
      )}
    </Dialog>
  );
}
