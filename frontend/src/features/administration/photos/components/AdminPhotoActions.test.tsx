import { act, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { deferred } from '../../../../test/renderWithProviders';
import { clearSessionScopedQueries } from '../../../auth/api/sessionCache';
import { fetchAdminPhoto, removeAdminPhoto, replaceAdminPhoto } from '../api/adminPhotoApi';
import { apiError, photo, renderAdmin } from '../test/helpers';
import { AdminPhotoActions } from './AdminPhotoActions';

vi.mock('../api/adminPhotoApi', async (original) => ({ ...await original<typeof import('../api/adminPhotoApi')>(), fetchAdminPhoto: vi.fn(), replaceAdminPhoto: vi.fn(), removeAdminPhoto: vi.fn() }));
const createUrl = vi.fn(() => 'blob:local-preview');
const revokeUrl = vi.fn();
const file = new File(['jpeg'], 'photo.jpg', { type: 'image/jpeg' });
beforeEach(() => {
  vi.mocked(fetchAdminPhoto).mockReset().mockResolvedValue({ ...photo, revision: '8' });
  vi.mocked(replaceAdminPhoto).mockReset().mockResolvedValue(undefined);
  vi.mocked(removeAdminPhoto).mockReset().mockResolvedValue(undefined);
  createUrl.mockClear(); revokeUrl.mockClear();
  vi.stubGlobal('URL', class extends URL { static createObjectURL = createUrl; static revokeObjectURL = revokeUrl; });
});
afterEach(() => { vi.unstubAllGlobals(); });

function renderActions(recordId = 31, revision = '7') {
  return renderAdmin(<AdminPhotoActions recordId={recordId} revision={revision} onAccessError={vi.fn()} />);
}

test('local preview plus replacement confirmation binds file/id/string version and cancels without writing', async () => {
  const { user, unmount } = renderActions();
  await user.upload(screen.getByLabelText('新照片'), file);
  expect(screen.getByRole('img', { name: '新照片预览' })).toHaveAttribute('src', 'blob:local-preview');
  await user.click(screen.getByRole('button', { name: '替换照片' }));
  expect(screen.getByRole('alertdialog', { name: '确认替换照片' })).toBeVisible();
  expect(screen.getByText('旧照片将被移除，无法恢复。')).toBeVisible();
  expect(replaceAdminPhoto).not.toHaveBeenCalled();
  await user.click(screen.getByRole('button', { name: '取消' }));
  expect(replaceAdminPhoto).not.toHaveBeenCalled();
  await user.click(screen.getByRole('button', { name: '替换照片' }));
  await user.click(screen.getByRole('button', { name: '确认替换' }));
  expect(replaceAdminPhoto).toHaveBeenCalledWith(31, file, '7');
  await waitFor(() => expect(screen.queryByRole('img')).not.toBeInTheDocument());
  expect(revokeUrl).toHaveBeenCalledWith('blob:local-preview');
  unmount();
});

test('delete confirms irreversibility and keeps record; cancellation never writes', async () => {
  const { user } = renderActions();
  await user.click(screen.getByRole('button', { name: '删除照片' }));
  expect(screen.getByRole('alertdialog', { name: '确认删除照片' })).toBeVisible();
  expect(screen.getByText('钓获记录会保留，旧照片无法恢复。')).toBeVisible();
  await user.click(screen.getByRole('button', { name: '取消' }));
  expect(removeAdminPhoto).not.toHaveBeenCalled();
  await user.click(screen.getByRole('button', { name: '删除照片' }));
  await user.click(screen.getByRole('button', { name: '确认删除' }));
  expect(removeAdminPhoto).toHaveBeenCalledWith(31, '7');
});

test('changed revision closes confirmation and releases file/preview without mutation', async () => {
  const { user, rerender } = renderActions();
  await user.upload(screen.getByLabelText('新照片'), file);
  await user.click(screen.getByRole('button', { name: '替换照片' }));
  rerender(<AdminPhotoActions recordId={31} revision="8" onAccessError={vi.fn()} />);
  expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
  expect(revokeUrl).toHaveBeenCalledWith('blob:local-preview');
  expect(replaceAdminPhoto).not.toHaveBeenCalled();
});

test('target switch revokes preview, closes confirmation and clears native file selection', async () => {
  const { user, rerender, unmount } = renderActions();
  await user.upload(screen.getByLabelText('新照片'), file);
  await user.click(screen.getByRole('button', { name: '替换照片' }));
  rerender(<AdminPhotoActions recordId={32} revision="7" onAccessError={vi.fn()} />);
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
  expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  expect((screen.getByLabelText('新照片') as HTMLInputElement).files).toHaveLength(0);
  expect(replaceAdminPhoto).not.toHaveBeenCalled();
  expect(revokeUrl).toHaveBeenCalledWith('blob:local-preview');
  await user.upload(screen.getByLabelText('新照片'), file);
  unmount();
  expect(revokeUrl).toHaveBeenCalledTimes(2);
});

test('late write failure for the previous target cannot remove the new target selection', async () => {
  const write = deferred<void>();
  vi.mocked(replaceAdminPhoto).mockReturnValue(write.promise);
  const onAccessError = vi.fn();
  const { user, rerender, queryClient } = renderAdmin(<AdminPhotoActions recordId={31} revision="7" onAccessError={onAccessError} />);
  await user.upload(screen.getByLabelText('新照片'), file);
  await user.click(screen.getByRole('button', { name: '替换照片' }));
  await user.click(screen.getByRole('button', { name: '确认替换' }));
  rerender(<AdminPhotoActions recordId={32} revision="7" onAccessError={onAccessError} />);
  await user.upload(screen.getByLabelText('新照片'), file);
  await act(async () => { write.reject(apiError(403)); });
  expect(screen.getByRole('img', { name: '新照片预览' })).toBeVisible();
  expect(onAccessError).not.toHaveBeenCalled();
  expect(fetchAdminPhoto).not.toHaveBeenCalled();
  expect(queryClient.getQueriesData({ queryKey: ['admin-photos'] })).toEqual([]);
});

test('revision change also closes a pending delete confirmation without writing', async () => {
  const { user, rerender } = renderActions();
  await user.click(screen.getByRole('button', { name: '删除照片' }));
  rerender(<AdminPhotoActions recordId={31} revision="8" onAccessError={vi.fn()} />);
  expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  expect(removeAdminPhoto).not.toHaveBeenCalled();
});

test('changing a selected file closes the prior snapshot and reclaims its preview', async () => {
  createUrl.mockReturnValueOnce('blob:first').mockReturnValueOnce('blob:second');
  const { user } = renderActions();
  await user.upload(screen.getByLabelText('新照片'), file);
  await user.click(screen.getByRole('button', { name: '替换照片' }));
  await user.upload(screen.getByLabelText('新照片'), new File(['png'], 'second.png', { type: 'image/png' }));
  expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  expect(screen.getByRole('img')).toHaveAttribute('src', 'blob:second');
  expect(revokeUrl).toHaveBeenCalledWith('blob:first');
  expect(replaceAdminPhoto).not.toHaveBeenCalled();
});

test('invalid media never creates a preview or enables replacement', async () => {
  const { user } = renderActions();
  await user.upload(screen.getByLabelText('新照片'), new File([], 'empty.jpg', { type: 'image/jpeg' }));
  expect(screen.getByRole('status')).toHaveTextContent('请选择 10 MB 以内的 JPEG、PNG 或 WebP 照片');
  expect(screen.getByRole('button', { name: '替换照片' })).toBeDisabled();
  expect(createUrl).not.toHaveBeenCalled();
});

test('shared write lock covers pending mutation and delayed actual-state refresh', async () => {
  const write = deferred<void>(); const refresh = deferred<typeof photo>();
  vi.mocked(replaceAdminPhoto).mockReturnValue(write.promise);
  vi.mocked(fetchAdminPhoto).mockReturnValue(refresh.promise);
  const { user } = renderActions();
  await user.upload(screen.getByLabelText('新照片'), file);
  await user.click(screen.getByRole('button', { name: '替换照片' }));
  await user.click(screen.getByRole('button', { name: '确认替换' }));
  expect(screen.getByLabelText('新照片')).toBeDisabled();
  expect(screen.getByRole('button', { name: '删除照片' })).toBeDisabled();
  expect(screen.getByRole('button', { name: '取消' })).toBeDisabled();
  await act(async () => { write.resolve(undefined); });
  expect(screen.getByRole('button', { name: '删除照片' })).toBeDisabled();
  await act(async () => { refresh.resolve({ ...photo, revision: '8' }); });
  await waitFor(() => expect(screen.getByRole('button', { name: '删除照片' })).toBeEnabled());
  expect(removeAdminPhoto).not.toHaveBeenCalled();
});

test('409 resets reviewed choice, refreshes metadata, and never retries a mutation', async () => {
  vi.mocked(replaceAdminPhoto).mockRejectedValue(apiError(409));
  const { user } = renderActions();
  await user.upload(screen.getByLabelText('新照片'), file);
  await user.click(screen.getByRole('button', { name: '替换照片' }));
  await user.click(screen.getByRole('button', { name: '确认替换' }));
  expect(await screen.findByText('照片或记录已被修改，请刷新后重新确认操作')).toBeVisible();
  await waitFor(() => expect(fetchAdminPhoto).toHaveBeenCalledWith(31));
  expect(replaceAdminPhoto).toHaveBeenCalledTimes(1);
  expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: '替换照片' })).toBeDisabled();
});

