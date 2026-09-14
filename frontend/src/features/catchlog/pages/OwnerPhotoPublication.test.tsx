import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import { ApiError } from '../../../shared/api/ApiError';
import { deferred } from '../../../test/renderWithProviders';
import { CURRENT_USER_QUERY_KEY } from '../../auth/api/currentUser';
import { clearSessionScopedQueries } from '../../auth/api/sessionCache';
import { ADMIN_PHOTOS_QUERY_KEY } from '../../administration/photos/api/adminPhotoApi';
import { catchDetailQueryKey, catchPageQueryKey } from '../api/catchRecordsApi';
import type { CatchRecordDetail } from '../model/types';
import { CatchDetailPage } from './CatchDetailPage';
import { CatchNewPage } from './CatchNewPage';

const mocks = vi.hoisted(() => ({ fetch: vi.fn(), remove: vi.fn(), put: vi.fn(), create: vi.fn(), fish: vi.fn() }));
vi.mock('../api/catchRecordsApi', async (original) => ({ ...await original(), fetchCatchRecord: mocks.fetch, createCatchRecord: mocks.create }));
vi.mock('../api/catchPhotoApi', async (original) => ({ ...await original(), removeCatchPhoto: mocks.remove, putCatchPhoto: mocks.put }));
vi.mock('../../catalog/api/catalogApi', async (original) => ({ ...await original(), fetchAllPublishedFishOptions: mocks.fish }));

const old: CatchRecordDetail = { id: 31, revision: '7', fishSlug: 'channa-argus', commonNameZh: '乌鳢', caughtOn: '2026-08-20', location: '水库', lengthCm: null, weightG: null, method: null, notes: null, hasPhoto: true, createdAt: '2026-08-20T08:00:00Z', updatedAt: '2026-08-20T08:00:00Z' };
const newer = { ...old, revision: '8', hasPhoto: false };
const preciseNewer = { ...old, revision: '9007199254740993', hasPhoto: true };
const preciseOlder = { ...old, revision: '9007199254740992', hasPhoto: false };
function error(status: number) {
  return new ApiError(status, { code: status === 401 ? 'AUTHENTICATION_REQUIRED' : status === 404 ? 'CATCH_RECORD_NOT_FOUND' : status === 409 ? 'CATCH_PHOTO_CONFLICT' : 'ACCESS_DENIED', message: 'private internal detail', fieldErrors: [], requestId: 'publication-test' });
}
function setup(path = '/catches/31') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryDelay: 0 }, mutations: { retry: false } } });
  client.setQueryData(CURRENT_USER_QUERY_KEY, { id: 1, email: 'owner@example.com', nickname: 'Owner', role: 'USER' });
  const view = render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[path]}>
    <Link to="/catches/32">另一个记录</Link><Link to="/away">离开页面</Link>
    <Routes><Route path="/catches/new" element={<CatchNewPage />} /><Route path="/catches/:id" element={<CatchDetailPage />} /><Route path="/away" element={<h1>已离开</h1>} /><Route path="/login" element={<h1>登录</h1>} /></Routes>
  </MemoryRouter></QueryClientProvider>);
  return { ...view, client, user: userEvent.setup() };
}
async function createWithPhoto(user: ReturnType<typeof userEvent.setup>) {
  await user.selectOptions(await screen.findByLabelText('鱼种'), 'channa-argus');
  await user.type(screen.getByLabelText('钓获日期'), '2026-08-20');
  await user.type(screen.getByLabelText('地点'), '水库');
  await user.upload(screen.getByLabelText('照片（可选）'), new File(['jpeg'], 'photo.jpg', { type: 'image/jpeg' }));
  await user.click(screen.getByRole('button', { name: '保存记录' }));
}
async function removePhoto(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: '移除照片' }));
  await user.click(screen.getByRole('button', { name: '确认移除' }));
}
beforeEach(() => {
  vi.restoreAllMocks(); vi.resetAllMocks();
  mocks.create.mockResolvedValue({ ...old, hasPhoto: false });
  mocks.put.mockRejectedValue(new Error('unavailable'));
  mocks.remove.mockResolvedValue(undefined);
  mocks.fish.mockResolvedValue([{ slug: 'channa-argus', commonNameZh: '乌鳢', scientificName: 'Channa argus', familyNameZh: '鳢科', aliases: [], habitats: [], imagePath: '/fish.jpg', imageAltText: '乌鳢' }]);
});

