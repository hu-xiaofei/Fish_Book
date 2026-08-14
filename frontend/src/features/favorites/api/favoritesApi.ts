import { apiFetch } from '../../../shared/api/httpClient';
import type { FavoriteStatusResponse } from '../model/types';

export const FAVORITES_QUERY_KEY = ['favorites'] as const;

export function favoriteStatusQueryKey(slugs: readonly string[]) {
  return [...FAVORITES_QUERY_KEY, 'status', ...[...slugs].sort()] as const;
}

export function fetchFavoriteStatuses(
  slugs: readonly string[],
): Promise<FavoriteStatusResponse> {
  const searchParams = new URLSearchParams();
  slugs.forEach((slug) => searchParams.append('fishSlug', slug));

  return apiFetch<FavoriteStatusResponse>(
    `/api/v1/favorites/status?${searchParams.toString()}`,
  );
}

export function addFavorite(fishSlug: string): Promise<void> {
  return apiFetch<void>(`/api/v1/favorites/${encodeURIComponent(fishSlug)}`, {
    method: 'PUT',
  });
}

export function removeFavorite(fishSlug: string): Promise<void> {
  return apiFetch<void>(`/api/v1/favorites/${encodeURIComponent(fishSlug)}`, {
    method: 'DELETE',
  });
}
