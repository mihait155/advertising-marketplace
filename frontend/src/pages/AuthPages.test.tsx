import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { session } from '../api/client';
import { renderWithProviders } from '../test-utils';
import { LoginPage } from './LoginPage';
import { SignupPage } from './SignupPage';

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const authResponse = {
  tokenType: 'Bearer',
  accessToken: 'a',
  accessTokenExpiresAt: '',
  refreshToken: 'r',
  refreshTokenExpiresAt: '',
  user: {
    id: 'u1',
    fullName: 'Maya Chen',
    email: 'maya@x.test',
    phone: null,
    userType: 'MARKETER',
    createdAt: '',
    updatedAt: '',
  },
};

describe('auth pages', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => vi.stubGlobal('fetch', fetchMock));
  afterEach(() => {
    session.clear();
    fetchMock.mockReset();
    vi.unstubAllGlobals();
  });

  it('shows the server error for bad credentials', async () => {
    fetchMock.mockResolvedValueOnce(json(401, { status: 401, detail: 'Invalid email or password' }));
    renderWithProviders(<LoginPage />, { route: '/login', path: '/login' });

    await userEvent.type(screen.getByLabelText('Email'), 'maya@x.test');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-passw0rd');
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password');
  });

  it('logs in and goes to postings', async () => {
    fetchMock.mockResolvedValueOnce(json(200, authResponse));
    renderWithProviders(<LoginPage />, { route: '/login', path: '/login' });

    await userEvent.type(screen.getByLabelText('Email'), 'maya@x.test');
    await userEvent.type(screen.getByLabelText('Password'), 'Sup3r-secret');
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));

    expect(await screen.findByText('Postings page')).toBeInTheDocument();
    expect(localStorage.getItem('am.session')).toBe('1');
    expect(JSON.stringify(localStorage)).not.toContain('"r"');
  });

  it('signs up with the chosen account type and shows field errors', async () => {
    fetchMock.mockResolvedValueOnce(
      json(400, { status: 400, detail: 'Validation failed', errors: { password: 'must be 8-64 characters' } }),
    );
    renderWithProviders(<SignupPage />, { route: '/signup', path: '/signup' });

    await userEvent.click(screen.getByRole('radio', { name: /I want to advertise/ }));
    await userEvent.type(screen.getByLabelText('Full name'), 'Acme');
    await userEvent.type(screen.getByLabelText('Email'), 'ads@acme.test');
    await userEvent.type(screen.getByLabelText('Password'), 'short');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('must be 8-64 characters')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(JSON.parse(fetchMock.mock.calls[0][1]!.body as string)).toMatchObject({
      userType: 'CUSTOMER',
      fullName: 'Acme',
    });
  });
});
