import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, refreshSession, session } from './client';

const auth = (accessToken: string, refreshToken: string) => ({
  tokenType: 'Bearer',
  accessToken,
  accessTokenExpiresAt: '2026-11-05T12:15:00Z',
  refreshToken,
  refreshTokenExpiresAt: '2026-12-05T12:00:00Z',
  user: {
    id: 'u1',
    fullName: 'Maya Chen',
    email: 'maya@x.test',
    phone: null,
    userType: 'MARKETER',
    createdAt: '',
    updatedAt: '',
  },
});

function json(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('api client', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    session.set(auth('access-1', 'refresh-1') as never);
  });

  afterEach(() => {
    session.clear();
    fetchMock.mockReset();
    vi.unstubAllGlobals();
  });

  it('sends the access token and parses JSON', async () => {
    fetchMock.mockResolvedValueOnce(json(200, { ok: true }));

    await expect(api('/things', { query: { a: 1, b: undefined, c: '' } })).resolves.toEqual({ ok: true });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/things?a=1');
    expect((init!.headers as Record<string, string>).Authorization).toBe('Bearer access-1');
  });

  it('turns problem details into ApiError with field errors', async () => {
    fetchMock.mockResolvedValueOnce(
      json(400, { status: 400, detail: 'Validation failed', errors: { email: 'must be valid' } }),
    );

    const error = (await api('/things', { method: 'POST', body: {} }).catch((e) => e)) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(400);
    expect(error.message).toBe('Validation failed');
    expect(error.fieldErrors).toEqual({ email: 'must be valid' });
  });

  it('refreshes once on 401 and retries the request', async () => {
    fetchMock
      .mockResolvedValueOnce(json(401, { status: 401 }))
      .mockResolvedValueOnce(json(200, auth('access-2', 'refresh-2')))
      .mockResolvedValueOnce(json(200, { ok: true }));

    await expect(api('/things')).resolves.toEqual({ ok: true });

    expect(fetchMock).toHaveBeenCalledTimes(3);
    const [refreshUrl, refreshInit] = fetchMock.mock.calls[1];
    expect(refreshUrl).toBe('/api/v1/auth/refresh');
    expect(refreshInit!.body).toBeUndefined();
    expect(refreshInit!.credentials).toBe('same-origin');
    expect(refreshInit!.headers).toEqual({ 'X-Requested-With': 'fetch' });
    expect((fetchMock.mock.calls[2][1]!.headers as Record<string, string>).Authorization).toBe('Bearer access-2');
    expect(JSON.stringify(localStorage)).not.toContain('refresh');
  });

  it('shares one refresh between concurrent callers', async () => {
    let resolve!: (response: Response) => void;
    fetchMock.mockReturnValueOnce(new Promise((r) => (resolve = r)));

    const first = refreshSession();
    const second = refreshSession();
    resolve(json(200, auth('access-3', 'refresh-3')));

    expect(await first).toEqual(await second);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('clears the session when the refresh token is rejected', async () => {
    const listener = vi.fn();
    session.subscribe(listener);
    fetchMock.mockResolvedValueOnce(json(401, { status: 401 })).mockResolvedValueOnce(json(401, { status: 401 }));

    await expect(api('/things')).rejects.toBeInstanceOf(ApiError);

    expect(session.accessToken).toBeNull();
    expect(session.mayHaveSession).toBe(false);
    expect(listener).toHaveBeenLastCalledWith(null);
  });

  it('does not send credentials to public endpoints', async () => {
    fetchMock.mockResolvedValueOnce(json(204));

    await api('/auth/logout', { method: 'POST', body: { refreshToken: 'x' }, auth: false });

    expect((fetchMock.mock.calls[0][1]!.headers as Record<string, string>).Authorization).toBeUndefined();
  });
});
