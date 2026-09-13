import { act, fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { Link } from 'react-router-dom';
import { clearSessionScopedQueries } from '../../../auth/api/sessionCache';
import { deferred } from '../../../../test/renderWithProviders';
import { adminPhotoDetailQueryKey, fetchAdminPhoto, fetchAdminPhotoOperations, removeAdminPhoto } from '../api/adminPhotoApi';
import { apiError, photo, renderAdmin } from '../test/helpers';
import { AdminPhotoDetailPage } from './AdminPhotoDetailPage';

vi.mock('../api/adminPhotoApi', async (original) => ({ ...await original<typeof import('../api/adminPhotoApi')>(), fetchAdminPhoto: vi.fn(), fetchAdminPhotoOperations: vi.fn(), removeAdminPhoto: vi.fn() }));
vi.mock('../../../auth/components/SessionNav', () => ({ SessionNav: () => null }));
beforeEach(() => {
  vi.mocked(fetchAdminPhoto).mockReset().mockResolvedValue(photo);
  vi.mocked(fetchAdminPhotoOperations).mockReset().mockResolvedValue({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
  vi.mocked(removeAdminPhoto).mockReset().mockResolvedValue(undefined);
});
afterEach(() => { vi.unstubAllGlobals(); });

test('uses private revision image route and minimal metadata; failed image does not hide evidence', async () => {
  const { user } = renderAdmin(<AdminPhotoDetailPage />, '/admin/photos/31', '/admin/photos/:id');
  const image = await screen.findByRole('img', { name: '鲫鱼钓获照片' });
  expect(image).toHaveAttribute('src', '/api/v1/admin/photos/31/content?revision=7&reload=0');
  expect(screen.getByText('河边钓友')).toBeVisible();
  fireEvent.error(image);
  expect(screen.getByText('照片暂时无法显示')).toBeVisible();
  await waitFor(() => expect(fetchAdminPhoto).toHaveBeenCalledTimes(2));
  expect(await screen.findByText('暂无管理员操作记录')).toBeVisible();
  await user.click(screen.getByRole('button', { name: '重新加载照片' }));
  expect(screen.getByRole('img')).toHaveAttribute('src', '/api/v1/admin/photos/31/content?revision=7&reload=1');
});

test.each([401, 403])('image-only status %s is caught by one authority recheck and releases selected local preview', async (status) => {
  const revoke = vi.fn();
  vi.stubGlobal('URL', class extends URL { static createObjectURL = () => 'blob:private-local'; static revokeObjectURL = revoke; });
  vi.mocked(fetchAdminPhoto).mockResolvedValueOnce(photo).mockRejectedValue(apiError(status));
  const { user, queryClient } = renderAdmin(<AdminPhotoDetailPage />, '/admin/photos/31', '/admin/photos/:id');
  const original = await screen.findByRole('img', { name: '鲫鱼钓获照片' });
  await user.upload(screen.getByLabelText('新照片'), new File(['jpeg'], 'new.jpg', { type: 'image/jpeg' }));
  expect(screen.getByRole('img', { name: '新照片预览' })).toBeVisible();
  fireEvent.error(original);
  expect(await screen.findByRole('heading', { name: status === 401 ? '登录页' : '没有管理员权限' })).toBeVisible();
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
  expect(screen.queryByLabelText('新照片')).not.toBeInTheDocument();
  expect(revoke).toHaveBeenCalledWith('blob:private-local');
  expect(fetchAdminPhoto).toHaveBeenCalledTimes(2);
  expect(queryClient.getQueriesData({ queryKey: ['admin-photos'] })).toEqual([]);
});

test('non-auth authority recheck failure retains broken-image fallback and independent evidence without a read loop', async () => {
  vi.mocked(fetchAdminPhoto).mockResolvedValueOnce(photo).mockRejectedValue(apiError(503));
  renderAdmin(<AdminPhotoDetailPage />, '/admin/photos/31', '/admin/photos/:id');
  fireEvent.error(await screen.findByRole('img'));
  expect(await screen.findByText('暂无管理员操作记录')).toBeVisible();
  await waitFor(() => expect(fetchAdminPhoto).toHaveBeenCalledTimes(2));
  expect(screen.getByText('照片暂时无法显示')).toBeVisible();
  expect(screen.getByLabelText('新照片')).toBeEnabled();
  expect(screen.queryByText('internal-private-key')).not.toBeInTheDocument();
});

test('late authority recheck after logout cannot restore private data', async () => {
  const response = deferred<typeof photo>();
  vi.mocked(fetchAdminPhoto).mockResolvedValueOnce(photo).mockReturnValue(response.promise);
  const { queryClient } = renderAdmin(<AdminPhotoDetailPage />, '/admin/photos/31', '/admin/photos/:id');
  fireEvent.error(await screen.findByRole('img'));
  await waitFor(() => expect(fetchAdminPhoto).toHaveBeenCalledTimes(2));
  act(() => clearSessionScopedQueries(queryClient));
  await act(async () => { response.resolve(photo); });
  expect(await screen.findByRole('heading', { name: '登录页' })).toBeVisible();
  expect(queryClient.getQueriesData({ queryKey: ['admin-photos'] })).toEqual([]);
});

test('late authority denial for the previous record cannot hide the newly selected target', async () => {
  const response = deferred<typeof photo>();
  vi.mocked(fetchAdminPhoto).mockResolvedValueOnce(photo).mockReturnValueOnce(response.promise).mockResolvedValue({ ...photo, recordId: 32, ownerNickname: '另一位钓友' });
  const { user } = renderAdmin(<><AdminPhotoDetailPage /><Link to="/admin/photos/32">切换目标</Link></>, '/admin/photos/31', '/admin/photos/:id');
  fireEvent.error(await screen.findByRole('img'));
  await waitFor(() => expect(fetchAdminPhoto).toHaveBeenCalledTimes(2));
  await user.click(screen.getByRole('link', { name: '切换目标' }));
  expect(await screen.findByText('另一位钓友')).toBeVisible();
  await act(async () => { response.reject(apiError(403)); });
  expect(screen.getByText('另一位钓友')).toBeVisible();
  expect(screen.queryByRole('heading', { name: '没有管理员权限' })).not.toBeInTheDocument();
  expect(screen.getByRole('img')).toHaveAttribute('src', '/api/v1/admin/photos/32/content?revision=7&reload=0');
});

test('removed photo detail retains metadata and evidence without upload capability', async () => {
  vi.mocked(fetchAdminPhoto).mockResolvedValue({ ...photo, hasPhoto: false, revision: '8' });
  renderAdmin(<AdminPhotoDetailPage />, '/admin/photos/31', '/admin/photos/:id');
  expect(await screen.findByText('暂无照片')).toBeVisible();
  expect(await screen.findByText('暂无管理员操作记录')).toBeVisible();
  expect(screen.queryByLabelText('新照片')).not.toBeInTheDocument();
});

test.each([401, 403, 404])('detail status %s has safe terminal state', async (status) => {
  vi.mocked(fetchAdminPhoto).mockRejectedValue(apiError(status));
  renderAdmin(<AdminPhotoDetailPage />, '/admin/photos/31', '/admin/photos/:id');
  expect(await screen.findByRole('heading', { name: status === 401 ? '登录页' : status === 403 ? '没有管理员权限' : '没有找到钓获记录' })).toBeVisible();
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
  expect(screen.queryByText('internal-private-key')).not.toBeInTheDocument();
});

test('forbidden history removes the original preview and action controls', async () => {
  vi.mocked(fetchAdminPhotoOperations).mockRejectedValue(apiError(403));
  renderAdmin(<AdminPhotoDetailPage />, '/admin/photos/31', '/admin/photos/:id');
  expect(await screen.findByRole('heading', { name: '没有管理员权限' })).toBeVisible();
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
  expect(screen.queryByLabelText('新照片')).not.toBeInTheDocument();
});

test('successful delete uses actual refreshed no-photo state and independently refreshes operation evidence', async () => {
  vi.mocked(fetchAdminPhoto).mockResolvedValueOnce(photo).mockResolvedValue({ ...photo, hasPhoto: false, revision: '9' });
  const { user } = renderAdmin(<AdminPhotoDetailPage />, '/admin/photos/31', '/admin/photos/:id');
  await screen.findByRole('img');
  await user.click(screen.getByRole('button', { name: '删除照片' }));
  await user.click(screen.getByRole('button', { name: '确认删除' }));
  expect(await screen.findByText('暂无照片')).toBeVisible();
  expect(screen.getByText('9')).toBeVisible();
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
  await waitFor(() => expect(fetchAdminPhotoOperations).toHaveBeenCalledTimes(2));
});

test.each(['before-write', 'during-refresh'])('delete refresh cannot be overwritten by a delayed background detail read started %s', async (timing) => {
  const oldRead = deferred<typeof photo>();
  const actualRead = deferred<typeof photo>();
  vi.mocked(fetchAdminPhoto).mockResolvedValueOnce(photo);
  if (timing === 'before-write') vi.mocked(fetchAdminPhoto).mockReturnValueOnce(oldRead.promise).mockReturnValueOnce(actualRead.promise);
  else vi.mocked(fetchAdminPhoto).mockReturnValueOnce(actualRead.promise).mockReturnValueOnce(oldRead.promise);
  const { user, queryClient } = renderAdmin(<AdminPhotoDetailPage />, '/admin/photos/31', '/admin/photos/:id');
  await screen.findByRole('img');
  let backgroundRead = Promise.resolve();
  if (timing === 'before-write') {
    backgroundRead = queryClient.refetchQueries({ queryKey: adminPhotoDetailQueryKey(31), exact: true });
    await waitFor(() => expect(fetchAdminPhoto).toHaveBeenCalledTimes(2));
  }
  await user.click(screen.getByRole('button', { name: '删除照片' }));
  await user.click(screen.getByRole('button', { name: '确认删除' }));
  if (timing === 'during-refresh') {
    await waitFor(() => expect(fetchAdminPhoto).toHaveBeenCalledTimes(2));
    backgroundRead = queryClient.refetchQueries({ queryKey: adminPhotoDetailQueryKey(31), exact: true });
  }
  await waitFor(() => expect(fetchAdminPhoto).toHaveBeenCalledTimes(3));
  await act(async () => { actualRead.resolve({ ...photo, hasPhoto: false, revision: '8' }); });
  expect(await screen.findByText('暂无照片')).toBeVisible();
  await act(async () => { oldRead.resolve(photo); await backgroundRead; });
  expect(queryClient.getQueryData(adminPhotoDetailQueryKey(31))).toMatchObject({ hasPhoto: false, revision: '8' });
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '删除照片' })).not.toBeInTheDocument();
});

