import { useQuery } from '@tanstack/react-query';
import { Suspense, useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router';
import { conversationsApi } from '../api/endpoints';
import { useCurrentUser, useAuth } from '../auth/AuthContext';
import { userTypeLabel } from '../lib/format';
import { Avatar } from './Avatar';
import { LoadingState } from './EmptyState';
import { LinkButton } from './Button';
import { PlusIcon } from './Icons';
import styles from './AppLayout.module.css';

function UserMenu() {
  const user = useCurrentUser();
  const { logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    const close = (event: MouseEvent) => {
      if (!ref.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    const escape = (event: KeyboardEvent) => event.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', escape);
    return () => {
      document.removeEventListener('mousedown', close);
      document.removeEventListener('keydown', escape);
    };
  }, [open]);

  return (
    <div className={styles.userMenu} ref={ref}>
      <button
        type="button"
        className={styles.userButton}
        aria-expanded={open}
        aria-haspopup="menu"
        onClick={() => setOpen((v) => !v)}
      >
        <Avatar name={user.fullName} size={28} />
        <span className={styles.userName}>{user.fullName}</span>
      </button>
      {open && (
        <div className={styles.menu} role="menu">
          <div className={styles.menuHeader}>
            <p className={styles.menuName}>{user.fullName}</p>
            <p className={styles.menuMeta}>
              {userTypeLabel[user.userType]} · {user.email}
            </p>
          </div>
          <Link to={`/users/${user.id}`} role="menuitem" className={styles.menuItem} onClick={() => setOpen(false)}>
            Public profile
          </Link>
          <Link to="/settings" role="menuitem" className={styles.menuItem} onClick={() => setOpen(false)}>
            Settings
          </Link>
          <button
            type="button"
            role="menuitem"
            className={styles.menuItem}
            onClick={async () => {
              setOpen(false);
              await logout();
              navigate('/login');
            }}
          >
            Log out
          </button>
        </div>
      )}
    </div>
  );
}

export function AppLayout() {
  const user = useCurrentUser();
  const unread = useQuery({ queryKey: ['unread'], queryFn: conversationsApi.unreadCount, refetchInterval: 60_000 });
  const unreadCount = unread.data?.unreadMessages ?? 0;
  const navClass = ({ isActive }: { isActive: boolean }) => `${styles.navLink} ${isActive ? styles.active : ''}`;

  return (
    <div className={styles.shell}>
      <header className={styles.topbar}>
        <div className={styles.topbarInner}>
          <Link to="/postings" className={styles.brand}>
            <span className={styles.logo} aria-hidden>
              <svg viewBox="0 0 32 32" width="22" height="22">
                <rect width="32" height="32" rx="7" fill="currentColor" />
                <path
                  d="M9 22V10l7 7 7-7v12"
                  fill="none"
                  stroke="#fff"
                  strokeWidth="2.6"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            </span>
            Advertising Marketplace
          </Link>
          <nav className={styles.nav} aria-label="Main">
            <NavLink to="/postings" end className={navClass}>
              Browse
            </NavLink>
            <NavLink to="/my/postings" className={navClass}>
              My postings
            </NavLink>
            {user.userType !== 'ADMIN' && (
              <NavLink to="/my/offers" className={navClass}>
                My offers
              </NavLink>
            )}
            <NavLink to="/deals" className={navClass}>
              Deals
            </NavLink>
            <NavLink to="/messages" className={navClass}>
              Messages
              {unreadCount > 0 && (
                <span className={styles.unread} aria-label={`${unreadCount} unread`}>
                  {unreadCount > 99 ? '99+' : unreadCount}
                </span>
              )}
            </NavLink>
            {user.userType === 'ADMIN' && (
              <NavLink to="/admin/reports" className={navClass}>
                Reports
              </NavLink>
            )}
          </nav>
          <div className={styles.actions}>
            <LinkButton to="/postings/new" variant="primary" size="sm" icon={<PlusIcon />} aria-label="New posting">
              <span className={styles.newLabel}>New posting</span>
            </LinkButton>
            <UserMenu />
          </div>
        </div>
      </header>
      <main className={styles.main}>
        <Suspense fallback={<LoadingState />}>
          <Outlet />
        </Suspense>
      </main>
    </div>
  );
}

export function AuthLayout() {
  return (
    <div className={styles.authShell}>
      <Link to="/" className={`${styles.brand} ${styles.authBrand}`}>
        <span className={styles.logo} aria-hidden>
          <svg viewBox="0 0 32 32" width="22" height="22">
            <rect width="32" height="32" rx="7" fill="currentColor" />
            <path
              d="M9 22V10l7 7 7-7v12"
              fill="none"
              stroke="#fff"
              strokeWidth="2.6"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </span>
        Advertising Marketplace
      </Link>
      <Outlet />
    </div>
  );
}
