import type { CatalogFilters, HabitatCode } from './types';

const habitatCodes: readonly HabitatCode[] = [
  'RIVER',
  'LAKE',
  'RESERVOIR',
  'POND',
  'STREAM',
];

function normalizeHabitat(value: string | null | undefined): HabitatCode | '' {
  const code = value?.trim().toUpperCase();
  return code && habitatCodes.includes(code as HabitatCode) ? code as HabitatCode : '';
}

function normalizePage(value: string | number | null | undefined): number {
  const page = typeof value === 'number'
    ? value
    : Number(value?.trim() ?? '');

  return Number.isInteger(page) && page >= 0 ? page : 0;
}

export function parseCatalogSearchParams(params: URLSearchParams): CatalogFilters {
  return {
    q: params.get('q')?.trim() ?? '',
    family: params.get('family')?.trim() ?? '',
    habitat: normalizeHabitat(params.get('habitat')),
    page: normalizePage(params.get('page')),
  };
}

export function toCatalogSearchParams(filters: CatalogFilters): URLSearchParams {
  const params = new URLSearchParams();
  const q = filters.q.trim();
  const family = filters.family.trim();
  const habitat = normalizeHabitat(filters.habitat);
  const page = normalizePage(filters.page);

  if (q) params.set('q', q);
  if (family) params.set('family', family);
  if (habitat) params.set('habitat', habitat);
  if (page > 0) params.set('page', String(page));

  return params;
}
