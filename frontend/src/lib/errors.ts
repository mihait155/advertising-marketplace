import { ApiError } from '../api/client';

export function fieldErrors(error: unknown): Record<string, string> {
  return error instanceof ApiError ? error.fieldErrors : {};
}

export function formError(error: unknown) {
  if (!error) {
    return null;
  }
  if (error instanceof ApiError) {
    if (Object.keys(error.fieldErrors).length > 0 && error.status === 400) {
      return null;
    }
    return error.message;
  }
  return 'Something went wrong. Check your connection and try again.';
}
