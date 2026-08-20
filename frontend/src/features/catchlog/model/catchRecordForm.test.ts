import { describe, expect, test, vi } from 'vitest';
import { z } from 'zod';
import { parseCatchForm, todayInShanghai } from './catchRecordForm';

const validValues = {
  fishSlug: 'channa-argus',
  caughtOn: '2026-08-20',
  location: '城郊水库',
  lengthCm: '42.5',
  weightG: '1350',
  method: '路亚',
  notes: '傍晚近岸中鱼',
};

describe('catch record form parsing', () => {
  test('normalizes blank optional measurements and text to null', () => {
    const parsed = parseCatchForm({
      fishSlug: 'channa-argus',
      caughtOn: '2026-08-20',
      location: ' 水库 ',
      lengthCm: '',
      weightG: '',
      method: ' ',
      notes: '',
    }, '2026-08-20');

    expect(parsed).toEqual({
      fishSlug: 'channa-argus',
      caughtOn: '2026-08-20',
      location: '水库',
      lengthCm: null,
      weightG: null,
      method: null,
      notes: null,
    });
  });

  test('preserves zero measurements and accepts the storage boundaries', () => {
    expect(parseCatchForm({
      ...validValues,
      location: 'x'.repeat(200),
      lengthCm: '0',
      weightG: '99999999.99',
      method: 'm'.repeat(100),
      notes: 'n'.repeat(5000),
    }, '2026-08-20')).toEqual({
      fishSlug: 'channa-argus',
      caughtOn: '2026-08-20',
      location: 'x'.repeat(200),
      lengthCm: 0,
      weightG: 99999999.99,
      method: 'm'.repeat(100),
      notes: 'n'.repeat(5000),
    });
  });

  test.each([
    ['a future caught-on date', { ...validValues, caughtOn: '2026-08-21' }],
    ['an invalid ISO date', { ...validValues, caughtOn: '2026-02-30' }],
    ['a blank location', { ...validValues, location: '  ' }],
    ['a location longer than 200 characters', { ...validValues, location: 'x'.repeat(201) }],
    ['a method longer than 100 characters', { ...validValues, method: 'm'.repeat(101) }],
    ['notes longer than 5000 characters', { ...validValues, notes: 'n'.repeat(5001) }],
    ['a non-canonical fish slug', { ...validValues, fishSlug: 'Channa Argus' }],
  ])('rejects %s', (_description, values) => {
    expect(() => parseCatchForm(values, '2026-08-20')).toThrow(z.ZodError);
  });

  test.each([
    ['negative length', { ...validValues, lengthCm: '-0.01' }],
    ['length beyond DECIMAL(8,2)', { ...validValues, lengthCm: '1000000.00' }],
    ['length with meaningful scale beyond two decimals', { ...validValues, lengthCm: '42.501' }],
    ['negative weight', { ...validValues, weightG: '-0.01' }],
    ['weight beyond DECIMAL(10,2)', { ...validValues, weightG: '100000000.00' }],
    ['weight with meaningful scale beyond two decimals', { ...validValues, weightG: '1350.001' }],
  ])('rejects %s', (_description, values) => {
    expect(() => parseCatchForm(values, '2026-08-20')).toThrow(z.ZodError);
  });

  test('derives today in Asia/Shanghai rather than the browser local zone', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-20T16:30:00.000Z'));

    expect(todayInShanghai()).toBe('2026-08-21');

    vi.useRealTimers();
  });
});
