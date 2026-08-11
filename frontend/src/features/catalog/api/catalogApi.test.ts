import { afterEach, expect, test, vi } from 'vitest';
import {
  fetchFishDetail,
  fetchFishFilterOptions,
  fetchFishPage,
  fishDetailQueryKey,
  fishFilterOptionsQueryKey,
  fishListQueryKey,
} from './catalogApi';

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

test('requests a filtered fish page with the canonical query string', async () => {
  const page = { items: [], page: 1, size: 12, totalItems: 0, totalPages: 0 };
  const fetchMock = vi.fn().mockResolvedValue(jsonResponse(page));
  vi.stubGlobal('fetch', fetchMock);

  await expect(fetchFishPage({ q: '黑鱼', family: '', habitat: 'LAKE', page: 1 }))
    .resolves.toEqual(page);

  expect(fetchMock).toHaveBeenLastCalledWith(
    '/api/v1/fish?q=%E9%BB%91%E9%B1%BC&habitat=LAKE&page=1',
    expect.objectContaining({ credentials: 'include' }),
  );
});

test('requests filter options from the public filters endpoint', async () => {
  const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ families: [], habitats: [] }));
  vi.stubGlobal('fetch', fetchMock);

  await fetchFishFilterOptions();

  expect(fetchMock).toHaveBeenLastCalledWith(
    '/api/v1/fish/filters', expect.objectContaining({ credentials: 'include' }),
  );
});

test('URL-encodes the fish detail slug', async () => {
  const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ slug: 'fish slug' }));
  vi.stubGlobal('fetch', fetchMock);

  await fetchFishDetail('fish slug');

  expect(fetchMock).toHaveBeenLastCalledWith(
    '/api/v1/fish/fish%20slug', expect.objectContaining({ credentials: 'include' }),
  );
});

test('uses distinct list query keys whenever a filter changes', () => {
  expect(fishListQueryKey({ q: '', family: '', habitat: '', page: 0 }))
    .not.toEqual(fishListQueryKey({ q: '鲤', family: '', habitat: '', page: 0 }));
  expect(fishListQueryKey({ q: '', family: '', habitat: '', page: 0 }))
    .not.toEqual(fishListQueryKey({ q: '', family: '鲤科', habitat: '', page: 0 }));
  expect(fishListQueryKey({ q: '', family: '', habitat: '', page: 0 }))
    .not.toEqual(fishListQueryKey({ q: '', family: '', habitat: 'RIVER', page: 0 }));
  expect(fishListQueryKey({ q: '', family: '', habitat: '', page: 0 }))
    .not.toEqual(fishListQueryKey({ q: '', family: '', habitat: '', page: 1 }));
});

test('uses stable query keys for detail and filter requests', () => {
  expect(fishDetailQueryKey('channa-argus'))
    .toEqual(['fish-catalog', 'detail', 'channa-argus']);
  expect(fishFilterOptionsQueryKey).toEqual(['fish-catalog', 'filters']);
});