test('image authority metadata refresh cannot be overwritten by a preexisting delayed background detail read', async () => {
  const oldRead = deferred<typeof photo>();
  vi.mocked(fetchAdminPhoto).mockResolvedValueOnce(photo).mockReturnValueOnce(oldRead.promise).mockResolvedValue({ ...photo, hasPhoto: false, revision: '8' });
  const { queryClient } = renderAdmin(<AdminPhotoDetailPage />, '/admin/photos/31', '/admin/photos/:id');
  const original = await screen.findByRole('img');
  const backgroundRead = queryClient.refetchQueries({ queryKey: adminPhotoDetailQueryKey(31), exact: true });
  await waitFor(() => expect(fetchAdminPhoto).toHaveBeenCalledTimes(2));
  fireEvent.error(original);
  expect(await screen.findByText('暂无照片')).toBeVisible();
  await act(async () => { oldRead.resolve(photo); await backgroundRead; });
  expect(queryClient.getQueryData(adminPhotoDetailQueryKey(31))).toMatchObject({ hasPhoto: false, revision: '8' });
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '删除照片' })).not.toBeInTheDocument();
});

test('a completed newer background revision is not replaced by an older delayed actual-state read', async () => {
  const actualRead = deferred<typeof photo>();
  vi.mocked(fetchAdminPhoto).mockResolvedValueOnce(photo).mockReturnValueOnce(actualRead.promise).mockResolvedValue({ ...photo, hasPhoto: true, revision: '9007199254740993' });
  const { user, queryClient } = renderAdmin(<AdminPhotoDetailPage />, '/admin/photos/31', '/admin/photos/:id');
  await screen.findByRole('img');
  await user.click(screen.getByRole('button', { name: '删除照片' }));
  await user.click(screen.getByRole('button', { name: '确认删除' }));
  await waitFor(() => expect(fetchAdminPhoto).toHaveBeenCalledTimes(2));
  await act(async () => { await queryClient.refetchQueries({ queryKey: adminPhotoDetailQueryKey(31), exact: true }); });
  expect(await screen.findByText('9007199254740993')).toBeVisible();
  await act(async () => { actualRead.resolve({ ...photo, hasPhoto: false, revision: '9007199254740992' }); });
  expect(queryClient.getQueryData(adminPhotoDetailQueryKey(31))).toMatchObject({ hasPhoto: true, revision: '9007199254740993' });
  expect(screen.getByRole('img')).toHaveAttribute('src', '/api/v1/admin/photos/31/content?revision=9007199254740993&reload=0');
  expect(screen.getByRole('button', { name: '删除照片' })).toBeEnabled();
});

