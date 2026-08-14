import type { QueryClient } from '@tanstack/react-query';
import { FAVORITES_QUERY_KEY } from '../../favorites/api/favoritesApi';
import { CURRENT_USER_QUERY_KEY, isConfirmedUnauthorized } from './currentUser';

export function clearSessionScopedQueries(queryClient: QueryClient) {
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

  queryClient.removeQueries({ queryKey: FAVORITES_QUERY_KEY });
  void queryClient.resetQueries({
    queryKey: CURRENT_USER_QUERY_KEY,
    exact: true,
  });
  return true;
}
