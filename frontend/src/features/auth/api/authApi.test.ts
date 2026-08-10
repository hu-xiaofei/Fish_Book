import { afterEach, expect, test, vi } from 'vitest';
import { register } from './authApi';

afterEach(() => {
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/';
  vi.unstubAllGlobals();
});

test('posts the registration input to the registration endpoint', async () => {
  document.cookie = 'XSRF-TOKEN=test-csrf-token; Path=/';
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
    id: 1,
    email: 'Angler@Example.COM',
    nickname: 'Wall_E',
    role: 'USER',
  }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  }));
  vi.stubGlobal('fetch', fetchMock);

  await expect(register({
    email: 'Angler@Example.COM',
    password: 'strong-pass',
    nickname: 'Wall_E',
  })).resolves.toEqual({
    id: 1,
    email: 'Angler@Example.COM',
    nickname: 'Wall_E',
    role: 'USER',
  });

  expect(fetchMock).toHaveBeenCalledTimes(1);
  const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
  expect(path).toBe('/api/v1/auth/register');
  expect(init.method).toBe('POST');
  expect(init.credentials).toBe('include');
  expect(init.body).toBe(JSON.stringify({
    email: 'Angler@Example.COM',
    password: 'strong-pass',
    nickname: 'Wall_E',
  }));
});
