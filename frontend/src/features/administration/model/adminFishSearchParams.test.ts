import { expect, test } from 'vitest';
import {
  parseAdminFishSearchParams,
  toAdminFishSearchParams,
} from './adminFishSearchParams';

test('normalizes query status and page while dropping unsupported values', () => {
  expect(parseAdminFishSearchParams(new URLSearchParams(
    'q=%20%E9%B2%A4%20&status=draft&page=2&size=99',
  ))).toEqual({ q: '鲤', status: 'DRAFT', page: 2 });
});

test('serializes only non-default filters', () => {
  expect(toAdminFishSearchParams({ q: '', status: '', page: 0 }).toString()).toBe('');
  expect(toAdminFishSearchParams({ q: '鲤', status: 'UNPUBLISHED', page: 1 }).toString())
    .toBe('q=%E9%B2%A4&status=UNPUBLISHED&page=1');
});

test('normalizes invalid pages and caps keywords at 100 code points', () => {
  const longKeyword = `${'鱼'.repeat(100)}尾`;

  expect(parseAdminFishSearchParams(new URLSearchParams(`q=${longKeyword}&status=removed&page=1.5`)))
    .toEqual({ q: '鱼'.repeat(100), status: '', page: 0 });
  expect(parseAdminFishSearchParams(new URLSearchParams('page=-1'))).toMatchObject({ page: 0 });
  expect(parseAdminFishSearchParams(new URLSearchParams('page=9007199254740992')))
    .toMatchObject({ page: 0 });
});
