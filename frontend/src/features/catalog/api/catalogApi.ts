import { apiFetch } from '../../../shared/api/httpClient';
import { toCatalogSearchParams } from '../model/catalogSearchParams';
import type {
  CatalogFilters,
  FishDetail,
  FishFilterOptions,
  FishPage,
  FishSummary,
} from '../model/types';

export const FISH_CATALOG_QUERY_KEY = ['fish-catalog'] as const;

export const fishListQueryKey = (filters: CatalogFilters) => [
  ...FISH_CATALOG_QUERY_KEY, 'list', filters.q, filters.family, filters.habitat, filters.page,
] as const;

export const fishOptionsQueryKey = [...FISH_CATALOG_QUERY_KEY, 'options'] as const;

export async function fetchFishPage(filters: CatalogFilters): Promise<FishPage> {
  const params = toCatalogSearchParams(filters);
  return apiFetch<FishPage>(`/api/v1/fish${params.size ? `?${params}` : ''}`);
}

export const fishDetailQueryKey = (slug: string) =>
  [...FISH_CATALOG_QUERY_KEY, 'detail', slug] as const;

export const fishFilterOptionsQueryKey = [...FISH_CATALOG_QUERY_KEY, 'filters'] as const;

export function fetchFishDetail(slug: string): Promise<FishDetail> {
  return apiFetch<FishDetail>(`/api/v1/fish/${encodeURIComponent(slug)}`);
}

export function fetchFishFilterOptions(): Promise<FishFilterOptions> {
  return apiFetch<FishFilterOptions>('/api/v1/fish/filters');
}

export async function fetchAllPublishedFishOptions(): Promise<FishSummary[]> {
  const first = await fetchFishPage({ q: '', family: '', habitat: '', page: 0 });
  const remaining = await Promise.all(
    Array.from({ length: Math.max(0, first.totalPages - 1) }, (_, index) => (
      fetchFishPage({ q: '', family: '', habitat: '', page: index + 1 })
    )),
  );
  return [first, ...remaining].flatMap((page) => page.items);
}
