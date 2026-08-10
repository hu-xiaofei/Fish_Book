import { ApiError } from '../../../shared/api/ApiError';
import { apiFetch } from '../../../shared/api/httpClient';
import type { User } from '../../../shared/api/types';

export const CURRENT_USER_QUERY_KEY = ['current-user'] as const;

export const currentUserQueryConfig = {
  queryKey: CURRENT_USER_QUERY_KEY,
  staleTime: 5 * 60 * 1000,
  retry: (failureCount: number, error: Error) => {
    if (error instanceof ApiError && error.status === 401) return false;
    return failureCount < 2;
  },
};

export function fetchCurrentUser(): Promise<User> {
  return apiFetch<User>('/api/v1/me');
}

export function updateNickname(nickname: string): Promise<User> {
  return apiFetch<User>('/api/v1/me', {
    method: 'PATCH',
    body: JSON.stringify({ nickname }),
  });
}
