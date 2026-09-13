import { expect, test, type Page } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';

const firstPhoto = path.resolve(process.cwd(), '../frontend/public/images/fish/channa-argus.jpg');
const replacementPhoto = path.resolve(process.cwd(), '../frontend/public/images/fish/cyprinus-carpio.jpg');
const conflictMessage = '照片或记录已被修改，请刷新后重新确认操作';

async function login(page: Page, email: string, password = 'strong-pass') {
  await page.goto('/login');
  await page.getByLabel('邮箱').fill(email);
  await page.getByLabel('密码').fill(password);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/profile$/);
}

async function register(page: Page, prefix: string) {
  const email = `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2)}@example.com`;
  await page.goto('/register');
  await page.getByLabel('邮箱').fill(email);
  await page.getByLabel('密码').fill('strong-pass');
  await page.getByLabel('昵称').fill(prefix);
  await page.getByRole('button', { name: '注册' }).click();
  await expect(page.getByRole('status')).toHaveText('注册成功，请登录');
  await login(page, email);
  return email;
}

// Trusted test-only evidence: explicitly opt in to a separately named disposable
// Compose project. Never infer credentials from a real .env or inspect fishbook.
function auditRows(recordId: string) {
  const project = process.env.FISHBOOK_E2E_DISPOSABLE_PROJECT;
  if (!project || !/^fishbook-admin-photo-acceptance-[a-z0-9-]+$/.test(project)) {
    throw new Error('Set FISHBOOK_E2E_DISPOSABLE_PROJECT to the disposable acceptance project');
  }
  if (!/^\d+$/.test(recordId)) throw new Error('Invalid fixture record ID');
  const container = `${project}-mysql-1`;
  const label = execFileSync('docker', ['inspect', '--format', '{{ index .Config.Labels "com.docker.compose.project" }}', container], { encoding: 'utf8' }).trim();
  if (label !== project) throw new Error('Disposable database project label mismatch');
  const sql = `SELECT actor_user_id, owner_user_id, catch_record_id, operation, previous_version FROM admin_photo_operations WHERE catch_record_id = ${recordId} ORDER BY id`;
  return execFileSync('docker', ['exec', container, 'mysql', '--user=fishbook', '--password=fishbook_local_only', '--database=fishbook', '--batch', '--skip-column-names', '--execute', sql], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim().split('\n').filter(Boolean).map((row) => row.split('\t'));
}

function verifyDisposableTarget(baseURL: string | undefined) {
  const project = process.env.FISHBOOK_E2E_DISPOSABLE_PROJECT;
  if (!project || !/^fishbook-admin-photo-acceptance-[a-z0-9-]+$/.test(project)) {
    throw new Error('A separately named disposable acceptance project is required');
  }
  const url = new URL(baseURL ?? '');
  if (url.protocol !== 'http:' || url.hostname !== '127.0.0.1') {
    throw new Error('Acceptance must use the disposable frontend on IPv4 loopback');
  }
  const container = JSON.parse(execFileSync('docker', ['inspect', `${project}-frontend-1`], { encoding: 'utf8' }))[0];
  expect(container.Config.Labels['com.docker.compose.project']).toBe(project);
  expect(container.NetworkSettings.Ports['8080/tcp']).toEqual([{ HostIp: '127.0.0.1', HostPort: url.port }]);
}

test('administrator manages a private owner photo with conflicts, audit evidence, and session isolation', async ({ browser, baseURL }, testInfo) => {
  test.setTimeout(90_000);
  // A missing disposable fixture is a failure, never a skipped acceptance pass.
  verifyDisposableTarget(baseURL);
  const ownerContext = await browser.newContext({ baseURL });
  const adminContext = await browser.newContext({ baseURL });
  const strangerContext = await browser.newContext({ baseURL });
  const anonymousContext = await browser.newContext({ baseURL });
  try {
    const owner = await ownerContext.newPage();
    const admin = await adminContext.newPage();
    const stranger = await strangerContext.newPage();
    await register(owner, 'admin-photo-owner');
    const strangerEmail = await register(stranger, 'admin-photo-stranger');
    await login(admin, 'admin@fishbook.local', 'fishbook_admin_local_only');
    const ownerId = (await (await owner.request.get('/api/v1/me')).json()).id;
    const adminId = (await (await admin.request.get('/api/v1/me')).json()).id;
    expect(adminId).not.toBe(ownerId);

    await owner.goto('/catches/new');
    await owner.getByLabel('鱼种').selectOption('channa-argus');
    await owner.getByLabel('钓获日期').fill('2026-08-20');
    await owner.getByLabel('地点').fill('仅所有者可见的测试钓点');
    await owner.getByLabel('照片（可选）', { exact: true }).setInputFiles(firstPhoto);
    await owner.getByRole('button', { name: '保存记录' }).click();
    await expect(owner).toHaveURL(/\/catches\/\d+$/);
    const recordId = new URL(owner.url()).pathname.split('/').at(-1)!;
    const ownerApi = `/api/v1/catches/${recordId}`;
    const adminApi = `/api/v1/admin/photos/${recordId}`;
    const ownerBefore = await (await owner.request.get(ownerApi)).json();
    const original = readFileSync(firstPhoto);
    const replacement = readFileSync(replacementPhoto);
    await expect(owner.getByRole('img', { name: '乌鳢钓获照片' })).toBeVisible();
    expect(auditRows(recordId)).toEqual([]);

    await admin.getByRole('link', { name: '照片管理', exact: true }).click();
    await admin.getByLabel('所属用户 ID').fill(String(ownerId));
    await admin.getByRole('button', { name: '筛选', exact: true }).click();
    await expect(admin).toHaveURL(new RegExp(`userId=${ownerId}`));
    const imageRead = admin.waitForResponse((r) => r.url().includes(`${adminApi}/content?`) && r.request().method() === 'GET');
    await admin.getByRole('link', { name: `查看照片 ${recordId}`, exact: true }).click();
    const actualImage = await imageRead;
    expect(actualImage.status()).toBe(200);
    expect(actualImage.headers()['cache-control']).toBe('private, no-store');
    expect(actualImage.headers().etag).toBe(`"${ownerBefore.revision}"`);
    expect(await actualImage.body()).toEqual(original);
    await expect(admin.getByRole('img', { name: '乌鳢钓获照片' })).toBeVisible();
    await expect(admin.getByText('仅所有者可见的测试钓点')).toHaveCount(0);
    for (const suffix of ['', '/operations']) {
      const response = await admin.request.get(`${adminApi}${suffix}`);
      expect(response.headers()['cache-control']).toBe('private, no-store');
    }

    let adminWrites = 0;
    admin.on('request', (r) => { if (r.url().includes(`${adminApi}/content`) && ['PUT', 'DELETE'].includes(r.method())) adminWrites += 1; });
    await admin.getByLabel('新照片', { exact: true }).setInputFiles(replacementPhoto);
    await expect(admin.getByRole('img', { name: '新照片预览' })).toBeVisible();
    await admin.getByRole('button', { name: '替换照片', exact: true }).click();
    await admin.getByRole('button', { name: '取消', exact: true }).click();
    await expect(admin.getByRole('alertdialog')).toHaveCount(0);
    expect(adminWrites).toBe(0);
    expect(await (await owner.request.get(`${ownerApi}/photo`)).body()).toEqual(original);
    await admin.getByRole('button', { name: '替换照片', exact: true }).click();
    const replaceResponse = admin.waitForResponse((r) => r.url().includes(`${adminApi}/content`) && r.request().method() === 'PUT');
    await admin.getByRole('button', { name: '确认替换', exact: true }).click();
    const replaced = await replaceResponse;
    expect(replaced.status()).toBe(204);
    expect(replaced.request().headers()['if-match']).toBe(`"${ownerBefore.revision}"`);
    await expect(admin.getByRole('img', { name: '新照片预览' })).toHaveCount(0);
    const ownerImageRead = owner.waitForResponse((r) => r.url().includes(`${ownerApi}/photo?`) && r.request().method() === 'GET');
    await owner.reload();
    const refreshedImage = await ownerImageRead;
    expect(refreshedImage.headers()['cache-control']).toBe('private, no-store');
    expect(await refreshedImage.body()).toEqual(replacement);
    const afterAdmin = await (await owner.request.get(ownerApi)).json();
    expect(afterAdmin.revision).not.toBe(ownerBefore.revision);

    // Both open tabs reviewed afterAdmin. Owner wins first; stale admin confirms
    // once and must observe 409, refresh metadata, clear its file, and not retry.
    await admin.getByLabel('新照片', { exact: true }).setInputFiles(replacementPhoto);
    await admin.getByRole('button', { name: '替换照片', exact: true }).click();
    await owner.getByLabel('钓获照片', { exact: true }).setInputFiles(firstPhoto);
    const ownerWrite = owner.waitForResponse((r) => r.url().includes(`${ownerApi}/photo`) && r.request().method() === 'PUT');
    await owner.getByRole('button', { name: '替换照片', exact: true }).click();
    expect((await ownerWrite).status()).toBe(204);
    const conflict = admin.waitForResponse((r) => r.url().includes(`${adminApi}/content`) && r.request().method() === 'PUT');
    await admin.getByRole('button', { name: '确认替换', exact: true }).click();
    const rejected = await conflict;
    expect(rejected.status()).toBe(409);
    expect(await rejected.json()).toMatchObject({ code: 'CATCH_PHOTO_CONFLICT' });
    await expect(admin.getByText(conflictMessage, { exact: true })).toBeVisible();
    await expect(admin.getByRole('alertdialog')).toHaveCount(0);
    await expect(admin.getByLabel('新照片', { exact: true })).toBeEnabled();
    await expect(admin.getByLabel('新照片', { exact: true })).toHaveValue('');
    await expect(admin.getByRole('button', { name: '替换照片', exact: true })).toBeDisabled();
    expect(adminWrites).toBe(2);
    expect(await (await owner.request.get(`${ownerApi}/photo`)).body()).toEqual(original);
    const winner = await (await owner.request.get(ownerApi)).json();
    expect(auditRows(recordId)).toEqual([[String(adminId), String(ownerId), recordId, 'REPLACED', ownerBefore.revision]]);

    await stranger.goto(`/admin/photos/${recordId}`);
    await expect(stranger.getByRole('heading', { name: '没有管理员权限' })).toBeVisible();
    for (const url of ['/api/v1/admin/photos', adminApi, `${adminApi}/content`, `${adminApi}/operations`]) {
      expect((await stranger.request.get(url)).status()).toBe(403);
    }
    expect((await stranger.request.get(`${ownerApi}/photo`)).status()).toBe(404);
    expect((await admin.request.get(`${ownerApi}/photo`)).status()).toBe(404);
    expect((await anonymousContext.request.get(`${ownerApi}/photo`)).status()).toBe(401);
    expect((await anonymousContext.request.get(`${adminApi}/content`)).status()).toBe(401);
    expect(adminWrites).toBe(2); // No retry during independent browser/API checks.

    await admin.getByRole('button', { name: '删除照片', exact: true }).click();
    await expect(admin.getByRole('alertdialog', { name: '确认删除照片' })).toBeVisible();
    const removeResponse = admin.waitForResponse((r) => r.url().includes(`${adminApi}/content`) && r.request().method() === 'DELETE');
    await admin.getByRole('button', { name: '确认删除', exact: true }).click();
    expect((await removeResponse).status()).toBe(204);
    await expect(admin.getByText('暂无照片', { exact: true })).toBeVisible();
    await owner.reload();
    await expect(owner.getByText('暂无照片', { exact: true })).toBeVisible();
    const afterDelete = await (await owner.request.get(ownerApi)).json();
    const { revision: _oldRevision, hasPhoto: _oldPhoto, updatedAt: _oldTime, ...beforeFields } = ownerBefore;
    const { revision: _newRevision, hasPhoto: _newPhoto, updatedAt: _newTime, ...afterFields } = afterDelete;
    expect(afterFields).toEqual(beforeFields);
    expect(afterDelete.hasPhoto).toBe(false);
    expect((await owner.request.get(`${ownerApi}/photo`)).status()).toBe(404);
    const evidence = auditRows(recordId);
    expect(evidence).toEqual([
      [String(adminId), String(ownerId), recordId, 'REPLACED', ownerBefore.revision],
      [String(adminId), String(ownerId), recordId, 'REMOVED', winner.revision],
    ]);
    await testInfo.attach('trusted-disposable-db-audit', { body: JSON.stringify({ recordId, ownerId, adminId, operations: evidence }), contentType: 'application/json' });

    // Restore a photo as the owner only to exercise logout with a live original
    // and a blob preview. This creates no administrator success audit row.
    await owner.getByLabel('钓获照片', { exact: true }).setInputFiles(firstPhoto);
    const upload = owner.waitForResponse((r) => r.url().includes(`${ownerApi}/photo`) && r.request().method() === 'PUT');
    await owner.getByRole('button', { name: '上传照片', exact: true }).click();
    expect((await upload).status()).toBe(204);
    await admin.reload();
    await admin.getByLabel('新照片', { exact: true }).setInputFiles(replacementPhoto);
    await expect(admin.getByRole('img', { name: '新照片预览' })).toBeVisible();
    await admin.getByRole('button', { name: '退出登录', exact: true }).click();
    await expect(admin).toHaveURL(/\/login$/);
    await expect(admin.getByRole('img')).toHaveCount(0);
    await expect(admin.getByRole('heading', { name: '近期管理员操作' })).toHaveCount(0);
    await login(admin, strangerEmail);
    await admin.goto(`/admin/photos/${recordId}`);
    await expect(admin.getByRole('heading', { name: '没有管理员权限' })).toBeVisible();
    await expect(admin.getByRole('img')).toHaveCount(0);
    await expect(admin.getByLabel('新照片', { exact: true })).toHaveCount(0);
    expect((await admin.request.get(`${adminApi}/content`)).status()).toBe(403);
    expect(auditRows(recordId)).toEqual(evidence);
  } finally {
    await Promise.all([ownerContext.close(), adminContext.close(), strangerContext.close(), anonymousContext.close()]);
  }
});
