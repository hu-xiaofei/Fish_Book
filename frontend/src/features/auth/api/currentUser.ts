import { ApiError } from '../../../shared/api/ApiError';
import { apiFetch } from '../../../shared/api/httpClient';
import type { User } from '../../../shared/api/types';

export const CURRENT_USER_QUERY_KEY = ['current-user'] as const;

export function isConfirmedUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401;
}

export function hasUsableCurrentUser(
  user: User | undefined,
  error: unknown,
): user is User {
  return user !== undefined && !isConfirmedUnauthorized(error);
}

export const currentUserQueryConfig = {
  queryKey: CURRENT_USER_QUERY_KEY,
  staleTime: 5 * 60 * 1000,
  refetchOnMount: (query: { state: { error: unknown } }) => (
    !isConfirmedUnauthorized(query.state.error)
  ),
  retry: (failureCount: number, error: Error) => {
    if (isConfirmedUnauthorized(error)) return false;
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
