import { useState } from 'react';
import { useForm, type FieldPath } from 'react-hook-form';
import { ZodError } from 'zod';
import { ApiError } from '../../../shared/api/ApiError';
import { isConfirmedUnauthorized } from '../../auth/api/currentUser';
import { FormField } from '../../../shared/ui/FormField';
import { parseAdminFishForm, type AdminFishFormValues } from '../model/adminFishForm';
import type { AdminFishCreateInput } from '../model/types';

export type AdminFishFormProps = {
  initialValues?: AdminFishFormValues;
  slugReadOnly?: boolean;
  submitLabel: string;
  onSubmit: (input: AdminFishCreateInput) => Promise<void>;
};

const genericSaveError = '保存鱼类资料失败，请稍后重试';
const habitatLabels = {
  RIVER: '河流', LAKE: '湖泊', RESERVOIR: '水库', POND: '池塘', STREAM: '溪流',
} as const;
const formFields = new Set<keyof AdminFishFormValues>([
  'slug', 'commonNameZh', 'scientificName', 'familyNameZh', 'familyScientificName',
  'genusNameZh', 'genusScientificName', 'aliasesText', 'habitats', 'appearance',
  'sizeDescription', 'habitatDescription', 'distribution', 'description', 'imagePath',
  'imageAltText', 'imageSourceUrl', 'imageAuthor', 'imageLicenseName', 'imageLicenseUrl',
  'displayOrder',
]);

const emptyValues: AdminFishFormValues = {
  slug: '', commonNameZh: '', scientificName: '', familyNameZh: '', familyScientificName: '',
  genusNameZh: '', genusScientificName: '', aliasesText: '', habitats: [], appearance: '',
  sizeDescription: '', habitatDescription: '', distribution: '', description: '', imagePath: '',
  imageAltText: '', imageSourceUrl: '', imageAuthor: '', imageLicenseName: '', imageLicenseUrl: '',
  displayOrder: '',
};

function fieldProps(id: string, error?: string) {
  return {
    id,
    'aria-invalid': Boolean(error),
    'aria-describedby': error ? `${id}-error` : undefined,
  };
}