test('successful writes refresh real detail and invalidate lists/history and owner caches', async () => {
  const { user, queryClient } = renderActions();
  for (const key of [['admin-photos', 'detail', 31], ['admin-photos', 'page', '', 0], ['admin-photos', 'operations', 31, 0], ['catches', 'detail', 31], ['catches', 'page', 0]]) queryClient.setQueryData(key, { private: true });
  await user.click(screen.getByRole('button', { name: '删除照片' }));
  await user.click(screen.getByRole('button', { name: '确认删除' }));
  await waitFor(() => expect(queryClient.getQueryData(['admin-photos', 'detail', 31])).toMatchObject({ revision: '8' }));
  for (const key of [['admin-photos', 'page', '', 0], ['admin-photos', 'operations', 31, 0], ['catches', 'detail', 31], ['catches', 'page', 0]]) expect(queryClient.getQueryState(key)?.isInvalidated).toBe(true);
});

test('successful write invalidates private caches even if the subsequent detail refresh fails', async () => {
  vi.mocked(fetchAdminPhoto).mockRejectedValue(apiError(503));
  const { user, queryClient } = renderActions();
  for (const key of [['admin-photos', 'page', '', 0], ['admin-photos', 'operations', 31, 0], ['catches', 'detail', 31]]) queryClient.setQueryData(key, { private: true });
  await user.click(screen.getByRole('button', { name: '删除照片' }));
  await user.click(screen.getByRole('button', { name: '确认删除' }));
  expect(await screen.findByText('刷新照片状态失败，请刷新后再操作')).toBeVisible();
  for (const key of [['admin-photos', 'page', '', 0], ['admin-photos', 'operations', 31, 0], ['catches', 'detail', 31]]) expect(queryClient.getQueryState(key)?.isInvalidated).toBe(true);
  expect(screen.getByRole('button', { name: '删除照片' })).toBeDisabled();
  expect(screen.getByRole('button', { name: '刷新照片状态' })).toBeEnabled();
});

