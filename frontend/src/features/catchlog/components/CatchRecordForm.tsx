import { useEffect, useState } from 'react';
import { useForm, type FieldPath } from 'react-hook-form';
import { ZodError } from 'zod';
import { isConfirmedUnauthorized } from '../../auth/api/currentUser';
import { ApiError } from '../../../shared/api/ApiError';
import { FormField } from '../../../shared/ui/FormField';
import { parseCatchForm, todayInShanghai } from '../model/catchRecordForm';
import type { CatchRecordFormValues, CatchRecordInput } from '../model/types';

export type CatchRecordFormProps = {
  fishOptions: Array<{ slug: string; commonNameZh: string }>;
  initialValues?: CatchRecordFormValues;
  submitLabel: string;
  onSubmit: (input: CatchRecordInput) => Promise<void>;
};

const genericSaveError = '保存记录失败，请稍后重试';

const formFields = new Set<keyof CatchRecordFormValues>([
  'fishSlug', 'caughtOn', 'location', 'lengthCm', 'weightG', 'method', 'notes',
]);

function fieldProps(
  id: string,
  error: string | undefined,
) {
  return {
    id,
    'aria-invalid': Boolean(error),
    'aria-describedby': error ? `${id}-error` : undefined,
  };
}

function millisecondsUntilNextShanghaiDay(today: string): number {
  const nextMidnight = Date.parse(`${today}T16:00:00.000Z`);
  return Math.max(1, nextMidnight - Date.now());
}

export function CatchRecordForm({
  fishOptions,
  initialValues,
  submitLabel,
  onSubmit,
}: CatchRecordFormProps) {
  const [today, setToday] = useState(todayInShanghai);
  const [serverError, setServerError] = useState<string>();
  const {
    register,
    handleSubmit,
    setError,
    clearErrors,
    formState: { errors, isSubmitting },
  } = useForm<CatchRecordFormValues>({
    defaultValues: {
      fishSlug: '',
      caughtOn: '',
      location: '',
      lengthCm: '',
      weightG: '',
      method: '',
      notes: '',
      ...initialValues,
    },
  });

  useEffect(() => {
    let timeoutId: number;
    const refreshToday = () => {
      const currentToday = todayInShanghai();
      setToday(currentToday);
      timeoutId = window.setTimeout(
        refreshToday,
        millisecondsUntilNextShanghaiDay(currentToday),
      );
    };

    timeoutId = window.setTimeout(
      refreshToday,
      millisecondsUntilNextShanghaiDay(todayInShanghai()),
    );
    return () => window.clearTimeout(timeoutId);
  }, []);

  const submit = handleSubmit(async (values) => {
    clearErrors();
    setServerError(undefined);

    let input: CatchRecordInput;
    try {
      input = parseCatchForm(values, todayInShanghai());
    } catch (error) {
      if (error instanceof ZodError) {
        error.issues.forEach((issue) => {
          const field = issue.path[0];
          if (typeof field === 'string' && formFields.has(field as keyof CatchRecordFormValues)) {
            setError(field as FieldPath<CatchRecordFormValues>, {
              type: 'client',
              message: issue.message,
            });
          }
        });
        return;
      }

      setServerError(genericSaveError);
      return;
    }

    try {
      await onSubmit(input);
    } catch (error) {
      if (error instanceof ApiError) {
        let mappedFieldError = false;
        error.body.fieldErrors.forEach((fieldError) => {
          if (!formFields.has(fieldError.field as keyof CatchRecordFormValues)) return;

          mappedFieldError = true;
          setError(fieldError.field as FieldPath<CatchRecordFormValues>, {
            type: 'server',
            message: fieldError.message,
          });
        });
        if (mappedFieldError || isConfirmedUnauthorized(error)) return;
      }

      setServerError(genericSaveError);
    }
  });

  return (
    <form onSubmit={submit} noValidate>
      <FormField id="catch-fish-slug" label="鱼种" error={errors.fishSlug?.message}>
        <select
          {...fieldProps('catch-fish-slug', errors.fishSlug?.message)}
          {...register('fishSlug')}
        >
          <option value="">请选择鱼种</option>
          {fishOptions.map((fish) => (
            <option key={fish.slug} value={fish.slug}>{fish.commonNameZh}</option>
          ))}
        </select>
      </FormField>

      <FormField id="catch-caught-on" label="钓获日期" error={errors.caughtOn?.message}>
        <input
          type="date"
          min="1000-01-01"
          max={today}
          {...fieldProps('catch-caught-on', errors.caughtOn?.message)}
          {...register('caughtOn')}
        />
      </FormField>

      <FormField id="catch-location" label="地点" error={errors.location?.message}>
        <input
          type="text"
          {...fieldProps('catch-location', errors.location?.message)}
          {...register('location')}
        />
      </FormField>

      <FormField id="catch-length" label="长度（cm）" error={errors.lengthCm?.message}>
        <input
          type="number"
          min="0"
          max="999999.99"
          step="0.01"
          {...fieldProps('catch-length', errors.lengthCm?.message)}
          {...register('lengthCm')}
        />
      </FormField>

      <FormField id="catch-weight" label="重量（g）" error={errors.weightG?.message}>
        <input
          type="number"
          min="0"
          max="99999999.99"
          step="0.01"
          {...fieldProps('catch-weight', errors.weightG?.message)}
          {...register('weightG')}
        />
      </FormField>

      <FormField id="catch-method" label="钓法" error={errors.method?.message}>
        <input
          type="text"
          {...fieldProps('catch-method', errors.method?.message)}
          {...register('method')}
        />
      </FormField>

      <FormField id="catch-notes" label="备注" error={errors.notes?.message}>
        <textarea
          {...fieldProps('catch-notes', errors.notes?.message)}
          {...register('notes')}
        />
      </FormField>

      {serverError ? <p role="status" aria-live="polite">{serverError}</p> : null}

      <button type="submit" disabled={isSubmitting}>
        {isSubmitting ? '保存中…' : submitLabel}
      </button>
    </form>
  );
}