test.each(['before', 'during'] as const)('owner removal cancels a stale background read started %s its actual-state read', async (timing) => {
  const stale = deferred<CatchRecordDetail>(); const actual = deferred<CatchRecordDetail>();
  mocks.fetch.mockResolvedValueOnce(old);
  const { client, user } = setup();
  await screen.findByRole('button', { name: '移除照片' });
  let background!: Promise<void>;
  if (timing === 'before') {
    mocks.fetch.mockReturnValueOnce(stale.promise).mockReturnValueOnce(actual.promise);
    act(() => { background = client.refetchQueries({ queryKey: catchDetailQueryKey(31), exact: true }); });
  } else mocks.fetch.mockReturnValueOnce(actual.promise).mockReturnValueOnce(stale.promise);
  await removePhoto(user);
  await waitFor(() => expect(mocks.fetch).toHaveBeenCalledTimes(timing === 'before' ? 3 : 2));
  if (timing === 'during') act(() => { background = client.refetchQueries({ queryKey: catchDetailQueryKey(31), exact: true }); });
  await act(async () => { actual.resolve(newer); await actual.promise; });
  await screen.findByText('暂无照片');
  await act(async () => { stale.resolve(old); await background; });
  expect(client.getQueryData(catchDetailQueryKey(31))).toMatchObject({ revision: '8', hasPhoto: false });
  expect(screen.queryByRole('button', { name: '移除照片' })).not.toBeInTheDocument();
  expect(mocks.remove).toHaveBeenCalledTimes(1);
});

test('owner actual-state read preserves a completed newer background revision beyond Number precision', async () => {
  const actual = deferred<CatchRecordDetail>();
  mocks.fetch.mockResolvedValueOnce(old).mockReturnValueOnce(actual.promise).mockResolvedValueOnce(preciseNewer);
  const { client, user } = setup();
  await removePhoto(user);
  await waitFor(() => expect(mocks.fetch).toHaveBeenCalledTimes(2));
  await act(async () => { await client.refetchQueries({ queryKey: catchDetailQueryKey(31), exact: true }); });
  await waitFor(() => expect(screen.getByRole('img')).toHaveAttribute('src', expect.stringContaining('9007199254740993')));
  await act(async () => { actual.resolve(preciseOlder); await actual.promise; });
  await waitFor(() => expect(screen.getByRole('button', { name: '移除照片' })).toBeEnabled());
  expect(client.getQueryData(catchDetailQueryKey(31))).toEqual(preciseNewer);
});

test('unmounted new-record retry review cannot overwrite actual detail after navigation', async () => {
  const review = deferred<CatchRecordDetail>();
  mocks.fetch.mockReturnValueOnce(review.promise).mockResolvedValueOnce(newer);
  const { client, user } = setup('/catches/new');
  await createWithPhoto(user);
  await user.click(await screen.findByRole('button', { name: '重试上传' }));
  await waitFor(() => expect(mocks.fetch).toHaveBeenCalledTimes(1));
  await user.click(screen.getByRole('link', { name: '前往记录详情' }));
  await waitFor(() => expect(client.getQueryData(catchDetailQueryKey(31))).toEqual(newer));
  await act(async () => { review.resolve(old); await review.promise; });
  expect(client.getQueryData(catchDetailQueryKey(31))).toEqual(newer);
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
  expect(mocks.put).toHaveBeenCalledTimes(1);
});

test.each(['retry', 'conflict', 'success'] as const)('new-record %s publisher preserves a completed newer revision', async (path) => {
  const actual = deferred<CatchRecordDetail>();
  mocks.fetch.mockReturnValueOnce(actual.promise);
  if (path === 'conflict') mocks.put.mockRejectedValueOnce(error(409));
  if (path === 'success') mocks.put.mockResolvedValueOnce(undefined);
  const { client, user } = setup('/catches/new');
  await createWithPhoto(user);
  if (path === 'retry') await user.click(await screen.findByRole('button', { name: '重试上传' }));
  await waitFor(() => expect(mocks.fetch).toHaveBeenCalledTimes(1));
  await act(async () => { await client.fetchQuery({ queryKey: catchDetailQueryKey(31), queryFn: async () => preciseNewer }); });
  const publishedRevisions: string[] = [];
  const unsubscribe = client.getQueryCache().subscribe(() => {
    const detail = client.getQueryData<CatchRecordDetail>(catchDetailQueryKey(31));
    if (detail) publishedRevisions.push(detail.revision);
  });
  // Keep the destination's ordinary detail query at the actual newer server state.
  mocks.fetch.mockResolvedValue(preciseNewer);
  await act(async () => { actual.resolve(preciseOlder); await actual.promise; });
  if (path === 'retry') {
    await screen.findByText('当前记录版本：9007199254740993；已有照片');
    await user.click(screen.getByRole('button', { name: '确认上传照片' }));
    await waitFor(() => expect(mocks.put).toHaveBeenLastCalledWith(31, expect.any(File), '9007199254740993'));
  } else if (path === 'success') await screen.findByRole('heading', { name: '乌鳢钓获记录' });
  else await waitFor(() => expect(screen.getByRole('button', { name: '重试上传' })).toBeEnabled());
  expect(client.getQueryData(catchDetailQueryKey(31))).toEqual(preciseNewer);
  // A destination refetch must not hide an earlier transient cache regression.
  expect(publishedRevisions).not.toContain('9007199254740992');
  unsubscribe();
  expect(mocks.put).toHaveBeenCalledTimes(path === 'retry' ? 2 : 1);
});

