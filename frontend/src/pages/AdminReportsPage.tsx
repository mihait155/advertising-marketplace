import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { adminApi } from '../api/endpoints';
import type { Report, ReportResolution, ReportStatus } from '../api/types';
import { Badge } from '../components/Badge';
import { Button } from '../components/Button';
import { EmptyState, LoadingState } from '../components/EmptyState';
import { Notice, errorMessage } from '../components/Notice';
import { PageHeader } from '../components/PageHeader';
import { Tabs } from '../components/Tabs';
import { formatDateTime, formatRelative } from '../lib/format';
import styles from './AdminReportsPage.module.css';

export function AdminReportsPage() {
  const [status, setStatus] = useState<ReportStatus>('OPEN');
  const reports = useQuery({ queryKey: ['admin', 'reports', status], queryFn: () => adminApi.reports(status) });

  return (
    <>
      <PageHeader
        title="Reports"
        description="Messages people reported. You can read a conversation only after something in it has been reported."
      />
      <Tabs
        label="Report status"
        value={status}
        onChange={setStatus}
        items={[
          { value: 'OPEN', label: 'Open', count: status === 'OPEN' ? reports.data?.page.totalElements : undefined },
          { value: 'RESOLVED', label: 'Resolved' },
        ]}
      />
      {reports.isPending ? (
        <LoadingState />
      ) : reports.isError ? (
        <Notice tone="error">{errorMessage(reports.error)}</Notice>
      ) : reports.data.content.length === 0 ? (
        <EmptyState
          title={status === 'OPEN' ? 'No open reports' : 'No resolved reports'}
          description={status === 'OPEN' ? 'Nothing needs your attention right now.' : undefined}
        />
      ) : (
        <ul className={styles.list}>
          {reports.data.content.map((report) => (
            <ReportItem key={report.id} report={report} />
          ))}
        </ul>
      )}
    </>
  );
}

function ReportItem({ report }: { report: Report }) {
  const queryClient = useQueryClient();
  const [showConversation, setShowConversation] = useState(false);
  const resolve = useMutation({
    mutationFn: (resolution: ReportResolution) => adminApi.resolve(report.id, resolution),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'reports'] }),
  });
  const conversation = useQuery({
    queryKey: ['admin', 'conversation', report.conversationId],
    queryFn: () => adminApi.conversationMessages(report.conversationId),
    enabled: showConversation,
  });
  const message = report.message;

  return (
    <li className={styles.item}>
      <div className={styles.head}>
        <p>
          <strong>{report.reporter.fullName}</strong> reported a message from{' '}
          <strong>{message?.sender?.fullName ?? 'Deleted user'}</strong>
        </p>
        <time className="subtle" dateTime={report.createdAt} title={formatDateTime(report.createdAt)}>
          {formatRelative(report.createdAt)}
        </time>
      </div>
      <blockquote className={styles.quote}>
        {message?.deleted ? <em>Message deleted</em> : (message?.text ?? <em>Image only</em>)}
      </blockquote>
      <p className={styles.reason}>
        <span className={styles.reasonLabel}>Reason</span> {report.reason}
      </p>
      {report.status === 'RESOLVED' && (
        <p className={styles.resolved}>
          <Badge tone={report.resolution === 'MESSAGE_DELETED' ? 'danger' : 'neutral'}>
            {report.resolution === 'MESSAGE_DELETED' ? 'Message deleted' : 'Dismissed'}
          </Badge>
          <span className="subtle">
            by {report.resolvedBy?.fullName} {report.resolvedAt && formatRelative(report.resolvedAt)}
          </span>
        </p>
      )}
      {resolve.error && <Notice tone="error">{errorMessage(resolve.error)}</Notice>}
      <div className={styles.actions}>
        <Button size="sm" variant="ghost" onClick={() => setShowConversation((v) => !v)}>
          {showConversation ? 'Hide conversation' : 'View conversation'}
        </Button>
        {report.status === 'OPEN' && (
          <>
            <Button size="sm" disabled={resolve.isPending} onClick={() => resolve.mutate('DISMISSED')}>
              Dismiss
            </Button>
            <Button
              size="sm"
              variant="danger"
              disabled={resolve.isPending}
              onClick={() => resolve.mutate('MESSAGE_DELETED')}
            >
              Delete message
            </Button>
          </>
        )}
      </div>
      {showConversation && (
        <div className={styles.conversation}>
          {conversation.isPending ? (
            <LoadingState />
          ) : conversation.isError ? (
            <Notice tone="error">{errorMessage(conversation.error)}</Notice>
          ) : (
            <ol className={styles.transcript}>
              {conversation.data.messages
                .slice()
                .reverse()
                .map((m) => (
                  <li key={m.id} className={`${styles.line} ${m.id === message?.id ? styles.highlight : ''}`}>
                    <span className={styles.lineMeta}>
                      {m.type === 'SYSTEM' ? 'System' : m.sender?.fullName} · {formatDateTime(m.createdAt)}
                    </span>
                    <span>{m.deleted ? <em>Message deleted</em> : (m.text ?? <em>Image</em>)}</span>
                  </li>
                ))}
            </ol>
          )}
        </div>
      )}
    </li>
  );
}
