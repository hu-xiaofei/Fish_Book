import { expect, test, type Page } from '@playwright/test';

async function fillValidFishForm(page: Page, fish: { slug: string; name: string }) {
  await page.getByLabel('Slug').fill(fish.slug);
  await page.getByLabel('中文名', { exact: true }).fill(fish.name);
  await page.getByLabel('学名').fill(`Administrichthys testensis ${fish.slug}`);
  await page.getByLabel('别名（每行一个）').fill(`${fish.name}别名`);
  await page.getByLabel('湖泊').check();

  await page.getByLabel('科中文名').fill('后台测试科');
  await page.getByLabel('科拉丁名').fill('Administridae');
  await page.getByLabel('属中文名').fill('后台测试属');
  await page.getByLabel('属拉丁名').fill('Administrichthys');

  await page.getByLabel('外形').fill('用于管理员浏览器验收流程的外形说明。');
  await page.getByLabel('体型').fill('用于管理员浏览器验收流程的体型说明。');
  await page.getByLabel('栖息环境').fill('用于管理员浏览器验收流程的栖息环境说明。');
  await page.getByLabel('分布').fill('用于管理员浏览器验收流程的分布说明。');
  await page.getByLabel('介绍').fill('用于管理员浏览器验收流程的完整鱼类介绍。');

  await page.getByLabel('图片路径').fill('/images/fish/cyprinus-carpio.jpg');
  await page.getByLabel('图片替代文字').fill(`${fish.name}测试图片`);
  await page.getByLabel('图片来源链接').fill('https://commons.wikimedia.org/wiki/File:Cyprinus_carpio.jpg');
  await page.getByLabel('图片作者').fill('FishBook browser acceptance test');
  await page.getByLabel('图片许可名称').fill('CC BY-SA 4.0');
  await page.getByLabel('图片许可链接').fill('https://creativecommons.org/licenses/by-sa/4.0/');
  await page.getByLabel('显示顺序').fill('1000');
}

test('administrator publishes and unpublishes a fish while public visibility follows', async ({ browser, baseURL }) => {
  if (!baseURL) throw new Error('Playwright baseURL is required');
  const unique = Date.now();
  const slug = `admin-flow-fish-${unique}`;
  const name = `后台测试鱼${unique}`;

  const adminContext = await browser.newContext({ baseURL });
  const visitorContext = await browser.newContext({ baseURL });
  try {
    const admin = await adminContext.newPage();
    await admin.goto('/login');
    await admin.getByLabel('邮箱').fill('admin@fishbook.local');
    await admin.getByLabel('密码').fill('fishbook_admin_local_only');
    await admin.getByRole('button', { name: '登录' }).click();
    await admin.getByRole('link', { name: '图鉴管理' }).click();
    await admin.getByRole('link', { name: '新建鱼类' }).click();

    await fillValidFishForm(admin, { slug, name });
    await admin.getByRole('button', { name: '保存草稿' }).click();
    await expect(admin.getByText('草稿已保存')).toBeVisible();

    const visitor = await visitorContext.newPage();
    await visitor.goto(`/?q=${encodeURIComponent(name)}`);
    await expect(visitor.getByText(name, { exact: true })).toHaveCount(0);
    await visitor.goto(`/fish/${slug}`);
    await expect(visitor.getByRole('heading', { name: '没有找到这种鱼' })).toBeVisible();

    await admin.getByRole('button', { name: '发布' }).click();
    await expect(admin.getByRole('button', { name: '下架' })).toBeVisible();
    await visitor.goto(`/?q=${encodeURIComponent(name)}`);
    await expect(visitor.getByText(name, { exact: true })).toBeVisible();
    await visitor.goto(`/fish/${slug}`);
    await expect(visitor.getByRole('heading', { name })).toBeVisible();

    admin.once('dialog', (dialog) => dialog.accept());
    await admin.getByRole('button', { name: '下架' }).click();
    await expect(admin.getByRole('button', { name: '发布' })).toBeVisible();
    await visitor.goto(`/?q=${encodeURIComponent(name)}`);
    await expect(visitor.getByText(name, { exact: true })).toHaveCount(0);
    await visitor.goto(`/fish/${slug}`);
    await expect(visitor.getByRole('heading', { name: '没有找到这种鱼' })).toBeVisible();
  } finally {
    await visitorContext.close();
    await adminContext.close();
  }
});

test('ordinary user sees the local forbidden page and receives 403 from the admin API', async ({ browser, baseURL }) => {
  if (!baseURL) throw new Error('Playwright baseURL is required');
  const unique = Date.now();
  const email = `admin-denied-${unique}@example.com`;
  const ordinaryContext = await browser.newContext({ baseURL });
  try {
    const ordinary = await ordinaryContext.newPage();
    await ordinary.goto('/register');
    await ordinary.getByLabel('邮箱').fill(email);
    await ordinary.getByLabel('密码').fill('strong-pass');
    await ordinary.getByLabel('昵称').fill(`普通用户${unique}`);
    await ordinary.getByRole('button', { name: '注册' }).click();
    await expect(ordinary.getByText('注册成功，请登录')).toBeVisible();

    await ordinary.getByLabel('邮箱').fill(email);
    await ordinary.getByLabel('密码').fill('strong-pass');
    await ordinary.getByRole('button', { name: '登录' }).click();
    await expect(ordinary).toHaveURL(/\/profile$/);
    await ordinary.goto('/admin/fishes');
    await expect(ordinary.getByRole('heading', { name: '没有管理员权限' })).toBeVisible();

    const adminApiStatus = await ordinary.evaluate(async () => (
      await fetch('/api/v1/admin/fishes')
    ).status);
    expect(adminApiStatus).toBe(403);
  } finally {
    await ordinaryContext.close();
  }
});
