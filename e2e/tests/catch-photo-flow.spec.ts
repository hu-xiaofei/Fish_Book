import { expect, test, type Page } from '@playwright/test';
import path from 'node:path';

const firstPhoto = path.resolve(
  process.cwd(),
  '../frontend/public/images/fish/channa-argus.jpg',
);
const replacementPhoto = path.resolve(
  process.cwd(),
  '../frontend/public/images/fish/cyprinus-carpio.jpg',
);

async function registerAndLogin(page: Page, prefix: string) {
  const uniqueId = `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  const email = `${prefix}-${uniqueId}@example.com`;

  await page.goto('/register');
  await page.getByLabel('邮箱').fill(email);
  await page.getByLabel('密码').fill('strong-pass');
  await page.getByLabel('昵称').fill('Photo Angler');
  await page.getByRole('button', { name: '注册' }).click();
  await expect(page.getByRole('status')).toHaveText('注册成功，请登录');
  await page.getByLabel('邮箱').fill(email);
  await page.getByLabel('密码').fill('strong-pass');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/profile$/);
}

test('uploads, protects, replaces, reloads, and removes one private catch photo', async ({
  browser,
  page,
}) => {
  await registerAndLogin(page, 'photo-owner');
  await page.goto('/catches/new');
  await page.getByLabel('鱼种').selectOption('channa-argus');
  await page.getByLabel('钓获日期').fill('2026-08-20');
  await page.getByLabel('地点').fill('城郊水库');
  await page.getByLabel('照片（可选）', { exact: true }).setInputFiles(firstPhoto);
  await page.getByRole('button', { name: '保存记录' }).click();

  await expect(page).toHaveURL(/\/catches\/\d+$/);
  const recordPath = new URL(page.url()).pathname;
  const recordId = recordPath.split('/').at(-1);
  const photoPath = `/api/v1/catches/${recordId}/photo`;
  await expect(page.getByRole('img', { name: '乌鳢钓获照片' })).toBeVisible();

  const originalResponse = await page.request.get(photoPath);
  expect(originalResponse.status()).toBe(200);
  expect(originalResponse.headers()['content-type']).toContain('image/jpeg');
  expect(originalResponse.headers()['cache-control']).toBe('private, no-store');
  const originalBytes = await originalResponse.body();
  expect(originalBytes.byteLength).toBeGreaterThan(0);

  await page.reload();
  await expect(page.getByRole('img', { name: '乌鳢钓获照片' })).toBeVisible();

  const otherContext = await browser.newContext();
  try {
    const otherPage = await otherContext.newPage();
    await registerAndLogin(otherPage, 'photo-foreign');
    const foreignResponse = await otherPage.request.get(photoPath);
    expect(foreignResponse.status()).toBe(404);
    expect(foreignResponse.headers()['content-type']).toContain('application/json');
    expect(await foreignResponse.json()).toMatchObject({
      code: 'CATCH_PHOTO_NOT_FOUND',
    });
  } finally {
    await otherContext.close();
  }

  await page.getByLabel('钓获照片').setInputFiles(replacementPhoto);
  const replaceResponsePromise = page.waitForResponse((response) => (
    response.url().includes(photoPath)
      && response.request().method() === 'PUT'
  ));
  await page.getByRole('button', { name: '替换照片' }).click();
  expect((await replaceResponsePromise).status()).toBe(204);

  const replacedResponse = await page.request.get(photoPath);
  expect(replacedResponse.status()).toBe(200);
  expect(replacedResponse.headers()['content-type']).toContain('image/jpeg');
  expect(await replacedResponse.body()).not.toEqual(originalBytes);

  await page.getByRole('button', { name: '移除照片' }).click();
  await expect(page.getByRole('alertdialog', { name: '确认移除照片' })).toBeVisible();
  const removeResponsePromise = page.waitForResponse((response) => (
    response.url().includes(photoPath)
      && response.request().method() === 'DELETE'
  ));
  await page.getByRole('button', { name: '确认移除' }).click();
  expect((await removeResponsePromise).status()).toBe(204);
  await expect(page.getByText('暂无照片')).toBeVisible();

  const removedResponse = await page.request.get(photoPath);
  expect(removedResponse.status()).toBe(404);
  expect(await removedResponse.json()).toMatchObject({ code: 'CATCH_PHOTO_NOT_FOUND' });
});
