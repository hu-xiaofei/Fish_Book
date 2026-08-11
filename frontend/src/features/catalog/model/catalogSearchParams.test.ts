import { expect, test } from 'vitest';
import {
  parseCatalogSearchParams,
  toCatalogSearchParams,
} from './catalogSearchParams';

test('parses trimmed filters, a recognized habitat, and a non-negative page', () => {
  expect(parseCatalogSearchParams(new URLSearchParams(
    'q=%20%E9%BB%91%E9%B1%BC%20&family=%20%E9%B3%A2%E7%A7%91&habitat=lake&page=2',
  ))).toEqual({ q: '黑鱼', family: '鳢科', habitat: 'LAKE', page: 2 });
});

test('defaults invalid habitat and negative page values', () => {
  expect(parseCatalogSearchParams(new URLSearchParams('habitat=sea&page=-1')))
    .toEqual({ q: '', family: '', habitat: '', page: 0 });
});

test('defaults a fractional page value', () => {
  expect(parseCatalogSearchParams(new URLSearchParams('page=1.5')))
    .toEqual({ q: '', family: '', habitat: '', page: 0 });
});

test('omits empty filters and the first page from serialization', () => {
  expect(toCatalogSearchParams({ q: '', family: '', habitat: '', page: 0 }).toString())
    .toBe('');
});

test('serializes non-empty filters in canonical order and percent encoding', () => {
  expect(toCatalogSearchParams({
    q: '黑鱼', family: '鳢科', habitat: 'LAKE', page: 2,
  }).toString()).toBe(
    'q=%E9%BB%91%E9%B1%BC&family=%E9%B3%A2%E7%A7%91&habitat=LAKE&page=2',
  );
});

test('serializes only a non-empty text filter on the first page', () => {
  expect(toCatalogSearchParams({
    q: '黑鱼', family: '', habitat: '', page: 0,
  }).toString()).toBe('q=%E9%BB%91%E9%B1%BC');
});

test('normalizes Unicode-adjacent whitespace before serializing filters', () => {
  expect(toCatalogSearchParams({
    q: ' 黑鱼 ', family: ' 鳢科 ', habitat: 'LAKE', page: 2,
  }).toString()).toBe(
    'q=%E9%BB%91%E9%B1%BC&family=%E9%B3%A2%E7%A7%91&habitat=LAKE&page=2',
  );
});
