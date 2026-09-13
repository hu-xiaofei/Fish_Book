import { afterEach, beforeEach, describe, expect, expectTypeOf, test, vi } from 'vitest';
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
  test('requires explicit revision in both write contracts', () => {
    expectTypeOf(putCatchPhoto).parameters.toEqualTypeOf<[number, File, string]>();
    expectTypeOf(removeCatchPhoto).parameters.toEqualTypeOf<[number, string]>();
  });
  test('uploads multipart field photo without forcing a JSON content type', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);
    const file = new File(['jpeg'], 'catch.jpg', { type: 'image/jpeg' });

    await putCatchPhoto(31, file, '7');

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/catches/31/photo');
    const init = fetchMock.mock.calls[0]?.[1] as RequestInit;
    expect(init).toMatchObject({ method: 'PUT', credentials: 'include' });
    expect(init.body).toBeInstanceOf(FormData);
    expect((init.body as FormData).get('photo')).toBe(file);
    expect(new Headers(init.headers).get('Content-Type')).toBeNull();
    expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('photo-token');
    expect(new Headers(init.headers).get('If-Match')).toBe('"7"');
  });

  test('removes photos through the encoded record path and exposes the binary URL', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    await removeCatchPhoto(31, '9007199254740993');

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/catches/31/photo');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'DELETE',
      credentials: 'include',
    });
    expect(catchPhotoUrl(31)).toBe('/api/v1/catches/31/photo');
    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get('If-Match'))
      .toBe('"9007199254740993"');
  });

  test('rejects a conflicting write without sending a second mutation', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 'CATCH_PHOTO_CONFLICT', message: '照片或记录已被修改，请刷新后重新确认操作',
      fieldErrors: [], requestId: 'conflict',
    }), { status: 409 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(putCatchPhoto(31, new File(['jpeg'], 'catch.jpg'), '7'))
      .rejects.toMatchObject({ status: 409 });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
