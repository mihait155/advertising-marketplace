import { Link } from 'react-router';
import type { UserSummary } from '../api/types';

export function UserLink({ user, className }: { user: UserSummary; className?: string }) {
  if (!user.id) {
    return <span className={className}>{user.fullName}</span>;
  }
  return (
    <Link to={`/users/${user.id}`} className={className ? `user-link ${className}` : 'user-link'}>
      {user.fullName}
    </Link>
  );
}
