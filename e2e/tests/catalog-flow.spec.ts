import { expect, test } from '@playwright/test';

test('browses, searches, filters, and deep-links through the public catalog', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'FishBook' })).toBeVisible();
  await expect(page.getByRole('article')).toHaveCount(12);

  await page.getByRole('searchbox', { name: '搜索鱼类' }).fill('黑鱼');
  await page.getByRole('button', { name: '搜索' }).click();
  await expect(page).toHaveURL(/q=%E9%BB%91%E9%B1%BC/);
  await expect(page.getByRole('link', { name: '查看乌鳢详情' })).toBeVisible();

  await page.getByLabel('栖息环境').selectOption('LAKE');
  await expect(page).toHaveURL(/habitat=LAKE/);
  await page.getByRole('link', { name: '查看乌鳢详情' }).click();
  await expect(page).toHaveURL(/\/fish\/channa-argus$/);
  await expect(page.getByRole('heading', { name: '乌鳢' })).toBeVisible();
  await expect(page.getByRole('link', { name: /许可证/ })).toBeVisible();

  const imageResponse = await page.request.get('/images/fish/channa-argus.jpg');
  expect(imageResponse.status()).toBe(200);
  expect(imageResponse.headers()['content-type']).toContain('image/jpeg');

  await page.reload();
  await expect(page.getByRole('heading', { name: '乌鳢' })).toBeVisible();
  await page.getByRole('link', { name: '返回图鉴' }).click();
  await expect(page).toHaveURL(/q=%E9%BB%91%E9%B1%BC/);
});
