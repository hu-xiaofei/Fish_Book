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

  return email;
}

async function loginForCatches(page: Page, email: string) {
  await page.goto('/login');
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

test('creates a private catch record from catalog-selected fish details', async ({ page }) => {
  await registerAndLoginForCatches(page);

  await page.getByRole('link', { name: '钓获记录' }).click();
  await page.getByRole('link', { name: '记录第一次钓获' }).click();
  await expect(page).toHaveURL(/\/catches\/new$/);

  await page.getByLabel('鱼种').selectOption('channa-argus');
  await page.getByLabel('钓获日期').fill('2026-08-20');
  await page.getByLabel('地点').fill('城郊水库');
  await page.getByRole('button', { name: '保存记录' }).click();

  await expect(page).toHaveURL(/\/catches\/\d+$/);
});

test('edits then deletes a private catch record and keeps it hidden from another account', async ({ page }) => {
  const firstEmail = await registerAndLoginForCatches(page);

  await page.getByRole('link', { name: '钓获记录' }).click();
  await page.getByRole('link', { name: '记录第一次钓获' }).click();
  await page.getByLabel('鱼种').selectOption('channa-argus');
  await page.getByLabel('钓获日期').fill('2026-08-20');
  await page.getByLabel('地点').fill('城郊水库');
  await page.getByRole('button', { name: '保存记录' }).click();
  await expect(page.getByRole('heading', { name: '乌鳢钓获记录' })).toBeVisible();
  const privateRecordPath = new URL(page.url()).pathname;

  await page.getByRole('link', { name: '编辑记录' }).click();
  await page.getByLabel('地点').fill('河湾');
  await page.getByRole('button', { name: '保存修改' }).click();
  await expect(page.getByText('河湾')).toBeVisible();

  await page.getByRole('button', { name: '退出登录' }).click();
  const secondEmail = await registerAndLoginForCatches(page);
  expect(secondEmail).not.toBe(firstEmail);
  await page.goto(privateRecordPath);
  await expect(page.getByRole('heading', { name: '没有找到钓获记录' })).toBeVisible();

  await page.getByRole('link', { name: '返回钓获记录' }).click();
  await page.getByRole('button', { name: '退出登录' }).click();
  await loginForCatches(page, firstEmail);
  await page.goto(privateRecordPath);
  await expect(page.getByRole('heading', { name: '乌鳢钓获记录' })).toBeVisible();
  await page.getByRole('button', { name: '删除记录' }).click();
  await expect(page.getByRole('alertdialog', { name: '确认删除钓获记录' })).toBeVisible();
  await page.getByRole('button', { name: '确认删除' }).click();
  await expect(page).toHaveURL(/\/catches$/);
  await expect(page.getByRole('heading', { name: '还没有钓获记录' })).toBeVisible();
});