test.each([401, 403, 404])('owner refresh %s cannot be hidden behind newer retained cache', async (status) => {
  const actual = deferred<CatchRecordDetail>();
  mocks.fetch.mockResolvedValueOnce(old).mockReturnValueOnce(actual.promise);
  const { client, user } = setup();
  await removePhoto(user);
  await waitFor(() => expect(mocks.fetch).toHaveBeenCalledTimes(2));
  act(() => { client.setQueryData(catchDetailQueryKey(31), preciseNewer); });
  await act(async () => { actual.reject(error(status)); });
  await screen.findByRole('heading', { name: status === 401 ? '登录' : status === 404 ? '没有找到钓获记录' : '没有照片访问权限' });
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
  expect(screen.queryByLabelText('钓获照片')).not.toBeInTheDocument();
  expect(client.getQueryData(catchDetailQueryKey(31))).toBeUndefined();
});

test.each([403, 404])('retry authority %s closes private state despite a newer cached revision', async (status) => {
  const actual = deferred<CatchRecordDetail>(); mocks.fetch.mockReturnValueOnce(actual.promise);
  const { client, user } = setup('/catches/new');
  await createWithPhoto(user); await user.click(await screen.findByRole('button', { name: '重试上传' }));
  await waitFor(() => expect(mocks.fetch).toHaveBeenCalledTimes(1));
  act(() => { client.setQueryData(catchDetailQueryKey(31), preciseNewer); });
  await act(async () => { actual.reject(error(status)); });
  await screen.findByRole('heading', { name: status === 404 ? '没有找到钓获记录' : '没有照片访问权限' });
  expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  expect(client.getQueryData(catchDetailQueryKey(31))).toBeUndefined();
});

