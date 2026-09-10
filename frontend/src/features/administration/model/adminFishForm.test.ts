import { expect, test } from 'vitest';
import { adminFishDetailToFormValues, parseAdminFishForm, type AdminFishFormValues } from './adminFishForm';
import type { AdminFishDetail } from './types';

const validFormValues: AdminFishFormValues = {
  slug: 'test-fish', commonNameZh: '测试鱼', scientificName: 'Testus piscis',
  familyNameZh: '测试科', familyScientificName: 'Testidae', genusNameZh: '测试属', genusScientificName: 'Testus',
  aliasesText: '别名一\n别名二', habitats: ['LAKE'], appearance: '外形描述', sizeDescription: '体型描述',
  habitatDescription: '栖息环境描述', distribution: '分布描述', description: '综合介绍',
  imagePath: '/images/fish/test-fish.jpg', imageAltText: '测试鱼图片', imageSourceUrl: 'https://example.com/source',
  imageAuthor: '测试作者', imageLicenseName: 'CC BY 4.0', imageLicenseUrl: 'https://example.com/license',
  displayOrder: '1',
};

test('normalizes every text field aliases habitats and display order', () => {
  expect(parseAdminFishForm({
    ...validFormValues, slug: '  test-fish  ', commonNameZh: '  测试鱼  ',
    aliasesText: ' 别名一\n\n别名二\n别名一 ', habitats: ['LAKE', 'RIVER'], displayOrder: '7',
  })).toMatchObject({
    slug: 'test-fish', commonNameZh: '测试鱼', aliases: ['别名一', '别名二'],
    habitats: ['LAKE', 'RIVER'], displayOrder: 7,
  });
});

test('enforces canonical slug and its 120 code point limit', () => {
  expect(parseAdminFishForm({ ...validFormValues, slug: 'a'.repeat(120) }).slug).toHaveLength(120);
  expect(() => parseAdminFishForm({ ...validFormValues, slug: 'Test Fish' })).toThrow();
  expect(() => parseAdminFishForm({ ...validFormValues, slug: 'a'.repeat(121) })).toThrow();
});

test('requires all names within their API sizes', () => {
  expect(() => parseAdminFishForm({ ...validFormValues, commonNameZh: ' ' })).toThrow();
  expect(() => parseAdminFishForm({ ...validFormValues, scientificName: 'a'.repeat(161) })).toThrow();
  expect(() => parseAdminFishForm({ ...validFormValues, familyNameZh: 'a'.repeat(101) })).toThrow();
  expect(() => parseAdminFishForm({ ...validFormValues, familyScientificName: 'a'.repeat(161) })).toThrow();
  expect(() => parseAdminFishForm({ ...validFormValues, genusNameZh: 'a'.repeat(101) })).toThrow();
  expect(() => parseAdminFishForm({ ...validFormValues, genusScientificName: 'a'.repeat(161) })).toThrow();
});

test('deduplicates aliases and rejects aliases over 100 code points', () => {
  expect(parseAdminFishForm({ ...validFormValues, aliasesText: ' 别名\n别名 ' }).aliases).toEqual(['别名']);
  expect(() => parseAdminFishForm({ ...validFormValues, aliasesText: 'a'.repeat(101) })).toThrow();
  expect(() => parseAdminFishForm({ ...validFormValues, aliasesText: '' })).toThrow();
});

test('requires at least one recognized habitat', () => {
  expect(() => parseAdminFishForm({ ...validFormValues, habitats: [] })).toThrow();
  expect(() => parseAdminFishForm({ ...validFormValues, habitats: ['OCEAN' as 'LAKE'] })).toThrow();
});

test('accepts content at 10000 code points and rejects longer content', () => {
  expect(parseAdminFishForm({ ...validFormValues, appearance: '鱼'.repeat(10_000) }).appearance).toHaveLength(10_000);
  expect(() => parseAdminFishForm({ ...validFormValues, description: '鱼'.repeat(10_001) })).toThrow();
});

test('requires a local fish image path and URL-shaped source and license fields', () => {
  expect(() => parseAdminFishForm({ ...validFormValues, imagePath: '/uploads/fish.jpg' })).toThrow();
  expect(() => parseAdminFishForm({ ...validFormValues, imageSourceUrl: 'ftp://example.com/source' })).toThrow();
  expect(() => parseAdminFishForm({ ...validFormValues, imageLicenseUrl: 'license' })).toThrow();
});

test('requires a positive safe integer display order', () => {
  expect(() => parseAdminFishForm({ ...validFormValues, displayOrder: '0' })).toThrow();
  expect(() => parseAdminFishForm({ ...validFormValues, displayOrder: '1.2' })).toThrow();
  expect(() => parseAdminFishForm({ ...validFormValues, displayOrder: String(Number.MAX_SAFE_INTEGER + 1) })).toThrow();
});

test('converts an administrator detail response back to editable form values', () => {
  const detail: AdminFishDetail = {
    ...parseAdminFishForm(validFormValues), id: 7, status: 'DRAFT', publishedAt: null,
    createdAt: '2026-08-30T00:00:00Z', updatedAt: '2026-08-30T00:00:00Z',
  };
  expect(adminFishDetailToFormValues(detail)).toMatchObject({ aliasesText: '别名一\n别名二', displayOrder: '1' });
});
