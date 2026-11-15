import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { session } from '../api/client';
import { authApi, blocksApi, socialApi, usersApi } from '../api/endpoints';
import type { SocialProvider } from '../api/types';
import { useAuth, useCurrentUser } from '../auth/AuthContext';
import { Avatar } from '../components/Avatar';
import { Button } from '../components/Button';
import { Card, CardHeader } from '../components/Card';
import { Dialog } from '../components/Dialog';
import { LoadingState } from '../components/EmptyState';
import { Field, FieldRow, Input } from '../components/Field';
import { Notice, errorMessage } from '../components/Notice';
import { PageHeader } from '../components/PageHeader';
import { fieldErrors, formError } from '../lib/errors';
import { followerNoun, formatCompact, formatRelative, providerLabel, userTypeLabel } from '../lib/format';
import styles from './SettingsPage.module.css';

export function SettingsPage() {
  const user = useCurrentUser();
  return (
    <div className={styles.page}>
      <PageHeader
        title="Settings"
        description={`Signed in as ${user.email} · ${userTypeLabel[user.userType]} account`}
      />
      <ProfileSection />
      {user.userType === 'MARKETER' && <ConnectedAccountsSection />}
      <PasswordSection />
      <NotificationsSection />
      <BlockedSection />
      <SessionsSection />
    </div>
  );
}

function ProfileSection() {
  const user = useCurrentUser();
  const { setUser } = useAuth();
  const [fullName, setFullName] = useState(user.fullName);
  const [email, setEmail] = useState(user.email);
  const [phone, setPhone] = useState(user.phone ?? '');
  const save = useMutation({
    mutationFn: () => usersApi.update(user, { fullName, email, phone: phone || null }),
    onSuccess: setUser,
  });
  const errors = fieldErrors(save.error);
  const message = formError(save.error);
  const dirty = fullName !== user.fullName || email !== user.email || phone !== (user.phone ?? '');

  return (
    <Card>
      <CardHeader title="Profile" description="Shown to people you message and on your postings." />
      <form
        className={styles.form}
        onSubmit={(event: FormEvent) => {
          event.preventDefault();
          save.mutate();
        }}
        noValidate
      >
        <div className={styles.identity}>
          <Avatar name={fullName || user.fullName} size={44} />
          <div>
            <p className={styles.identityName}>{fullName || user.fullName}</p>
            <p className="muted">{userTypeLabel[user.userType]}</p>
          </div>
        </div>
        {message && <Notice tone="error">{message}</Notice>}
        {save.isSuccess && !dirty && <Notice tone="success">Profile saved.</Notice>}
        <Field label="Full name" error={errors.fullName}>
          {(props) => <Input {...props} value={fullName} onChange={(e) => setFullName(e.target.value)} />}
        </Field>
        <FieldRow>
          <Field label="Email" error={errors.email}>
            {(props) => <Input {...props} type="email" value={email} onChange={(e) => setEmail(e.target.value)} />}
          </Field>
          <Field label="Phone" optional error={errors.phone}>
            {(props) => <Input {...props} type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} />}
          </Field>
        </FieldRow>
        <div className={styles.actions}>
          <Button type="submit" variant="primary" disabled={!dirty} loading={save.isPending}>
            Save profile
          </Button>
        </div>
      </form>
    </Card>
  );
}