test('successful write after target switch invalidates old caches without refilling old detail or resetting new selection', async () => {
  const write = deferred<void>();
  vi.mocked(replaceAdminPhoto).mockReturnValue(write.promise);
  const { user, rerender, queryClient } = renderActions();
  for (const key of [['admin-photos', 'detail', 31], ['admin-photos', 'page', '', 0], ['admin-photos', 'operations', 31, 0], ['catches', 'detail', 31]]) queryClient.setQueryData(key, { private: true });
  await user.upload(screen.getByLabelText('新照片'), file);
  await user.click(screen.getByRole('button', { name: '替换照片' }));
  await user.click(screen.getByRole('button', { name: '确认替换' }));
  rerender(<AdminPhotoActions recordId={32} revision="7" onAccessError={vi.fn()} />);
  await user.upload(screen.getByLabelText('新照片'), file);
  await act(async () => { write.resolve(undefined); });
  for (const key of [['admin-photos', 'detail', 31], ['admin-photos', 'page', '', 0], ['admin-photos', 'operations', 31, 0], ['catches', 'detail', 31]]) expect(queryClient.getQueryState(key)?.isInvalidated).toBe(true);
  expect(fetchAdminPhoto).not.toHaveBeenCalled();
  expect(screen.getByRole('img', { name: '新照片预览' })).toBeVisible();
});

