import { expect, test, type Page } from '@playwright/test';

async function registerAndLoginForCatches(page: Page) {
  const uniqueId = `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  const email = `catches-${uniqueId}@example.com`;

  await page.goto('/register');
  await page.getByLabel('邮箱').fill(email);
  await page.getByLabel('密码').fill('strong-pass');
  await page.getByLabel('昵称').fill('Catch Angler');
  await page.getByRole('button', { name: '注册' }).click();
  await expect(page.getByRole('status')).toHaveText('注册成功，请登录');

  await page.getByLabel('邮箱').fill(email);
  await page.getByLabel('密码').fill('strong-pass');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/profile$/);
}

test('opens an empty private catch list from authenticated navigation', async ({ page }) => {
  await registerAndLoginForCatches(page);

  await page.getByRole('link', { name: '钓获记录' }).click();

  await expect(page).toHaveURL(/\/catches$/);
  await expect(page.getByRole('heading', { name: '还没有钓获记录' })).toBeVisible();
  await expect(page.getByRole('link', { name: '记录第一次钓获' })).toBeVisible();
});
