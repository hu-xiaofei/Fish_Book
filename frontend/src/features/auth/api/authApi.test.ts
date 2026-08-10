import { afterEach, expect, test, vi } from 'vitest';
import { fetchCsrf, login, logout, register } from './authApi';
import { fetchCurrentUser, updateNickname } from './currentUser';

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

test('fetches the CSRF bootstrap response from the public endpoint', async () => {
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
    token: 'test-csrf-token',
    headerName: 'X-XSRF-TOKEN',
  }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  }));
  vi.stubGlobal('fetch', fetchMock);

  await expect(fetchCsrf()).resolves.toEqual({
    token: 'test-csrf-token',
    headerName: 'X-XSRF-TOKEN',
  });

  expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/csrf', {
    credentials: 'include',
    headers: expect.any(Headers),
  });
});

test('posts login credentials to the login endpoint', async () => {
  document.cookie = 'XSRF-TOKEN=test-csrf-token; Path=/';
  const responseUser = {
    id: 1,
    email: 'angler@example.com',
    nickname: 'Wall_E',
    role: 'USER',
  };
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(responseUser), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  }));
  vi.stubGlobal('fetch', fetchMock);

  await expect(login({
    email: 'angler@example.com',
    password: 'strong-pass',
  })).resolves.toEqual(responseUser);

  const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
  expect(path).toBe('/api/v1/auth/login');
  expect(init.method).toBe('POST');
  expect(init.body).toBe(JSON.stringify({
    email: 'angler@example.com',
    password: 'strong-pass',
  }));
});

test('posts logout to the logout endpoint', async () => {
  document.cookie = 'XSRF-TOKEN=test-csrf-token; Path=/';
  const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
  vi.stubGlobal('fetch', fetchMock);

  await expect(logout()).resolves.toBeUndefined();

  const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
  expect(path).toBe('/api/v1/auth/logout');
  expect(init.method).toBe('POST');
  expect(init.body).toBeUndefined();
});

test('fetches the current user from the profile endpoint', async () => {
  const responseUser = {
    id: 1,
    email: 'angler@example.com',
    nickname: 'Wall_E',
    role: 'USER',
  };
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(responseUser), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  }));
  vi.stubGlobal('fetch', fetchMock);

  await expect(fetchCurrentUser()).resolves.toEqual(responseUser);

  const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
  expect(path).toBe('/api/v1/me');
  expect(init.method).toBeUndefined();
});

test('patches the normalized nickname to the profile endpoint', async () => {
  document.cookie = 'XSRF-TOKEN=test-csrf-token; Path=/';
  const responseUser = {
    id: 1,
    email: 'angler@example.com',
    nickname: 'River',
    role: 'USER',
  };
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(responseUser), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  }));
  vi.stubGlobal('fetch', fetchMock);

  await expect(updateNickname('River')).resolves.toEqual(responseUser);

  const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
  expect(path).toBe('/api/v1/me');
  expect(init.method).toBe('PATCH');
  expect(init.body).toBe(JSON.stringify({ nickname: 'River' }));
});