test.each([403, 404])('a newer cached revision cannot hide actual-state read failure %s', async (status) => {
  const actualRead = deferred<typeof photo>();
  vi.mocked(fetchAdminPhoto).mockResolvedValueOnce(photo).mockReturnValueOnce(actualRead.promise).mockResolvedValue({ ...photo, revision: '9' });
  const { user, queryClient } = renderAdmin(<AdminPhotoDetailPage />, '/admin/photos/31', '/admin/photos/:id');
  await screen.findByRole('img');
  await user.click(screen.getByRole('button', { name: '删除照片' }));
  await user.click(screen.getByRole('button', { name: '确认删除' }));
  await waitFor(() => expect(fetchAdminPhoto).toHaveBeenCalledTimes(2));
  await act(async () => { await queryClient.refetchQueries({ queryKey: adminPhotoDetailQueryKey(31), exact: true }); });
  expect(await screen.findByText('9')).toBeVisible();
  await act(async () => { actualRead.reject(apiError(status)); });
  expect(await screen.findByRole('heading', { name: status === 403 ? '没有管理员权限' : '没有找到钓获记录' })).toBeVisible();
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '删除照片' })).not.toBeInTheDocument();
  expect(queryClient.getQueryData(adminPhotoDetailQueryKey(31))).toBeUndefined();
});

