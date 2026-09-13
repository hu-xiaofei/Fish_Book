import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { adminPhotoContentUrl, fetchAdminPhoto, fetchAdminPhotoOperations, fetchAdminPhotoPage, removeAdminPhoto, replaceAdminPhoto } from './adminPhotoApi';

beforeEach(() => { document.cookie = 'XSRF-TOKEN=admin-token; Path=/'; });
afterEach(() => { document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/'; vi.unstubAllGlobals(); });

test('reads dedicated private metadata and paginated operations without public image URLs', async () => {
  const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify({ items: [] }))));
  vi.stubGlobal('fetch', fetchMock);
  await fetchAdminPhotoPage({ userId: '41', page: 2, size: 20 });
  await fetchAdminPhoto(31);
  await fetchAdminPhotoOperations(31, 1);
  expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(['/api/v1/admin/photos?userId=41&page=2&size=20', '/api/v1/admin/photos/31', '/api/v1/admin/photos/31/operations?page=1&size=20']);
  expect(adminPhotoContentUrl(31)).toBe('/api/v1/admin/photos/31/content');
});

test('writes multipart with CSRF and exact string revision and never retries conflict', async () => {
  const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
  vi.stubGlobal('fetch', fetchMock);
  const file = new File(['jpeg'], 'photo.jpg', { type: 'image/jpeg' });
  await replaceAdminPhoto(31, file, '9007199254740993');
  const init = fetchMock.mock.calls[0][1] as RequestInit;
  expect(init).toMatchObject({ method: 'PUT', credentials: 'include' });
  expect((init.body as FormData).get('photo')).toBe(file);
  expect(new Headers(init.headers).get('Content-Type')).toBeNull();
  expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('admin-token');
  expect(new Headers(init.headers).get('If-Match')).toBe('"9007199254740993"');
  await removeAdminPhoto(31, '7');
  expect(fetchMock.mock.calls[1][1].method).toBe('DELETE');
  expect(new Headers(fetchMock.mock.calls[1][1].headers).get('If-Match')).toBe('"7"');
  fetchMock.mockResolvedValue(new Response(JSON.stringify({ code: 'CATCH_PHOTO_CONFLICT', message: '冲突', fieldErrors: [], requestId: 'conflict' }), { status: 409 }));
  await expect(replaceAdminPhoto(31, file, '7')).rejects.toMatchObject({ status: 409 });
  expect(fetchMock).toHaveBeenCalledTimes(3);
});
