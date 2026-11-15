import { useId } from 'react';
import styles from './Stars.module.css';

function Star({ filled }: { filled: number }) {
  const id = useId();
  return (
    <svg viewBox="0 0 20 20" width="1em" height="1em" aria-hidden>
      <defs>
        <linearGradient id={id}>
          <stop offset={`${filled * 100}%`} stopColor="currentColor" />
          <stop offset={`${filled * 100}%`} stopColor="var(--border-strong)" />
        </linearGradient>
      </defs>
      <path
        fill={`url(#${id})`}
        d="M10 1.6l2.47 5.2 5.66.7-4.16 3.9 1.06 5.6L10 14.3 4.97 17l1.06-5.6L1.87 7.5l5.66-.7z"
      />
    </svg>
  );
}

export function Stars({ value, size = 14 }: { value: number; size?: number }) {
  return (
    <span className={styles.stars} style={{ fontSize: size }} role="img" aria-label={`${value.toFixed(1)} out of 5`}>
      {[0, 1, 2, 3, 4].map((i) => (
        <Star key={i} filled={Math.max(0, Math.min(1, value - i))} />
      ))}
    </span>
  );
}

export function StarInput({ value, onChange }: { value: number; onChange: (value: number) => void }) {
  return (
    <div className={styles.input} role="radiogroup" aria-label="Rating">
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          type="button"
          role="radio"
          aria-checked={value === n}
          aria-label={`${n} star${n === 1 ? '' : 's'}`}
          className={styles.option}
          onClick={() => onChange(n)}
        >
          <Star filled={n <= value ? 1 : 0} />
        </button>
      ))}
    </div>
  );
}
