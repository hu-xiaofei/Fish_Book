import { apiFetch } from '../../../shared/api/httpClient';
import { toCatalogSearchParams } from '../model/catalogSearchParams';
import type {
  CatalogFilters,
  FishDetail,
  FishFilterOptions,
  FishPage,
} from '../model/types';

export const fishListQueryKey = (filters: CatalogFilters) => [
  'fish-catalog', 'list', filters.q, filters.family, filters.habitat, filters.page,
] as const;

export async function fetchFishPage(filters: CatalogFilters): Promise<FishPage> {
  const params = toCatalogSearchParams(filters);
  return apiFetch<FishPage>(`/api/v1/fish${params.size ? `?${params}` : ''}`);
}

export const fishDetailQueryKey = (slug: string) =>
  ['fish-catalog', 'detail', slug] as const;

export const fishFilterOptionsQueryKey = ['fish-catalog', 'filters'] as const;

export function fetchFishDetail(slug: string): Promise<FishDetail> {
  return apiFetch<FishDetail>(`/api/v1/fish/${encodeURIComponent(slug)}`);
}

export function fetchFishFilterOptions(): Promise<FishFilterOptions> {
  return apiFetch<FishFilterOptions>('/api/v1/fish/filters');
}
