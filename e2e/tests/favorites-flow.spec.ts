import { expect, test } from '@playwright/test';

test('keeps a personal favorite through navigation and removes it persistently', async ({ page }) => {
  const uniqueId = `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  const email = `favorites-${uniqueId}@example.com`;

  await page.goto('/register');
  await page.getByLabel('邮箱').fill(email);
  await page.getByLabel('密码').fill('strong-pass');
  await page.getByLabel('昵称').fill('Favorite Angler');
  await page.getByRole('button', { name: '注册' }).click();
  await expect(page.getByRole('status')).toHaveText('注册成功，请登录');

  await page.getByLabel('邮箱').fill(email);
  await page.getByLabel('密码').fill('strong-pass');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/profile$/);

  await page.goto('/');
  await page.getByRole('searchbox', { name: '搜索鱼类' }).fill('黑鱼');
  await page.getByRole('button', { name: '搜索' }).click();
  await expect(page).toHaveURL(/q=%E9%BB%91%E9%B1%BC/);

  const snakeheadCard = page.getByRole('article').filter({
    has: page.getByRole('heading', { name: '乌鳢' }),
  });
  await expect(snakeheadCard.getByRole('link', { name: '查看乌鳢详情' })).toBeVisible();
  await snakeheadCard.getByRole('button', { name: '收藏' }).click();
  await expect(snakeheadCard.getByRole('button', { name: '取消收藏' })).toBeVisible();

  await page.goto('/favorites');
  await expect(page).toHaveURL(/\/favorites$/);
  await expect(page.getByRole('heading', { name: '乌鳢' })).toBeVisible();

  await page.getByRole('button', { name: '取消收藏' }).click();
  await expect(page.getByRole('heading', { name: '还没有收藏鱼类' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '乌鳢' })).toHaveCount(0);

  await page.reload();
  await expect(page.getByRole('heading', { name: '还没有收藏鱼类' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '乌鳢' })).toHaveCount(0);
});
