import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import type { Posting } from '../api/types';
import { PostingList } from './PostingList';

const posting = (overrides: Partial<Posting>): Posting => ({
  id: crypto.randomUUID(),
  category: 'CUSTOMER_POSTING',
  status: 'OPEN',
  title: 'Gaming streamers for a launch',
  text: 'We need **streamers**.',
  payFrom: 1500,
  payTo: 4000,
  payCurrency: 'EUR',
  creator: { id: 'c1', fullName: 'Northwind Games', userType: 'CUSTOMER' },
  images: [],
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
  ...overrides,
});

describe('PostingList', () => {
  it('shows title, creator, pay and a plain-text excerpt', () => {
    render(
      <MemoryRouter>
        <PostingList postings={[posting({})]} />
      </MemoryRouter>,
    );

    expect(screen.getByRole('link', { name: 'Gaming streamers for a launch' })).toHaveAttribute(
      'href',
      expect.stringMatching(/^\/postings\//),
    );
    expect(screen.getByText(/Northwind Games/)).toHaveTextContent('Northwind Games · Advertiser');
    expect(screen.getByText('€1,500 – €4,000')).toBeInTheDocument();
    expect(screen.getByText('We need streamers.')).toBeInTheDocument();
  });

  it('says when pay is not listed and flags non-open postings', () => {
    render(
      <MemoryRouter>
        <PostingList
          postings={[posting({ payFrom: null, payTo: null, payCurrency: null, status: 'CLOSED' })]}
          showStatus="notOpen"
        />
      </MemoryRouter>,
    );

    expect(screen.getByText('Pay not listed')).toBeInTheDocument();
    expect(screen.getByText('Closed')).toBeInTheDocument();
  });
});
