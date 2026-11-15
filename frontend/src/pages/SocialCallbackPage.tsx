import { useMutation } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { socialApi } from '../api/endpoints';
import { LinkButton } from '../components/Button';
import { LoadingState } from '../components/EmptyState';
import { Notice, errorMessage } from '../components/Notice';

export function SocialCallbackPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const started = useRef(false);
  const code = params.get('code');
  const state = params.get('state');
  const providerError = params.get('error');
  const connect = useMutation({
    mutationFn: () => socialApi.connect(code!, state!),
    onSuccess: (account) => navigate(`/settings?connected=${account.provider}`, { replace: true }),
  });

  useEffect(() => {
    if (!started.current && code && state && !providerError) {
      started.current = true;
      connect.mutate();
    }
  }, [code, state, providerError, connect]);

  if (providerError || !code || !state || connect.isError) {
    return (
      <div style={{ maxWidth: 520, display: 'flex', flexDirection: 'column', gap: 16 }}>
        <Notice tone="error" title="The account wasn’t connected">
          {providerError === 'access_denied'
            ? 'You declined the permission request.'
            : connect.isError
              ? errorMessage(connect.error)
              : 'The provider didn’t return an authorization code.'}
        </Notice>
        <div>
          <LinkButton to="/settings">Back to settings</LinkButton>
        </div>
      </div>
    );
  }
  return <LoadingState label="Connecting your account…" />;
}
