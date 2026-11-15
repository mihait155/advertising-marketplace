import { useMutation } from '@tanstack/react-query';
import { useRef } from 'react';
import { ApiError } from '../api/client';
import { imagesApi } from '../api/endpoints';
import type { Image } from '../api/types';
import { Button } from './Button';
import { ImageIcon } from './Icons';

const ACCEPT = 'image/jpeg,image/png,image/gif,image/webp';
const MAX_BYTES = 10 * 1024 * 1024;

export function uploadErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 413:
        return 'That image is larger than 10 MB.';
      case 415:
        return 'Only JPEG, PNG, GIF and WebP images can be uploaded.';
      case 422:
        return 'That file was rejected by the virus scanner.';
      case 503:
        return 'Uploads are temporarily unavailable. Try again in a minute.';
      default:
        return error.message;
    }
  }
  return error instanceof Error ? error.message : 'Upload failed.';
}

export function ImageUploadButton({
  onUploaded,
  onError,
  label = 'Add image',
  size = 'sm',
  variant = 'ghost',
  iconOnly,
}: {
  onUploaded: (image: Image) => void;
  onError: (message: string | null) => void;
  label?: string;
  size?: 'sm' | 'md';
  variant?: 'ghost' | 'secondary';
  iconOnly?: boolean;
}) {
  const input = useRef<HTMLInputElement>(null);
  const upload = useMutation({
    mutationFn: (file: File) => imagesApi.upload(file),
    onSuccess: (image) => {
      onError(null);
      onUploaded(image);
    },
    onError: (error) => onError(uploadErrorMessage(error)),
  });

  return (
    <>
      <input
        ref={input}
        type="file"
        accept={ACCEPT}
        hidden
        onChange={(event) => {
          const file = event.target.files?.[0];
          event.target.value = '';
          if (!file) {
            return;
          }
          if (file.size > MAX_BYTES) {
            onError('That image is larger than 10 MB.');
            return;
          }
          upload.mutate(file);
        }}
      />
      <Button
        size={size}
        variant={variant}
        icon={<ImageIcon />}
        loading={upload.isPending}
        onClick={() => input.current?.click()}
        aria-label={iconOnly ? label : undefined}
        title={iconOnly ? label : undefined}
      >
        {iconOnly ? null : upload.isPending ? 'Scanning…' : label}
      </Button>
    </>
  );
}
