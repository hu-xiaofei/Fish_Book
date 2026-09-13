import { expect, test } from 'vitest';
import { parseAdminPhotoSearchParams, toAdminPhotoSearchParams } from './adminPhotoSearchParams';

test('keeps the reviewed owner filter and zero-based page in the URL', () => {
  expect(parseAdminPhotoSearchParams(new URLSearchParams('userId=41&page=2')))
    .toEqual({ userId: '41', page: 2, size: 20 });
  expect(toAdminPhotoSearchParams({ userId: '41', page: 2, size: 20 }).toString()).toBe('userId=41&page=2');
  expect(parseAdminPhotoSearchParams(new URLSearchParams())).toEqual({ userId: '', page: 0, size: 20 });
});

test.each(['userId=abc', 'userId=0', 'userId=-1', 'userId=', 'userId=1&userId=2', 'userId=9223372036854775808', 'page=-1', 'page=1.5', 'page=', 'page=2147483648', 'size=50'])('rejects malformed filters instead of querying every owner: %s', (query) => {
  expect(parseAdminPhotoSearchParams(new URLSearchParams(query))).toBeUndefined();
});
