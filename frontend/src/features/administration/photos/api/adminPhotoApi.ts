import type { AdminPhotoFilters, AdminPhotoOperation, AdminPhotoPage, AdminPhotoSummary } from '../model/types';
import { apiFetch } from '../../../../shared/api/httpClient';
export const ADMIN_PHOTOS_QUERY_KEY = ['admin-photos'] as const;
export const adminPhotoPageQueryKey = (filters: AdminPhotoFilters) => [...ADMIN_PHOTOS_QUERY_KEY, 'page', filters.userId, filters.page, filters.size] as const;
export const adminPhotoDetailQueryKey = (recordId: number) => [...ADMIN_PHOTOS_QUERY_KEY, 'detail', recordId] as const;
export const adminPhotoOperationsQueryKey = (recordId: number, page: number) => [...ADMIN_PHOTOS_QUERY_KEY, 'operations', recordId, page] as const;
export function fetchAdminPhotoPage(filters: AdminPhotoFilters) {
  const params = new URLSearchParams();
  if (filters.userId) params.set('userId', filters.userId);
  params.set('page', String(filters.page)); params.set('size', String(filters.size));
  return apiFetch<AdminPhotoPage>(`/api/v1/admin/photos?${params}`);
}
export const fetchAdminPhoto = (recordId: number) => apiFetch<AdminPhotoSummary>(`/api/v1/admin/photos/${recordId}`);
export const fetchAdminPhotoOperations = (recordId: number, page: number) => apiFetch<AdminPhotoPage<AdminPhotoOperation>>(`/api/v1/admin/photos/${recordId}/operations?page=${page}&size=20`);
export const adminPhotoContentUrl = (recordId: number) => `/api/v1/admin/photos/${recordId}/content`;
export function replaceAdminPhoto(recordId: number, file: File, revision: string) {
  const form = new FormData(); form.append('photo', file);
  return apiFetch<void>(adminPhotoContentUrl(recordId), { method: 'PUT', body: form, headers: { 'If-Match': `"${revision}"` } });
}
export const removeAdminPhoto = (recordId: number, revision: string) => apiFetch<void>(adminPhotoContentUrl(recordId), { method: 'DELETE', headers: { 'If-Match': `"${revision}"` } });
