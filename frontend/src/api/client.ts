import type { AuthResponse } from './types';

export interface ProblemDetail {
  status: number;
  title?: string;
  detail?: string;
  errors?: Record<string, string>;
}

export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, problem: Partial<ProblemDetail>) {
    super(problem.detail || problem.title || `Request failed (${status})`);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = problem.errors ?? {};
  }
}

const SESSION_FLAG_KEY = 'am.session';
const BASE = '/api/v1';
const CSRF_HEADERS = { 'X-Requested-With': 'fetch' };

type SessionListener = (session: AuthResponse | null) => void;

let accessToken: string | null = null;
let refreshInFlight: Promise<AuthResponse | null> | null = null;
const listeners = new Set<SessionListener>();

function readFlag(): boolean {
  try {
    return localStorage.getItem(SESSION_FLAG_KEY) === '1';
  } catch {
    return false;
  }
}

function writeFlag(active: boolean) {
  try {
    if (active) {
      localStorage.setItem(SESSION_FLAG_KEY, '1');
    } else {
      localStorage.removeItem(SESSION_FLAG_KEY);
    }
  } catch {
    return;
  }
}

export const session = {
  get accessToken() {
    return accessToken;
  },
  get mayHaveSession() {
    return readFlag();
  },
  set(auth: AuthResponse) {
    accessToken = auth.accessToken;
    writeFlag(true);
    listeners.forEach((listener) => listener(auth));
  },
  clear() {
    accessToken = null;
    writeFlag(false);
    listeners.forEach((listener) => listener(null));
  },
  subscribe(listener: SessionListener) {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};

export function refreshSession(): Promise<AuthResponse | null> {
  if (refreshInFlight) {
    return refreshInFlight;
  }
  if (!readFlag()) {
    return Promise.resolve(null);
  }
  refreshInFlight = fetch(`${BASE}/auth/refresh`, {
    method: 'POST',
    credentials: 'same-origin',
    headers: CSRF_HEADERS,
  })
    .then(async (response) => {
      if (!response.ok) {
        if (response.status === 401 || response.status === 403) {
          session.clear();
        }
        return null;
      }
      const auth = (await response.json()) as AuthResponse;
      session.set(auth);
      return auth;
    })
    .catch(() => null)
    .finally(() => {
      refreshInFlight = null;
    });
  return refreshInFlight;
}

export async function endServerSession() {
  await fetch(`${BASE}/auth/logout`, { method: 'POST', credentials: 'same-origin', headers: CSRF_HEADERS }).catch(
    () => undefined,
  );
}

type Query = Record<string, string | number | boolean | null | undefined>;

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  query?: Query;
  auth?: boolean;
  signal?: AbortSignal;
}

function buildUrl(path: string, query?: Query) {
  const url = `${BASE}${path}`;
  if (!query) {
    return url;
  }
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') {
      params.set(key, String(value));
    }
  }
  const qs = params.toString();
  return qs ? `${url}?${qs}` : url;
}

async function send(path: string, options: RequestOptions): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  let body: BodyInit | undefined;
  if (options.body instanceof FormData) {
    body = options.body;
  } else if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(options.body);
  }
  if (options.auth !== false && accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  return fetch(buildUrl(path, options.query), {
    method: options.method ?? 'GET',
    headers,
    body,
    signal: options.signal,
  });
}

async function parse<T>(response: Response): Promise<T> {
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  const data = text ? JSON.parse(text) : undefined;
  if (!response.ok) {
    throw new ApiError(response.status, data ?? {});
  }
  return data as T;
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  if (options.auth !== false && !accessToken && session.mayHaveSession) {
    await refreshSession();
  }
  let response = await send(path, options);
  if (response.status === 401 && options.auth !== false && session.mayHaveSession) {
    const refreshed = await refreshSession();
    if (refreshed) {
      response = await send(path, options);
    }
  }
  return parse<T>(response);
}