test.each([401, 403])('write status %s releases file/preview and hides further writes', async (status) => {
  vi.mocked(replaceAdminPhoto).mockRejectedValue(apiError(status));
  const onAccessError = vi.fn();
  const { user } = renderAdmin(<AdminPhotoActions recordId={31} revision="7" onAccessError={onAccessError} />);
  await user.upload(screen.getByLabelText('新照片'), file);
  await user.click(screen.getByRole('button', { name: '替换照片' }));
  await user.click(screen.getByRole('button', { name: '确认替换' }));
  await waitFor(() => expect(onAccessError).toHaveBeenCalledWith(expect.objectContaining({ status })));
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
  expect(screen.queryByLabelText('新照片')).not.toBeInTheDocument();
  expect(revokeUrl).toHaveBeenCalledWith('blob:local-preview');
});

test('logout generation change releases preview immediately and late success cannot refill any private cache', async () => {
  const write = deferred<void>();
  vi.mocked(replaceAdminPhoto).mockReturnValue(write.promise);
  const { user, queryClient } = renderActions();
  queryClient.setQueryData(['admin-photos', 'detail', 31], photo);
  await user.upload(screen.getByLabelText('新照片'), file);
  await user.click(screen.getByRole('button', { name: '替换照片' }));
  await user.click(screen.getByRole('button', { name: '确认替换' }));
  act(() => clearSessionScopedQueries(queryClient));
  await waitFor(() => expect(revokeUrl).toHaveBeenCalledWith('blob:local-preview'));
  await act(async () => { write.resolve(undefined); });
  expect(fetchAdminPhoto).not.toHaveBeenCalled();
  expect(queryClient.getQueriesData({ queryKey: ['admin-photos'] })).toEqual([]);
  expect(queryClient.getQueriesData({ queryKey: ['catches'] })).toEqual([]);
});
test('logout during delayed refresh cannot refill private detail', async () => {
  const refresh = deferred<typeof photo>();
  vi.mocked(fetchAdminPhoto).mockReturnValue(refresh.promise);
  const { user, queryClient } = renderActions();
  queryClient.setQueryData(['admin-photos', 'detail', 31], photo);
  await user.click(screen.getByRole('button', { name: '删除照片' }));
  await user.click(screen.getByRole('button', { name: '确认删除' }));
  await waitFor(() => expect(fetchAdminPhoto).toHaveBeenCalledTimes(1));
  act(() => clearSessionScopedQueries(queryClient));
  await act(async () => { refresh.resolve({ ...photo, revision: '8' }); });
  expect(queryClient.getQueriesData({ queryKey: ['admin-photos'] })).toEqual([]);
});
