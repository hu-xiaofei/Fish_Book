import { z } from 'zod';
import type { CatchRecordFormValues, CatchRecordInput } from './types';

const canonicalFishSlug = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

function isIsoDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;

  const date = new Date(`${value}T00:00:00.000Z`);
  return !Number.isNaN(date.valueOf()) && date.toISOString().slice(0, 10) === value;
}

function optionalMeasurement(max: number) {
  return z.preprocess(
    (value) => {
      if (value === null || value === undefined) return null;
      if (typeof value === 'string') {
        return value.trim() === '' ? null : Number(value);
      }
      return value;
    },
    z.number()
      .finite('请输入有效数值')
      .min(0, '数值不能小于 0')
      .max(max, '数值超出可记录范围')
      .refine(
        (value) => Math.abs(value * 100 - Math.round(value * 100)) < 1e-7,
        '最多保留两位小数',
      )
      .nullable(),
  );
}

export function catchRecordFormSchema(today: string) {
  return z.object({
    fishSlug: z.string().regex(canonicalFishSlug, '请选择有效鱼种'),
    caughtOn: z.string()
      .refine(isIsoDate, '请输入有效日期')
      .refine((value) => value <= today, '钓获日期不能晚于今天'),
    location: z.string().trim()
      .min(1, '请输入地点')
      .max(200, '地点最多 200 个字符'),
    lengthCm: optionalMeasurement(999999.99),
    weightG: optionalMeasurement(99999999.99),
    method: z.string().trim().max(100, '方法最多 100 个字符')
      .transform((value) => value || null),
    notes: z.string().trim().max(5000, '备注最多 5000 个字符')
      .transform((value) => value || null),
  });
}

export function parseCatchForm(
  values: CatchRecordFormValues,
  today: string = todayInShanghai(),
): CatchRecordInput {
  return catchRecordFormSchema(today).parse(values);
}

export function todayInShanghai(): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date());
  const value = Object.fromEntries(
    parts
      .filter((part) => part.type !== 'literal')
      .map((part) => [part.type, part.value]),
  );

  return `${value.year}-${value.month}-${value.day}`;
}