function ConnectedAccountsSection() {
  const queryClient = useQueryClient();
  const [params] = useSearchParams();
  const connected = params.get('connected');
  const providers = useQuery({ queryKey: ['social', 'providers'], queryFn: socialApi.providers });
  const accounts = useQuery({ queryKey: ['social', 'accounts'], queryFn: socialApi.accounts });
  const authorize = useMutation({
    mutationFn: (provider: SocialProvider) => socialApi.authorize(provider),
    onSuccess: ({ url }) => window.location.assign(url),
  });
  const disconnect = useMutation({
    mutationFn: (id: string) => socialApi.disconnect(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['social', 'accounts'] });
      queryClient.invalidateQueries({ queryKey: ['profile'] });
    },
  });
  const byProvider = new Map((accounts.data ?? []).map((account) => [account.provider, account]));

  return (
    <Card>
      <CardHeader
        title="Connected accounts"
        description="Connect your channels so advertisers see verified audience numbers on your profile. We only read public channel statistics."
      />
      {connected && (
        <div className={styles.notice}>
          <Notice tone="success">{providerLabel[connected as SocialProvider] ?? 'Account'} connected.</Notice>
        </div>
      )}
      {(authorize.error || disconnect.error) && (
        <div className={styles.notice}>
          <Notice tone="error">{errorMessage(authorize.error ?? disconnect.error)}</Notice>
        </div>
      )}
      {providers.isPending || accounts.isPending ? (
        <LoadingState />
      ) : (
        <ul className={styles.blockList}>
          {(providers.data ?? []).map((provider) => {
            const account = byProvider.get(provider.provider);
            return (
              <li key={provider.provider} className={styles.blockItem}>
                <div className={styles.blockWho}>
                  <p className={styles.toggleTitle}>{provider.name}</p>
                  {account ? (
                    <p className="subtle">
                      {account.displayName} · {formatCompact(account.followers)} {followerNoun[account.provider]} ·
                      updated {formatRelative(account.lastSyncedAt)}
                    </p>
                  ) : (
                    <p className="subtle">
                      {provider.available ? 'Not connected' : 'Not available on this server yet'}
                    </p>
                  )}
                  {account?.needsReconnect && (
                    <p className={styles.warning}>Access expired. Reconnect to keep your numbers up to date.</p>
                  )}
                </div>
                {account && !account.needsReconnect ? (
                  <Button
                    size="sm"
                    variant="ghost"
                    loading={disconnect.isPending && disconnect.variables === account.id}
                    onClick={() => disconnect.mutate(account.id)}
                  >
                    Disconnect
                  </Button>
                ) : (
                  <Button
                    size="sm"
                    disabled={!provider.available}
                    loading={authorize.isPending && authorize.variables === provider.provider}
                    onClick={() => authorize.mutate(provider.provider)}
                  >
                    {account ? 'Reconnect' : 'Connect'}
                  </Button>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}

function PasswordSection() {
  const user = useCurrentUser();
  const { login } = useAuth();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const change = useMutation({
    mutationFn: async () => {
      await authApi.changePassword(current, next);
      await login(user.email, next);
    },
    onSuccess: () => {
      setCurrent('');
      setNext('');
    },
  });
  const errors = fieldErrors(change.error);
  const message = formError(change.error);

  return (
    <Card>
      <CardHeader title="Password" description="Changing your password signs you out on every other device." />
      <form
        className={styles.form}
        onSubmit={(event: FormEvent) => {
          event.preventDefault();
          change.mutate();
        }}
        noValidate
      >
        {message && <Notice tone="error">{message}</Notice>}
        {change.isSuccess && <Notice tone="success">Password changed.</Notice>}
        <FieldRow>
          <Field label="Current password" error={errors.currentPassword}>
            {(props) => (
              <Input
                {...props}
                type="password"
                autoComplete="current-password"
                value={current}
                onChange={(e) => setCurrent(e.target.value)}
              />
            )}
          </Field>
          <Field
            label="New password"
            error={errors.newPassword}
            hint="At least 8 characters, with a letter and a number."
          >
            {(props) => (
              <Input
                {...props}
                type="password"
                autoComplete="new-password"
                value={next}
                onChange={(e) => setNext(e.target.value)}
              />
            )}
          </Field>
        </FieldRow>
        <div className={styles.actions}>
          <Button type="submit" disabled={!current || !next} loading={change.isPending}>
            Change password
          </Button>
        </div>
      </form>
    </Card>
  );
}

function NotificationsSection() {
  const queryClient = useQueryClient();
  const settings = useQuery({ queryKey: ['notification-settings'], queryFn: usersApi.notificationSettings });
  const update = useMutation({
    mutationFn: (enabled: boolean) => usersApi.updateNotificationSettings(enabled),
    onSuccess: (data) => queryClient.setQueryData(['notification-settings'], data),
  });
  const enabled = update.isPending ? update.variables : settings.data?.emailMessageNotifications;

  return (
    <Card>
      <CardHeader title="Notifications" />
      {settings.isPending ? (
        <LoadingState />
      ) : (
        <label className={styles.toggle}>
          <input
            type="checkbox"
            checked={enabled ?? true}
            disabled={update.isPending}
            onChange={(e) => update.mutate(e.target.checked)}
          />
          <span>
            <span className={styles.toggleTitle}>Email me about unread messages</span>
            <span className={styles.toggleText}>
              We’ll send one email per conversation if a message stays unread for a few minutes.
            </span>
          </span>
        </label>
      )}
      {update.error && <Notice tone="error">{errorMessage(update.error)}</Notice>}
    </Card>
  );
}

function BlockedSection() {
  const queryClient = useQueryClient();
  const blocked = useQuery({ queryKey: ['blocks'], queryFn: blocksApi.list });
  const unblock = useMutation({
    mutationFn: (userId: string) => blocksApi.unblock(userId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['blocks'] }),
  });

  return (
    <Card>
      <CardHeader
        title="Blocked people"
        description="Blocked people can’t message you or send you offers, and you can’t contact them."
      />
      {blocked.isPending ? (
        <LoadingState />
      ) : blocked.data && blocked.data.length > 0 ? (
        <ul className={styles.blockList}>
          {blocked.data.map(({ user, blockedAt }) => (
            <li key={user.id} className={styles.blockItem}>
              <Avatar name={user.fullName} size={30} />
              <div className={styles.blockWho}>
                <p>{user.fullName}</p>
                <p className="subtle">Blocked {formatRelative(blockedAt)}</p>
              </div>
              <Button
                size="sm"
                loading={unblock.isPending && unblock.variables === user.id}
                onClick={() => user.id && unblock.mutate(user.id)}
              >
                Unblock
              </Button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="muted">You haven’t blocked anyone.</p>
      )}
    </Card>
  );
}

function SessionsSection() {
  const user = useCurrentUser();
  const { logout } = useAuth();
  const navigate = useNavigate();
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [confirmation, setConfirmation] = useState('');
  const logoutAll = useMutation({
    mutationFn: async () => {
      await authApi.logoutAll();
      session.clear();
    },
    onSuccess: () => navigate('/login'),
  });
  const remove = useMutation({
    mutationFn: async () => {
      await usersApi.remove(user.id);
      await logout();
    },
    onSuccess: () => navigate('/signup'),
  });

  return (
    <Card>
      <CardHeader title="Account" />
      <div className={styles.accountRows}>
        <div className={styles.accountRow}>
          <div>
            <p className={styles.toggleTitle}>Sign out everywhere</p>
            <p className={styles.toggleText}>Ends your sessions on all browsers and devices, including this one.</p>
          </div>
          <Button loading={logoutAll.isPending} onClick={() => logoutAll.mutate()}>
            Sign out everywhere
          </Button>
        </div>
        <div className={styles.accountRow}>
          <div>
            <p className={styles.toggleTitle}>Delete account</p>
            <p className={styles.toggleText}>
              Removes your profile, postings and offers. Your messages stay visible to others as “Deleted user”.
            </p>
          </div>
          <Button variant="danger" onClick={() => setConfirmDelete(true)}>
            Delete account
          </Button>
        </div>
      </div>
      {logoutAll.error && <Notice tone="error">{errorMessage(logoutAll.error)}</Notice>}
      <Dialog
        open={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        title="Delete your account?"
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmDelete(false)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              disabled={confirmation !== user.email}
              loading={remove.isPending}
              onClick={() => remove.mutate()}
            >
              Delete account
            </Button>
          </>
        }
      >
        <p>This can’t be undone. Type your email address to confirm.</p>
        <Field label="Email">
          {(props) => (
            <Input
              {...props}
              value={confirmation}
              placeholder={user.email}
              onChange={(e) => setConfirmation(e.target.value)}
            />
          )}
        </Field>
        {remove.error && <Notice tone="error">{errorMessage(remove.error)}</Notice>}
      </Dialog>
    </Card>
  );
}
