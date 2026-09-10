import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import type { AdminFishCreateInput, AdminFishDetail, AdminFishUpdateInput } from '../model/types';
import {
  ADMIN_FISHES_QUERY_KEY,
  adminFishDetailQueryKey,
  adminFishPageQueryKey,
  createAdminFish,
  fetchAdminFish,
  fetchAdminFishPage,
  publishAdminFish,
  unpublishAdminFish,
  updateAdminFish,
} from './adminFishApi';

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

const content: AdminFishUpdateInput = {
  commonNameZh: '鲤鱼', scientificName: 'Cyprinus carpio',
  familyNameZh: '鲤科', familyScientificName: 'Cyprinidae',
  genusNameZh: '鲤属', genusScientificName: 'Cyprinus', aliases: ['鲤'], habitats: ['RIVER'],
  appearance: '体侧扁', sizeDescription: '常见 30 厘米', habitatDescription: '江河湖泊',
  distribution: '全国', description: '常见淡水鱼', imagePath: '/images/fish/carp.jpg',
  imageAltText: '鲤鱼', imageSourceUrl: 'https://example.com/source', imageAuthor: '作者',
  imageLicenseName: 'CC BY 4.0', imageLicenseUrl: 'https://creativecommons.org/licenses/by/4.0/',
  displayOrder: 1,
};

const createInput: AdminFishCreateInput = { ...content, slug: 'cyprinus-carpio' };
const detail: AdminFishDetail = {
  ...createInput, id: 7, status: 'DRAFT', publishedAt: null,
  createdAt: '2026-08-30T10:00:00Z', updatedAt: '2026-08-30T10:00:00Z',
};
const emptyPage = { items: [], page: 2, size: 20 as const, totalItems: 0, totalPages: 0 };

function lastRequest(fetchMock: ReturnType<typeof vi.fn>) {
  const [url, init] = fetchMock.mock.calls.at(-1) as [string, RequestInit];
  return { url, method: init.method, body: init.body, credentials: init.credentials };
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token; Path=/';
});

afterEach(() => {
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/';
  vi.unstubAllGlobals();
});

test('fetches a filtered management page without a size parameter', async () => {
  const fetchMock = vi.fn().mockResolvedValue(jsonResponse(emptyPage));
  vi.stubGlobal('fetch', fetchMock);

  await expect(fetchAdminFishPage({ q: '鲤', status: 'DRAFT', page: 2 }))
    .resolves.toEqual(emptyPage);

  expect(fetchMock).toHaveBeenCalledWith(
    '/api/v1/admin/fishes?q=%E9%B2%A4&status=DRAFT&page=2',
    expect.objectContaining({ credentials: 'include' }),
  );
});

test('uses stable query roots for management pages and details', () => {
  expect(ADMIN_FISHES_QUERY_KEY).toEqual(['admin-fishes']);
  expect(adminFishPageQueryKey({ q: '鲤', status: 'DRAFT', page: 2 }))
    .toEqual(['admin-fishes', 'list', '鲤', 'DRAFT', 2]);
  expect(adminFishDetailQueryKey(7)).toEqual(['admin-fishes', 'detail', 7]);
});

test('fetches an individual management fish by ID', async () => {
  const fetchMock = vi.fn().mockResolvedValue(jsonResponse(detail));
  vi.stubGlobal('fetch', fetchMock);

  await expect(fetchAdminFish(7)).resolves.toEqual(detail);
  expect(lastRequest(fetchMock)).toMatchObject({ url: '/api/v1/admin/fishes/7' });
});

test('creates a fish with the complete JSON representation through the shared CSRF client', async () => {
  const fetchMock = vi.fn().mockResolvedValue(jsonResponse(detail));
  vi.stubGlobal('fetch', fetchMock);

  await createAdminFish(createInput);

  expect(lastRequest(fetchMock)).toMatchObject({
    url: '/api/v1/admin/fishes', method: 'POST', body: JSON.stringify(createInput), credentials: 'include',
  });
  expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get('X-XSRF-TOKEN')).toBe('test-token');
});

test('uses independent content and publication endpoints', async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(jsonResponse(detail))
    .mockResolvedValueOnce(jsonResponse({ ...detail, status: 'PUBLISHED' }))
    .mockResolvedValueOnce(jsonResponse({ ...detail, status: 'UNPUBLISHED' }));
  vi.stubGlobal('fetch', fetchMock);

  await updateAdminFish(7, content);
  expect(lastRequest(fetchMock)).toMatchObject({
    url: '/api/v1/admin/fishes/7', method: 'PUT', body: JSON.stringify(content),
  });

  await publishAdminFish(7);
  expect(lastRequest(fetchMock)).toMatchObject({ url: '/api/v1/admin/fishes/7/publish', method: 'POST' });

  await unpublishAdminFish(7);
  expect(lastRequest(fetchMock)).toMatchObject({ url: '/api/v1/admin/fishes/7/unpublish', method: 'POST' });
});
