import { LinkButton } from '../components/Button';
import { EmptyState } from '../components/EmptyState';

export function NotFoundPage() {
  return (
    <EmptyState
      title="Page not found"
      description="The page you’re looking for doesn’t exist or you don’t have access to it."
      action={<LinkButton to="/postings">Back to browsing</LinkButton>}
    />
  );
}
