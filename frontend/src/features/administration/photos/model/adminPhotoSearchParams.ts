import type { AdminPhotoFilters } from './types';
export function parseAdminPhotoSearchParams(params: URLSearchParams): AdminPhotoFilters | undefined {
  for (const name of ['userId', 'page', 'size']) if (params.getAll(name).length > 1) return undefined;
  const userId = params.get('userId') ?? '';
  if (params.has('userId') && (!/^[1-9]\d*$/.test(userId) || BigInt(userId) > 9223372036854775807n)) return undefined;
  const pageValue = params.get('page') ?? '0';
  if (!/^\d+$/.test(pageValue) || Number(pageValue) > 2147483647) return undefined;
  if (params.has('size') && params.get('size') !== '20') return undefined;
  return { userId, page: Number(pageValue), size: 20 };
}
export function toAdminPhotoSearchParams(filters: AdminPhotoFilters) {
  const params = new URLSearchParams();
  if (filters.userId) params.set('userId', filters.userId);
  if (filters.page > 0) params.set('page', String(filters.page));
  return params;
}
