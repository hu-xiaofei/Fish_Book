import type { QueryClient } from '@tanstack/react-query';
import { CATCHES_QUERY_KEY } from '../../catchlog/api/catchRecordsApi';
import { FAVORITES_QUERY_KEY } from '../../favorites/api/favoritesApi';
import { CURRENT_USER_QUERY_KEY, isConfirmedUnauthorized } from './currentUser';

let sessionGeneration = 0;

export function captureSessionGeneration(): number {
  return sessionGeneration;
}

export function isCurrentSessionGeneration(generation: number): boolean {
  return generation === sessionGeneration;
}

export function clearSessionScopedQueries(queryClient: QueryClient) {
  sessionGeneration += 1;
  queryClient.removeQueries({ queryKey: CATCHES_QUERY_KEY });
  queryClient.removeQueries({ queryKey: FAVORITES_QUERY_KEY });
  queryClient.removeQueries({
    queryKey: CURRENT_USER_QUERY_KEY,
    exact: true,
  });
}

export function expireSessionOnUnauthorized(
  queryClient: QueryClient,
  error: unknown,
): boolean {
  if (!isConfirmedUnauthorized(error)) return false;

  clearSessionScopedQueries(queryClient);
  return true;
}
