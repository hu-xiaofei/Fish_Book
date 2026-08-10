import { afterEach, describe, expect, test, vi } from 'vitest';
import { ApiError } from './ApiError';
import { apiFetch } from './httpClient';

const errorBody = {
  code: 'DUPLICATE_EMAIL',
  message: '该邮箱已注册',
  fieldErrors: [{ field: 'email', message: '该邮箱已注册' }],
  requestId: 'request-123',
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function clearCsrfCookie() {
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/';
}

afterEach(() => {
  clearCsrfCookie();
  vi.unstubAllGlobals();
});

describe('apiFetch', () => {
  test('includes credentials and parses successful JSON', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ id: 1, nickname: 'River' }),
    );
    vi.stubGlobal('fetch', fetchMock);

    await expect(apiFetch<{ id: number; nickname: string }>('/api/v1/me'))
      .resolves.toEqual({ id: 1, nickname: 'River' });
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/me', {
      credentials: 'include',
      headers: expect.any(Headers),
    });
  });

  test('bootstraps a missing CSRF cookie and sends it on unsafe JSON requests', async () => {
    const fetchMock = vi
      .fn()
      .mockImplementationOnce(async () => {
        document.cookie = 'XSRF-TOKEN=csrf-token-123; Path=/';
        return jsonResponse({
          token: 'csrf-token-123',
          headerName: 'X-XSRF-TOKEN',
        });
      })
      .mockResolvedValueOnce(jsonResponse({ id: 1 }));
    vi.stubGlobal('fetch', fetchMock);

    await apiFetch<{ id: number }>('/api/v1/auth/register', {
      method: 'POST',
      headers: { Accept: 'application/json' },
      body: JSON.stringify({ email: 'angler@example.com' }),
    });

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/auth/csrf', {
      credentials: 'include',
    });
    const requestInit = fetchMock.mock.calls[1]?.[1] as RequestInit;
    const headers = new Headers(requestInit.headers);
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/v1/auth/register');
    expect(requestInit.credentials).toBe('include');
    expect(requestInit.method).toBe('POST');
    expect(headers.get('Accept')).toBe('application/json');
    expect(headers.get('Content-Type')).toBe('application/json');
    expect(headers.get('X-XSRF-TOKEN')).toBe('csrf-token-123');
  });

  test('uses an existing CSRF cookie without bootstrapping again', async () => {
    document.cookie = 'XSRF-TOKEN=existing-token; Path=/';
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ nickname: 'Lake' }));
    vi.stubGlobal('fetch', fetchMock);

    await apiFetch('/api/v1/me', {
      method: 'PATCH',
      body: JSON.stringify({ nickname: 'Lake' }),
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const requestInit = fetchMock.mock.calls[0]?.[1] as RequestInit;
    expect(new Headers(requestInit.headers).get('X-XSRF-TOKEN')).toBe(
      'existing-token',
    );
  });

  test('returns undefined for a successful 204 response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));

    await expect(apiFetch<void>('/api/v1/empty')).resolves.toBeUndefined();
  });

  test('throws an ApiError containing the backend error body', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(errorBody, 409)));

    const error = await apiFetch('/api/v1/me').catch((reason: unknown) => reason);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 409,
      message: '该邮箱已注册',
      body: errorBody,
    });
  });

  test('stops when CSRF bootstrapping returns an error', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(errorBody, 403));
    vi.stubGlobal('fetch', fetchMock);

    const error = await apiFetch('/api/v1/auth/logout', { method: 'DELETE' })
      .catch((reason: unknown) => reason);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 403, body: errorBody });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
