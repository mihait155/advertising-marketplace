import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { StarInput, Stars } from './Stars';

describe('Stars', () => {
  it('announces the rating to assistive technology', () => {
    render(<Stars value={4.25} />);
    expect(screen.getByRole('img', { name: '4.3 out of 5' })).toBeInTheDocument();
  });
});

describe('StarInput', () => {
  it('reports the chosen rating and marks it checked', () => {
    const onChange = vi.fn();
    render(<StarInput value={3} onChange={onChange} />);
    expect(screen.getByRole('radio', { name: '3 stars' })).toHaveAttribute('aria-checked', 'true');
    fireEvent.click(screen.getByRole('radio', { name: '5 stars' }));
    expect(onChange).toHaveBeenCalledWith(5);
  });
});