test('session change during awaited detail cancellation prevents starting the old-session actual-state read', async () => {
  const cancellation = deferred<void>();
  const { user, queryClient } = renderAdmin(<AdminPhotoDetailPage />, '/admin/photos/31', '/admin/photos/:id');
  await screen.findByRole('img');
  const cancel = queryClient.cancelQueries.bind(queryClient);
  const cancelSpy = vi.spyOn(queryClient, 'cancelQueries').mockImplementation(async (...args) => { await cancel(...args); await cancellation.promise; });
  await user.click(screen.getByRole('button', { name: '删除照片' }));
  await user.click(screen.getByRole('button', { name: '确认删除' }));
  await waitFor(() => expect(cancelSpy).toHaveBeenCalledWith({ queryKey: ['admin-photos', 'detail', 31], exact: true }));
  act(() => clearSessionScopedQueries(queryClient));
  await act(async () => { cancellation.resolve(undefined); });
  expect(await screen.findByRole('heading', { name: '登录页' })).toBeVisible();
  expect(fetchAdminPhoto).toHaveBeenCalledTimes(1);
  expect(queryClient.getQueriesData({ queryKey: ['admin-photos'] })).toEqual([]);
});

test('target switch during awaited authority cancellation prevents reading or modifying the previous target', async () => {
  const cancellation = deferred<void>();
  vi.mocked(fetchAdminPhoto).mockResolvedValueOnce(photo).mockResolvedValue({ ...photo, recordId: 32, ownerNickname: '另一位钓友' });
  const { user, queryClient } = renderAdmin(<><AdminPhotoDetailPage /><Link to="/admin/photos/32">切换目标</Link></>, '/admin/photos/31', '/admin/photos/:id');
  const original = await screen.findByRole('img');
  const cancel = queryClient.cancelQueries.bind(queryClient);
  const cancelSpy = vi.spyOn(queryClient, 'cancelQueries').mockImplementation(async (...args) => { await cancel(...args); await cancellation.promise; });
  fireEvent.error(original);
  await waitFor(() => expect(cancelSpy).toHaveBeenCalledTimes(1));
  await user.click(screen.getByRole('link', { name: '切换目标' }));
  expect(await screen.findByText('另一位钓友')).toBeVisible();
  await act(async () => { cancellation.resolve(undefined); });
  expect(fetchAdminPhoto).toHaveBeenCalledTimes(2);
  expect(fetchAdminPhoto).toHaveBeenLastCalledWith(32);
  expect(screen.getByText('另一位钓友')).toBeVisible();
});

test('late private detail response after logout cannot restore any cached metadata or image', async () => {
  const response = deferred<typeof photo>();
  vi.mocked(fetchAdminPhoto).mockReturnValue(response.promise);
  const { queryClient } = renderAdmin(<AdminPhotoDetailPage />, '/admin/photos/31', '/admin/photos/:id');
  expect(screen.getByRole('status')).toHaveTextContent('正在加载照片管理详情');
  act(() => clearSessionScopedQueries(queryClient));
  await act(async () => { response.resolve(photo); });
  expect(await screen.findByRole('heading', { name: '登录页' })).toBeVisible();
  expect(queryClient.getQueriesData({ queryKey: ['admin-photos'] })).toEqual([]);
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
  expect(screen.queryByText('河边钓友')).not.toBeInTheDocument();
});

test('invalid record IDs do not call a broader or fallback private API', () => {
  renderAdmin(<AdminPhotoDetailPage />, '/admin/photos/wrong', '/admin/photos/:id');
  expect(screen.getByRole('heading', { name: '没有找到钓获记录' })).toBeVisible();
  expect(fetchAdminPhoto).not.toHaveBeenCalled();
  expect(fetchAdminPhotoOperations).not.toHaveBeenCalled();
});

test('generic metadata failure offers a read retry without leaking private diagnostics', async () => {
  vi.mocked(fetchAdminPhoto).mockRejectedValueOnce(apiError(503)).mockResolvedValue(photo);
  const { user } = renderAdmin(<AdminPhotoDetailPage />, '/admin/photos/31', '/admin/photos/:id');
  expect(await screen.findByText('加载照片管理详情失败，请稍后重试')).toBeVisible();
  expect(screen.queryByText('internal-private-key')).not.toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '重试' }));
  expect(await screen.findByRole('img')).toBeVisible();
});
