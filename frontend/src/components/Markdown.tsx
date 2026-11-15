import ReactMarkdown, { defaultUrlTransform } from 'react-markdown';
import type { Image } from '../api/types';
import { imageIdFromRef, resolveImageSrc } from '../lib/markdown';
import styles from './Markdown.module.css';

function urlTransform(url: string) {
  return imageIdFromRef(url) ? url : defaultUrlTransform(url);
}

export function Markdown({ text, images = [] }: { text: string; images?: Image[] }) {
  return (
    <div className={styles.markdown}>
      <ReactMarkdown
        urlTransform={urlTransform}
        components={{
          img: ({ src, alt }) => {
            const url = resolveImageSrc(typeof src === 'string' ? src : undefined, images);
            if (!url) {
              return <span className={styles.missing}>Image unavailable</span>;
            }
            return (
              <a href={url} target="_blank" rel="noreferrer" className={styles.figure}>
                <img src={url} alt={alt ?? ''} loading="lazy" />
                {alt && <span className={styles.caption}>{alt}</span>}
              </a>
            );
          },
          a: ({ href, children }) => (
            <a href={href} target="_blank" rel="noreferrer nofollow">
              {children}
            </a>
          ),
        }}
      >
        {text}
      </ReactMarkdown>
    </div>
  );
}
