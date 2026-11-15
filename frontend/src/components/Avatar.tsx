import { initials } from '../lib/format';
import styles from './Avatar.module.css';

const PALETTE = ['#dfe9e3', '#e6e2d6', '#dde4ee', '#ece0de', '#e4e0ec', '#dcebea'];
const INK = ['#2f5d48', '#5b4f2c', '#34506f', '#6b3d36', '#4c4268', '#2f5f5b'];

function hash(value: string) {
  let h = 0;
  for (let i = 0; i < value.length; i++) {
    h = (h * 31 + value.charCodeAt(i)) | 0;
  }
  return Math.abs(h);
}

export function Avatar({ name, size = 32 }: { name: string; size?: number }) {
  const index = hash(name) % PALETTE.length;
  return (
    <span
      className={styles.avatar}
      style={{
        width: size,
        height: size,
        fontSize: Math.round(size * 0.38),
        background: PALETTE[index],
        color: INK[index],
      }}
      aria-hidden
    >
      {initials(name)}
    </span>
  );
}
