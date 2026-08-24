import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import {
  catchPhotoUrl,
  putCatchPhoto,
  removeCatchPhoto,
} from './catchPhotoApi';

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=photo-token; Path=/';
});

afterEach(() => {
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/';
  vi.unstubAllGlobals();
});

describe('catch photo API', () => {
  test('uploads multipart field photo without forcing a JSON content type', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);
    const file = new File(['jpeg'], 'catch.jpg', { type: 'image/jpeg' });

    await putCatchPhoto(31, file);

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/catches/31/photo');
    const init = fetchMock.mock.calls[0]?.[1] as RequestInit;
    expect(init).toMatchObject({ method: 'PUT', credentials: 'include' });
    expect(init.body).toBeInstanceOf(FormData);
    expect((init.body as FormData).get('photo')).toBe(file);
    expect(new Headers(init.headers).get('Content-Type')).toBeNull();
    expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('photo-token');
  });

  test('removes photos through the encoded record path and exposes the binary URL', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    await removeCatchPhoto(31);

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/catches/31/photo');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'DELETE',
      credentials: 'include',
    });
    expect(catchPhotoUrl(31)).toBe('/api/v1/catches/31/photo');
  });
});
