import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import type { CatchRecordInput } from '../model/types';
import {
  CATCHES_QUERY_KEY,
  catchDetailQueryKey,
  catchPageQueryKey,
  createCatchRecord,
  deleteCatchRecord,
  fetchCatchPage,
  fetchCatchRecord,
  updateCatchRecord,
} from './catchRecordsApi';

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

const input: CatchRecordInput = {
  fishSlug: 'channa-argus',
  caughtOn: '2026-08-20',
  location: '城郊水库',
  lengthCm: 42.5,
  weightG: 1350,
  method: '路亚',
  notes: '傍晚近岸中鱼',
};

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token; Path=/';
});

afterEach(() => {
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/';
  vi.unstubAllGlobals();
});

describe('catch records API', () => {
  test('uses page and detail keys that support precise invalidation', () => {
    expect(CATCHES_QUERY_KEY).toEqual(['catches']);
    expect(catchPageQueryKey(2)).toEqual(['catches', 'page', 2]);
    expect(catchDetailQueryKey(31)).toEqual(['catches', 'detail', 31]);
    expect(catchPageQueryKey(2)).not.toEqual(catchDetailQueryKey(2));
  });

  test('requests the server-fixed page size without a size parameter', async () => {
    const response = { items: [], page: 2, size: 20, totalItems: 0, totalPages: 0 };
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(response));
    vi.stubGlobal('fetch', fetchMock);

    await expect(fetchCatchPage(2)).resolves.toEqual(response);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/catches?page=2',
      expect.objectContaining({ credentials: 'include' }),
    );
  });

  test('requests an individual catch record by ID', async () => {
    const response = { id: 31, ...input, commonNameZh: '乌鳢', hasPhoto: false };
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(response));
    vi.stubGlobal('fetch', fetchMock);

    await expect(fetchCatchRecord(31)).resolves.toEqual(response);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/catches/31',
      expect.objectContaining({ credentials: 'include' }),
    );
  });

  test('creates a record with JSON through the shared CSRF client', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 31, ...input }));
    vi.stubGlobal('fetch', fetchMock);

    await createCatchRecord(input);

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/catches');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'POST',
      body: JSON.stringify(input),
      credentials: 'include',
    });
    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get('X-XSRF-TOKEN'))
      .toBe('test-token');
  });

  test('updates a record with the complete JSON representation', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 31, ...input }));
    vi.stubGlobal('fetch', fetchMock);

    await updateCatchRecord(31, input);

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/catches/31');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'PUT',
      body: JSON.stringify(input),
      credentials: 'include',
    });
  });

  test('deletes a record through the shared CSRF client', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(deleteCatchRecord(31)).resolves.toBeUndefined();

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/catches/31');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'DELETE',
      credentials: 'include',
    });
  });
});
