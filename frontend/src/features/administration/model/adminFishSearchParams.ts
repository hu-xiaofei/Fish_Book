import type { AdminFishFilters, PublicationStatus } from './types';

const publicationStatuses: readonly PublicationStatus[] = [
  'DRAFT',
  'PUBLISHED',
  'UNPUBLISHED',
];

function normalizeStatus(value: string | null): PublicationStatus | '' {
  const status = value?.trim().toUpperCase();
  return status && publicationStatuses.includes(status as PublicationStatus)
    ? status as PublicationStatus
    : '';
}

function normalizePage(value: string | number | null | undefined): number {
  if (typeof value === 'number') {
    return Number.isSafeInteger(value) && value >= 0 ? value : 0;
  }

  const decimalPage = value?.trim() ?? '';
  if (!/^\d+$/.test(decimalPage)) return 0;

  const page = Number(decimalPage);
  return Number.isSafeInteger(page) && page >= 0 ? page : 0;
}

function normalizeKeyword(value: string | null | undefined): string {
  return Array.from(value?.trim() ?? '').slice(0, 100).join('');
}

export function parseAdminFishSearchParams(params: URLSearchParams): AdminFishFilters {
  return {
    q: normalizeKeyword(params.get('q')),
    status: normalizeStatus(params.get('status')),
    page: normalizePage(params.get('page')),
  };
}

export function toAdminFishSearchParams(filters: AdminFishFilters): URLSearchParams {
  const params = new URLSearchParams();
  const q = normalizeKeyword(filters.q);
  const status = normalizeStatus(filters.status);
  const page = normalizePage(filters.page);

  if (q) params.set('q', q);
  if (status) params.set('status', status);
  if (page > 0) params.set('page', String(page));

  return params;
}
