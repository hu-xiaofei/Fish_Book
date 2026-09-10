import { z } from 'zod';
import type { HabitatCode } from '../../catalog/model/types';
import type { AdminFishCreateInput, AdminFishDetail } from './types';

export type AdminFishFormValues = {
  slug: string;
  commonNameZh: string;
  scientificName: string;
  familyNameZh: string;
  familyScientificName: string;
  genusNameZh: string;
  genusScientificName: string;
  aliasesText: string;
  habitats: HabitatCode[];
  appearance: string;
  sizeDescription: string;
  habitatDescription: string;
  distribution: string;
  description: string;
  imagePath: string;
  imageAltText: string;
  imageSourceUrl: string;
  imageAuthor: string;
  imageLicenseName: string;
  imageLicenseUrl: string;
  displayOrder: string;
};

const habitats = ['RIVER', 'LAKE', 'RESERVOIR', 'POND', 'STREAM'] as const;
const canonicalSlug = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const localFishImage = /^\/images\/fish\/[a-z0-9-]+\.(jpg|jpeg|png|webp)$/;
const httpUrl = /^https?:\/\/.+/;

function codePointCount(value: string) {
  return Array.from(value).length;
}

function requiredText(max: number, message = '此字段不能为空或超出长度限制') {
  return z.string().refine(
    (value) => value.trim().length > 0 && codePointCount(value.trim()) <= max,
    message,
  );
}

function normalizedAliases(value: string) {
  return [...new Set(value.split('\n').map((alias) => alias.trim()).filter(Boolean))];
}

const rawAdminFishFormSchema = z.object({
  slug: z.string().refine(
    (value) => canonicalSlug.test(value.trim()) && codePointCount(value.trim()) <= 120,
    'Slug 必须是最多 120 个字符的小写连字符标识',
  ),
  commonNameZh: requiredText(100),
  scientificName: requiredText(160),
  familyNameZh: requiredText(100),
  familyScientificName: requiredText(160),
  genusNameZh: requiredText(100),
  genusScientificName: requiredText(160),
  aliasesText: z.string().refine((value) => {
    const aliases = normalizedAliases(value);
    return aliases.length > 0 && aliases.every((alias) => codePointCount(alias) <= 100);
  }, '请至少填写一个不超过 100 个字符的别名'),
  habitats: z.array(z.enum(habitats)).min(1, '请至少选择一个栖息地'),
  appearance: requiredText(10_000),
  sizeDescription: requiredText(10_000),
  habitatDescription: requiredText(10_000),
  distribution: requiredText(10_000),
  description: requiredText(10_000),
  imagePath: z.string().refine(
    (value) => localFishImage.test(value.trim()) && codePointCount(value.trim()) <= 255,
    '图片路径必须是本地图鉴图片路径',
  ),
  imageAltText: requiredText(255),
  imageSourceUrl: z.string().refine(
    (value) => httpUrl.test(value.trim()) && codePointCount(value.trim()) <= 1000,
    '请填写有效的图片来源链接',
  ),
  imageAuthor: requiredText(255),
  imageLicenseName: requiredText(100),
  imageLicenseUrl: z.string().refine(
    (value) => httpUrl.test(value.trim()) && codePointCount(value.trim()) <= 1000,
    '请填写有效的图片许可链接',
  ),
  displayOrder: z.string().refine((value) => {
    const normalized = value.trim();
    return /^\d+$/.test(normalized)
      && Number.isSafeInteger(Number(normalized))
      && Number(normalized) > 0;
  }, '显示顺序必须是正整数'),
});

export const adminFishFormSchema = rawAdminFishFormSchema.transform((values): AdminFishCreateInput => ({
  slug: values.slug.trim(),
  commonNameZh: values.commonNameZh.trim(),
  scientificName: values.scientificName.trim(),
  familyNameZh: values.familyNameZh.trim(),
  familyScientificName: values.familyScientificName.trim(),
  genusNameZh: values.genusNameZh.trim(),
  genusScientificName: values.genusScientificName.trim(),
  aliases: normalizedAliases(values.aliasesText),
  habitats: values.habitats,
  appearance: values.appearance.trim(),
  sizeDescription: values.sizeDescription.trim(),
  habitatDescription: values.habitatDescription.trim(),
  distribution: values.distribution.trim(),
  description: values.description.trim(),
  imagePath: values.imagePath.trim(),
  imageAltText: values.imageAltText.trim(),
  imageSourceUrl: values.imageSourceUrl.trim(),
  imageAuthor: values.imageAuthor.trim(),
  imageLicenseName: values.imageLicenseName.trim(),
  imageLicenseUrl: values.imageLicenseUrl.trim(),
  displayOrder: Number(values.displayOrder.trim()),
}));

export function parseAdminFishForm(values: AdminFishFormValues): AdminFishCreateInput {
  return adminFishFormSchema.parse(values);
}

export function adminFishDetailToFormValues(fish: AdminFishDetail): AdminFishFormValues {
  return {
    slug: fish.slug,
    commonNameZh: fish.commonNameZh,
    scientificName: fish.scientificName,
    familyNameZh: fish.familyNameZh,
    familyScientificName: fish.familyScientificName,
    genusNameZh: fish.genusNameZh,
    genusScientificName: fish.genusScientificName,
    aliasesText: fish.aliases.join('\n'),
    habitats: [...fish.habitats],
    appearance: fish.appearance,
    sizeDescription: fish.sizeDescription,
    habitatDescription: fish.habitatDescription,
    distribution: fish.distribution,
    description: fish.description,
    imagePath: fish.imagePath,
    imageAltText: fish.imageAltText,
    imageSourceUrl: fish.imageSourceUrl,
    imageAuthor: fish.imageAuthor,
    imageLicenseName: fish.imageLicenseName,
    imageLicenseUrl: fish.imageLicenseUrl,
    displayOrder: String(fish.displayOrder),
  };
}