export function AdminFishForm({
  initialValues,
  slugReadOnly = false,
  submitLabel,
  onSubmit,
}: AdminFishFormProps) {
  const [serverError, setServerError] = useState<string>();
  const {
    register,
    handleSubmit,
    setError,
    clearErrors,
    formState: { errors, isSubmitting },
  } = useForm<AdminFishFormValues>({ defaultValues: { ...emptyValues, ...initialValues } });

  const submit = handleSubmit(async (values) => {
    clearErrors();
    setServerError(undefined);
    let input: AdminFishCreateInput;
    try {
      input = parseAdminFishForm(values);
    } catch (error) {
      if (error instanceof ZodError) {
        error.issues.forEach((issue) => {
          const field = issue.path[0];
          if (typeof field === 'string' && formFields.has(field as keyof AdminFishFormValues)) {
            setError(field as FieldPath<AdminFishFormValues>, { type: 'client', message: issue.message });
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
        if (error.status === 400 && error.body.code === 'VALIDATION_FAILED') {
          let mapped = false;
          error.body.fieldErrors.forEach((fieldError) => {
            const field = fieldError.field === 'aliases' ? 'aliasesText' : fieldError.field;
            if (!formFields.has(field as keyof AdminFishFormValues)) return;
            mapped = true;
            setError(field as FieldPath<AdminFishFormValues>, { type: 'server', message: fieldError.message });
          });
          if (mapped) return;
        }
        if (isConfirmedUnauthorized(error)) return;
        if (error.body.code === 'CATALOG_ENTRY_CONFLICT') {
          setServerError('Slug、中文名或学名已被使用');
          return;
        }
      }
      setServerError(genericSaveError);
    }
  });

  return (
    <form onSubmit={submit} noValidate>
      <fieldset>
        <legend>基本信息</legend>
        <FormField id="admin-fish-slug" label="Slug" error={errors.slug?.message}>
          <input type="text" readOnly={slugReadOnly} {...fieldProps('admin-fish-slug', errors.slug?.message)} {...register('slug')} />
        </FormField>
        <FormField id="admin-fish-common-name" label="中文名" error={errors.commonNameZh?.message}>
          <input type="text" {...fieldProps('admin-fish-common-name', errors.commonNameZh?.message)} {...register('commonNameZh')} />
        </FormField>
        <FormField id="admin-fish-scientific-name" label="学名" error={errors.scientificName?.message}>
          <input type="text" {...fieldProps('admin-fish-scientific-name', errors.scientificName?.message)} {...register('scientificName')} />
        </FormField>
        <FormField id="admin-fish-aliases" label="别名（每行一个）" error={errors.aliasesText?.message}>
          <textarea {...fieldProps('admin-fish-aliases', errors.aliasesText?.message)} {...register('aliasesText')} />
        </FormField>
        <div aria-describedby={errors.habitats ? 'admin-fish-habitats-error' : undefined}>
          <p>栖息地</p>
          {Object.entries(habitatLabels).map(([value, label]) => (
            <label key={value}>
              <input type="checkbox" value={value} {...register('habitats')} />{label}
            </label>
          ))}
          {errors.habitats ? <p id="admin-fish-habitats-error" aria-live="polite">{errors.habitats.message}</p> : null}
        </div>
      </fieldset>

      <fieldset>
        <legend>分类信息</legend>
        <FormField id="admin-fish-family-name" label="科中文名" error={errors.familyNameZh?.message}>
          <input type="text" {...fieldProps('admin-fish-family-name', errors.familyNameZh?.message)} {...register('familyNameZh')} />
        </FormField>
        <FormField id="admin-fish-family-scientific-name" label="科拉丁名" error={errors.familyScientificName?.message}>
          <input type="text" {...fieldProps('admin-fish-family-scientific-name', errors.familyScientificName?.message)} {...register('familyScientificName')} />
        </FormField>
        <FormField id="admin-fish-genus-name" label="属中文名" error={errors.genusNameZh?.message}>
          <input type="text" {...fieldProps('admin-fish-genus-name', errors.genusNameZh?.message)} {...register('genusNameZh')} />
        </FormField>
        <FormField id="admin-fish-genus-scientific-name" label="属拉丁名" error={errors.genusScientificName?.message}>
          <input type="text" {...fieldProps('admin-fish-genus-scientific-name', errors.genusScientificName?.message)} {...register('genusScientificName')} />
        </FormField>
      </fieldset>

      <fieldset>
        <legend>内容</legend>
        <FormField id="admin-fish-appearance" label="外形" error={errors.appearance?.message}><textarea {...fieldProps('admin-fish-appearance', errors.appearance?.message)} {...register('appearance')} /></FormField>
        <FormField id="admin-fish-size" label="体型" error={errors.sizeDescription?.message}><textarea {...fieldProps('admin-fish-size', errors.sizeDescription?.message)} {...register('sizeDescription')} /></FormField>
        <FormField id="admin-fish-habitat-description" label="栖息环境" error={errors.habitatDescription?.message}><textarea {...fieldProps('admin-fish-habitat-description', errors.habitatDescription?.message)} {...register('habitatDescription')} /></FormField>
        <FormField id="admin-fish-distribution" label="分布" error={errors.distribution?.message}><textarea {...fieldProps('admin-fish-distribution', errors.distribution?.message)} {...register('distribution')} /></FormField>
        <FormField id="admin-fish-description" label="介绍" error={errors.description?.message}><textarea {...fieldProps('admin-fish-description', errors.description?.message)} {...register('description')} /></FormField>
      </fieldset>

      <fieldset>
        <legend>图片署名</legend>
        <FormField id="admin-fish-image-path" label="图片路径" error={errors.imagePath?.message}><input type="text" {...fieldProps('admin-fish-image-path', errors.imagePath?.message)} {...register('imagePath')} /></FormField>
        <FormField id="admin-fish-image-alt" label="图片替代文字" error={errors.imageAltText?.message}><input type="text" {...fieldProps('admin-fish-image-alt', errors.imageAltText?.message)} {...register('imageAltText')} /></FormField>
        <FormField id="admin-fish-image-source" label="图片来源链接" error={errors.imageSourceUrl?.message}><input type="url" {...fieldProps('admin-fish-image-source', errors.imageSourceUrl?.message)} {...register('imageSourceUrl')} /></FormField>
        <FormField id="admin-fish-image-author" label="图片作者" error={errors.imageAuthor?.message}><input type="text" {...fieldProps('admin-fish-image-author', errors.imageAuthor?.message)} {...register('imageAuthor')} /></FormField>
        <FormField id="admin-fish-image-license-name" label="图片许可名称" error={errors.imageLicenseName?.message}><input type="text" {...fieldProps('admin-fish-image-license-name', errors.imageLicenseName?.message)} {...register('imageLicenseName')} /></FormField>
        <FormField id="admin-fish-image-license-url" label="图片许可链接" error={errors.imageLicenseUrl?.message}><input type="url" {...fieldProps('admin-fish-image-license-url', errors.imageLicenseUrl?.message)} {...register('imageLicenseUrl')} /></FormField>
      </fieldset>

      <fieldset>
        <legend>排序</legend>
        <FormField id="admin-fish-display-order" label="显示顺序" error={errors.displayOrder?.message}>
          <input type="number" min="1" step="1" {...fieldProps('admin-fish-display-order', errors.displayOrder?.message)} {...register('displayOrder')} />
        </FormField>
      </fieldset>

      {serverError ? <p role="status" aria-live="polite">{serverError}</p> : null}
      <button type="submit" disabled={isSubmitting}>{isSubmitting ? '保存中…' : submitLabel}</button>
    </form>
  );
}
