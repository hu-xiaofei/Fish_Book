import type { QueryClient } from '@tanstack/react-query';
import { ADMIN_FISHES_QUERY_KEY } from '../../administration/api/adminFishApi';
import { CATCHES_QUERY_KEY } from '../../catchlog/api/catchRecordsApi';
import { FAVORITES_QUERY_KEY } from '../../favorites/api/favoritesApi';
import { CURRENT_USER_QUERY_KEY, isConfirmedUnauthorized } from './currentUser';
import { ADMIN_PHOTOS_QUERY_KEY } from '../../administration/photos/api/adminPhotoApi';

let sessionGeneration = 0;
const generationListeners = new Set<() => void>();

export function subscribeSessionGeneration(listener: () => void) {
  generationListeners.add(listener);
  return () => { generationListeners.delete(listener); };
}

export function captureSessionGeneration(): number {
  return sessionGeneration;
}

export function isCurrentSessionGeneration(generation: number): boolean {
  return generation === sessionGeneration;
}

export function clearSessionScopedQueries(queryClient: QueryClient) {
  sessionGeneration += 1;
  queryClient.removeQueries({ queryKey: ADMIN_FISHES_QUERY_KEY });
  queryClient.removeQueries({ queryKey: ADMIN_PHOTOS_QUERY_KEY });
  queryClient.removeQueries({ queryKey: CATCHES_QUERY_KEY });
  queryClient.removeQueries({ queryKey: FAVORITES_QUERY_KEY });
  queryClient.removeQueries({
    queryKey: CURRENT_USER_QUERY_KEY,
    exact: true,
  });
  generationListeners.forEach((listener) => listener());
}

export function expireSessionOnUnauthorized(
  queryClient: QueryClient,
  error: unknown,
): boolean {
  if (!isConfirmedUnauthorized(error)) return false;

  clearSessionScopedQueries(queryClient);
  return true;
}