test.each(['panel', 'new'] as const)('successful %s upload invalidates consumers after its initiating page unmounts', async (path) => {
  const upload = deferred<void>(); mocks.put.mockReturnValueOnce(upload.promise); mocks.fetch.mockResolvedValue(old);
  const { client, user } = setup(path === 'new' ? '/catches/new' : '/catches/31');
  if (path === 'new') await createWithPhoto(user);
  else {
    await user.upload(await screen.findByLabelText('钓获照片'), new File(['jpeg'], 'photo.jpg', { type: 'image/jpeg' }));
    await user.click(screen.getByRole('button', { name: '替换照片' }));
  }
  await waitFor(() => expect(mocks.put).toHaveBeenCalledTimes(1));
  await user.click(screen.getByRole('link', { name: '离开页面' }));
  client.setQueryData(catchPageQueryKey(0), { items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
  client.setQueryData([...ADMIN_PHOTOS_QUERY_KEY, 'list'], { items: [] });
  const readsBefore = mocks.fetch.mock.calls.length;
  await act(async () => { upload.resolve(); await upload.promise; });
  await waitFor(() => expect(client.getQueryState(catchPageQueryKey(0))?.isInvalidated).toBe(true));
  expect(client.getQueryState([...ADMIN_PHOTOS_QUERY_KEY, 'list'])?.isInvalidated).toBe(true);
  expect(client.getQueryState(catchDetailQueryKey(31))?.isInvalidated).toBe(true);
  expect(mocks.fetch).toHaveBeenCalledTimes(readsBefore);
  expect(screen.getByRole('heading', { name: '已离开' })).toBeInTheDocument();
});

test.each(['target', 'session'] as const)('owner pending direct read cannot publish after %s transition', async (transition) => {
  const actual = deferred<CatchRecordDetail>();
  mocks.fetch.mockResolvedValueOnce(old).mockReturnValueOnce(actual.promise).mockResolvedValue({ ...newer, id: 32, location: '新记录' });
  const { client, user } = setup();
  await removePhoto(user); await waitFor(() => expect(mocks.fetch).toHaveBeenCalledTimes(2));
  if (transition === 'target') await user.click(screen.getByRole('link', { name: '另一个记录' }));
  else {
    await user.click(screen.getByRole('link', { name: '离开页面' }));
    act(() => { clearSessionScopedQueries(client); client.setQueryData(CURRENT_USER_QUERY_KEY, { id: 2, email: 'b@example.com', nickname: 'B', role: 'USER' }); });
  }
  const retained = client.getQueryData(catchDetailQueryKey(31));
  await act(async () => { actual.resolve(preciseNewer); await actual.promise; });
  expect(client.getQueryData(catchDetailQueryKey(31))).toEqual(retained);
  if (transition === 'target') expect(await screen.findByText('新记录')).toBeInTheDocument();
  else expect(client.getQueryData(CURRENT_USER_QUERY_KEY)).toMatchObject({ id: 2 });
});

test.each(['retry', 'conflict', 'success'] as const)('new-record %s cancels background reads before and during direct publication', async (path) => {
  const upload = deferred<void>(); const before = deferred<CatchRecordDetail>();
  const during = deferred<CatchRecordDetail>(); const actual = deferred<CatchRecordDetail>();
  mocks.put.mockReturnValueOnce(upload.promise); mocks.fetch.mockReturnValueOnce(actual.promise).mockResolvedValue(newer);
  const { client, user } = setup('/catches/new');
  await createWithPhoto(user);
  await waitFor(() => expect(mocks.put).toHaveBeenCalledTimes(1));
  let beforeRead!: Promise<unknown>;
  act(() => { beforeRead = client.fetchQuery({ queryKey: catchDetailQueryKey(31), queryFn: () => before.promise }).catch(() => undefined); });
  await act(async () => { if (path === 'success') upload.resolve(); else upload.reject(path === 'conflict' ? error(409) : new Error('unavailable')); });
  if (path === 'retry') await user.click(await screen.findByRole('button', { name: '重试上传' }));
  await waitFor(() => expect(mocks.fetch).toHaveBeenCalledTimes(1));
  let duringRead!: Promise<unknown>;
  act(() => { duringRead = client.fetchQuery({ queryKey: catchDetailQueryKey(31), queryFn: () => during.promise }).catch(() => undefined); });
  await act(async () => { actual.resolve(newer); await actual.promise; });
  if (path === 'retry') await screen.findByText('当前记录版本：8；暂无照片');
  else if (path === 'success') await screen.findByRole('heading', { name: '乌鳢钓获记录' });
  else await waitFor(() => expect(screen.getByRole('button', { name: '重试上传' })).toBeEnabled());
  await act(async () => { before.resolve(old); during.resolve(old); await Promise.all([beforeRead, duringRead]); });
  expect(client.getQueryData(catchDetailQueryKey(31))).toEqual(newer);
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
  expect(mocks.put).toHaveBeenCalledTimes(1);
});

test.each(['panel', 'retry'] as const)('%s preserves a newer revision observed before its direct read starts', async (path) => {
  const stale = deferred<CatchRecordDetail>();
  mocks.fetch.mockResolvedValueOnce(path === 'panel' ? old : preciseOlder).mockResolvedValue(preciseOlder);
  const { client, user } = setup(path === 'panel' ? '/catches/31' : '/catches/new');
  if (path === 'panel') await screen.findByRole('button', { name: '移除照片' });
  else await createWithPhoto(user);
  let background!: Promise<unknown>;
  act(() => { background = client.fetchQuery({ queryKey: catchDetailQueryKey(31), queryFn: () => stale.promise }).catch(() => undefined); });
  act(() => { client.setQueryData(catchDetailQueryKey(31), preciseNewer); });
  if (path === 'panel') await removePhoto(user);
  else await user.click(await screen.findByRole('button', { name: '重试上传' }));
  await waitFor(() => expect(path === 'panel' ? screen.getByLabelText('钓获照片') : screen.getByRole('button', { name: '重试上传' })).toBeEnabled());
  await act(async () => { stale.resolve(old); await background; });
  expect(client.getQueryData(catchDetailQueryKey(31))).toEqual(preciseNewer);
  if (path === 'retry') expect(screen.getByText('当前记录版本：9007199254740993；已有照片')).toBeInTheDocument();
});

test.each([
  ['panel', 1, 'mount'], ['panel', 2, 'mount'], ['panel', 1, 'session'], ['panel', 2, 'session'],
  ['retry', 1, 'mount'], ['retry', 2, 'mount'], ['retry', 1, 'session'], ['retry', 2, 'session'],
] as const)('%s cancellation %s rechecks the %s boundary before reading or publishing', async (path, phase, transition) => {
    const cancellation = deferred<void>();
    mocks.fetch.mockReset().mockResolvedValueOnce(path === 'panel' ? old : newer).mockResolvedValue(newer);
    mocks.put.mockRejectedValue(new Error('unavailable'));
    const { client, user, unmount } = setup(path === 'panel' ? '/catches/31' : '/catches/new');
    if (path === 'panel') await screen.findByRole('button', { name: '移除照片' }); else await createWithPhoto(user);
    const cancel = client.cancelQueries.bind(client); let calls = 0;
    vi.spyOn(client, 'cancelQueries').mockImplementation(async (...args) => { await cancel(...args); if (++calls === phase) await cancellation.promise; });
    if (path === 'panel') await removePhoto(user); else await user.click(await screen.findByRole('button', { name: '重试上传' }));
    await waitFor(() => expect(calls).toBe(phase));
    if (transition === 'mount') await user.click(screen.getByRole('link', { name: '离开页面' }));
    else act(() => { clearSessionScopedQueries(client); client.setQueryData(CURRENT_USER_QUERY_KEY, { id: 2, email: 'b@example.com', nickname: 'B', role: 'USER' }); });
    const retained = client.getQueryData(catchDetailQueryKey(31));
    const reads = mocks.fetch.mock.calls.length;
    await act(async () => { cancellation.resolve(); await cancellation.promise; });
    expect(client.getQueryData(catchDetailQueryKey(31))).toEqual(retained);
    expect(mocks.fetch).toHaveBeenCalledTimes(reads);
    if (transition === 'mount') expect(screen.getByRole('heading', { name: '已离开' })).toBeInTheDocument();
    else expect(client.getQueryData(CURRENT_USER_QUERY_KEY)).toMatchObject({ id: 2 });
    unmount();
});

test('mounted create publication keeps an observed newer revision and only uploads with the original submitted revision', async () => {
  const creating = deferred<CatchRecordDetail>(); mocks.create.mockReturnValueOnce(creating.promise);
  const { client, user } = setup('/catches/new');
  await createWithPhoto(user);
  client.setQueryData(catchDetailQueryKey(31), preciseNewer);
  await act(async () => { creating.resolve({ ...old, hasPhoto: false }); await creating.promise; });
  await screen.findByText('记录已保存，照片未上传');
  expect(client.getQueryData(catchDetailQueryKey(31))).toEqual(preciseNewer);
  expect(mocks.put).toHaveBeenCalledExactlyOnceWith(31, expect.any(File), '7');
  expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
});

test.each(['conflict', 'success'] as const)('unmounted %s refresh cannot publish into the destination cache', async (path) => {
  const actual = deferred<CatchRecordDetail>();
  mocks.put.mockImplementationOnce(() => path === 'success' ? Promise.resolve() : Promise.reject(error(409)));
  mocks.fetch.mockReturnValueOnce(actual.promise).mockResolvedValue({ ...newer, id: 32, location: '新记录' });
  const { client, user } = setup('/catches/new');
  await createWithPhoto(user); await waitFor(() => expect(mocks.fetch).toHaveBeenCalledTimes(1));
  await user.click(screen.getByRole('link', { name: '另一个记录' }));
  await screen.findByText('新记录');
  const retained = client.getQueryData(catchDetailQueryKey(31));
  await act(async () => { actual.resolve(preciseNewer); await actual.promise; });
  expect(client.getQueryData(catchDetailQueryKey(31))).toEqual(retained);
  expect(screen.getByText('新记录')).toBeInTheDocument();
  expect(mocks.put).toHaveBeenCalledTimes(1);
});

test('create response cannot overwrite newer detail observed while create was pending or navigate after unmount', async () => {
  const creating = deferred<CatchRecordDetail>(); mocks.create.mockReturnValueOnce(creating.promise);
  mocks.fetch.mockResolvedValue(preciseNewer);
  const { client, user } = setup('/catches/new');
  await createWithPhoto(user);
  await user.click(screen.getByRole('link', { name: '另一个记录' }));
  await screen.findByRole('heading', { name: '乌鳢钓获记录' });
  client.setQueryData(catchDetailQueryKey(31), preciseNewer);
  await act(async () => { creating.resolve({ ...old, hasPhoto: false }); await creating.promise; });
  expect(client.getQueryData(catchDetailQueryKey(31))).toEqual(preciseNewer);
  expect(mocks.put).not.toHaveBeenCalled();
});
