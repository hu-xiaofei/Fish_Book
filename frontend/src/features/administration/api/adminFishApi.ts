import { apiFetch } from '../../../shared/api/httpClient';
import { toAdminFishSearchParams } from '../model/adminFishSearchParams';
import type {
  AdminFishCreateInput,
  AdminFishDetail,
  AdminFishFilters,
  AdminFishPage,
  AdminFishUpdateInput,
} from '../model/types';

export const ADMIN_FISHES_QUERY_KEY = ['admin-fishes'] as const;

export const adminFishPageQueryKey = (filters: AdminFishFilters) => [
  ...ADMIN_FISHES_QUERY_KEY, 'list', filters.q, filters.status, filters.page,
] as const;

export const adminFishDetailQueryKey = (id: number) => [
  ...ADMIN_FISHES_QUERY_KEY, 'detail', id,
] as const;

export function fetchAdminFishPage(filters: AdminFishFilters): Promise<AdminFishPage> {
  const params = toAdminFishSearchParams(filters);
  return apiFetch<AdminFishPage>(`/api/v1/admin/fishes${params.size ? `?${params}` : ''}`);
}

export const fetchAdminFish = (id: number) =>
  apiFetch<AdminFishDetail>(`/api/v1/admin/fishes/${id}`);

export const createAdminFish = (input: AdminFishCreateInput) =>
  apiFetch<AdminFishDetail>('/api/v1/admin/fishes', {
    method: 'POST',
    body: JSON.stringify(input),
  });

export const updateAdminFish = (id: number, input: AdminFishUpdateInput) =>
  apiFetch<AdminFishDetail>(`/api/v1/admin/fishes/${id}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  });

export const publishAdminFish = (id: number) =>
  apiFetch<AdminFishDetail>(`/api/v1/admin/fishes/${id}/publish`, { method: 'POST' });

export const unpublishAdminFish = (id: number) =>
  apiFetch<AdminFishDetail>(`/api/v1/admin/fishes/${id}/unpublish`, { method: 'POST' });
