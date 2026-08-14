import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import {
  addFavorite,
  favoritePageQueryKey,
  favoriteStatusQueryKey,
  fetchFavoritePage,
  fetchFavoriteStatuses,
  removeFavorite,
} from './favoritesApi';

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token; Path=/';
});

afterEach(() => {
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/';
  vi.unstubAllGlobals();
});

describe('favorites API', () => {
  test('sorts status slugs in the query key without mutating the input', () => {
    const slugs = ['b', 'a'];

    expect(favoriteStatusQueryKey(slugs)).toEqual(['favorites', 'status', 'a', 'b']);
    expect(slugs).toEqual(['b', 'a']);
  });

  test('builds a page-scoped key and requests the fixed-size page without a size parameter', async () => {
    const response = {
      items: [],
      page: 2,
      size: 12,
      totalItems: 25,
      totalPages: 3,
    };
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(response));
    vi.stubGlobal('fetch', fetchMock);

    expect(favoritePageQueryKey(2)).toEqual(['favorites', 'page', 2]);
    await expect(fetchFavoritePage(2)).resolves.toEqual(response);

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/favorites?page=2');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ credentials: 'include' });
  });

  test('requests all statuses once with repeated encoded parameters in visible order', async () => {
    const response = {
      items: [
        { fishSlug: 'b', favorited: false },
        { fishSlug: 'a/b', favorited: true },
      ],
    };
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(response));
    vi.stubGlobal('fetch', fetchMock);

    await expect(fetchFavoriteStatuses(['b', 'a/b'])).resolves.toEqual(response);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      '/api/v1/favorites/status?fishSlug=b&fishSlug=a%2Fb',
    );
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ credentials: 'include' });
  });

  test.each([
    ['add', addFavorite, 'PUT'],
    ['remove', removeFavorite, 'DELETE'],
  ] as const)('%s uses an encoded slug and the shared credentialed client', async (
    _name,
    operation,
    method,
  ) => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(operation('fish/with space')).resolves.toBeUndefined();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toBe('/api/v1/favorites/fish%2Fwith%20space');
    expect(init).toMatchObject({ method, credentials: 'include' });
    expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('test-token');
  });
});
