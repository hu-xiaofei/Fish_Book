import { expect, test } from '@playwright/test';

test('registers, restores the session, edits profile, and logs out', async ({ page }) => {
  const email = `angler-${Date.now()}@example.com`;

  await page.goto('/register');
  await page.getByLabel('邮箱').fill(email);
  await page.getByLabel('密码').fill('strong-pass');
  await page.getByLabel('昵称').fill('Wall_E');
  await page.getByRole('button', { name: '注册' }).click();
  await expect(page.getByText('注册成功，请登录')).toBeVisible();

  await page.getByLabel('邮箱').fill(email);
  await page.getByLabel('密码').fill('strong-pass');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/profile$/);
  await expect(page.getByText(email)).toBeVisible();

  await page.getByLabel('昵称').fill('River');
  const updateResponsePromise = page.waitForResponse((response) => (
    response.url().endsWith('/api/v1/me')
      && response.request().method() === 'PATCH'
  ));
  await page.getByRole('button', { name: '保存' }).click();
  const updateResponse = await updateResponsePromise;
  expect(updateResponse.status()).toBe(200);
  await expect(page.getByLabel('昵称')).toHaveValue('River');

  await page.reload();
  await expect(page.getByLabel('昵称')).toHaveValue('River');

  await page.getByRole('button', { name: '退出登录' }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.goto('/profile');
  await expect(page).toHaveURL(/\/login\?returnTo=%2Fprofile$/);
});
