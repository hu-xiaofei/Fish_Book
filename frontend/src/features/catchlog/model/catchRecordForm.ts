import { z } from 'zod';
import type { CatchRecordFormValues, CatchRecordInput } from './types';

const canonicalFishSlug = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const decimalLiteral = /^-?\d+(?:\.\d+)?$/;

function isIsoDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;

  const date = new Date(`${value}T00:00:00.000Z`);
  return !Number.isNaN(date.valueOf()) && date.toISOString().slice(0, 10) === value;
}

function optionalMeasurement(max: number) {
  return z.union([z.string(), z.number(), z.null(), z.undefined()])
    .transform((value, context) => {
      if (value === null || value === undefined) return null;

      const literal = typeof value === 'string' ? value.trim() : String(value);
      if (literal === '') return null;
      if (!decimalLiteral.test(literal)) {
        context.addIssue({ code: 'custom', message: '请输入普通十进制数值' });
        return z.NEVER;
      }

      const fraction = literal.split('.')[1] ?? '';
      if (fraction.replace(/0+$/, '').length > 2) {
        context.addIssue({ code: 'custom', message: '最多保留两位小数' });
        return z.NEVER;
      }

      const numericValue = Number(literal);
      if (!Number.isFinite(numericValue)) {
        context.addIssue({ code: 'custom', message: '请输入有效数值' });
        return z.NEVER;
      }
      if (numericValue < 0) {
        context.addIssue({ code: 'custom', message: '数值不能小于 0' });
        return z.NEVER;
      }
      if (numericValue > max) {
        context.addIssue({ code: 'custom', message: '数值超出可记录范围' });
        return z.NEVER;
      }
      return numericValue;
    });
}

export function catchRecordFormSchema(today: string) {
  return z.object({
    fishSlug: z.string().regex(canonicalFishSlug, '请选择有效鱼种'),
    caughtOn: z.string()
      .refine(isIsoDate, '请输入有效日期')
      .refine(
        (value) => !isIsoDate(value) || value >= '1000-01-01',
        '钓获日期不能早于 1000-01-01',
      )
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
