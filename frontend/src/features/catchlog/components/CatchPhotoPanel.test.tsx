import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { PropsWithChildren } from 'react';
import { useState } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import { deferred } from '../../../test/renderWithProviders';
import { ApiError } from '../../../shared/api/ApiError';
import { clearSessionScopedQueries } from '../../auth/api/sessionCache';
import { CURRENT_USER_QUERY_KEY } from '../../auth/api/currentUser';
import { catchDetailQueryKey } from '../api/catchRecordsApi';
import type { CatchRecordDetail } from '../model/types';
import { CatchPhotoPanel } from './CatchPhotoPanel';

const { putCatchPhotoMock, removeCatchPhotoMock, fetchCatchRecordMock } = vi.hoisted(() => ({
  putCatchPhotoMock: vi.fn(),
  removeCatchPhotoMock: vi.fn(),
  fetchCatchRecordMock: vi.fn(),
}));

vi.mock('../api/catchRecordsApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/catchRecordsApi')>();
  return { ...actual, fetchCatchRecord: fetchCatchRecordMock };
});

vi.mock('../api/catchPhotoApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/catchPhotoApi')>();
  return {
    ...actual,
    putCatchPhoto: putCatchPhotoMock,
    removeCatchPhoto: removeCatchPhotoMock,
  };
});

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}</output>;
}

function renderPanel(hasPhoto = false) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  queryClient.setQueryData(['catches'], { seeded: true });
  const initialDetail: CatchRecordDetail = {
    id: 31, revision: '7', fishSlug: 'channa-argus', commonNameZh: '乌鳢',
    caughtOn: '2026-08-20', location: '水库', lengthCm: null, weightG: null,
    method: null, notes: null, hasPhoto, createdAt: '2026-08-20T08:00:00Z',
    updatedAt: '2026-08-20T08:00:00Z',
  };
  queryClient.setQueryData(catchDetailQueryKey(31), initialDetail);
  queryClient.setQueryData(catchDetailQueryKey(99), { ...initialDetail, id: 99, revision: '12' });
  function PanelHarness() {
    const [targetId, setTargetId] = useState(31);
    const detail = useQuery({ queryKey: catchDetailQueryKey(targetId), queryFn: fetchCatchRecordMock, staleTime: Infinity }).data as CatchRecordDetail;
    return <><button type="button" onClick={() => setTargetId(99)}>切换记录</button><CatchPhotoPanel recordId={detail.id} revision={detail.revision} hasPhoto={detail.hasPhoto} photoAlt="乌鳢钓获照片" /></>;
  }
  function Wrapper({ children }: PropsWithChildren) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/catches/31']}>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  }

  return {
    queryClient,
    user: userEvent.setup({ applyAccept: false }),
    ...render(
      <Routes>
        <Route
          path="/catches/:id"
          element={(
            <>
              <PanelHarness />
              <LocationProbe />
            </>
          )}
        />
        <Route path="/login" element={<LocationProbe />} />
      </Routes>,
      { wrapper: Wrapper },
    ),
  };
}

beforeEach(() => {
  putCatchPhotoMock.mockReset();
  removeCatchPhotoMock.mockReset();
  fetchCatchRecordMock.mockReset();
  fetchCatchRecordMock.mockResolvedValue({
    id: 31, revision: '8', fishSlug: 'channa-argus', commonNameZh: '乌鳢',
    caughtOn: '2026-08-20', location: '水库', lengthCm: null, weightG: null,
    method: null, notes: null, hasPhoto: true, createdAt: '2026-08-20T08:00:00Z',
    updatedAt: '2026-08-20T08:00:00Z',
  });
});

test('shows no-photo state and rejects unsupported or oversized files before upload', async () => {
  const { user } = renderPanel();
  const input = screen.getByLabelText('钓获照片');

  expect(screen.getByText('暂无照片')).toBeInTheDocument();
  await user.upload(input, new File(['gif'], 'catch.gif', { type: 'image/gif' }));
  expect(screen.getByText('请选择 10 MB 以内的 JPEG、PNG 或 WebP 照片')).toBeInTheDocument();
  expect(putCatchPhotoMock).not.toHaveBeenCalled();

  await user.upload(input, new File(
    [new Uint8Array(10 * 1024 * 1024 + 1)],
    'huge.jpg',
    { type: 'image/jpeg' },
  ));
  expect(screen.getByText('请选择 10 MB 以内的 JPEG、PNG 或 WebP 照片')).toBeInTheDocument();
  expect(putCatchPhotoMock).not.toHaveBeenCalled();
});

test('shows upload pending, then renders the protected image and invalidates catch data', async () => {
  const uploading = deferred<void>();
  putCatchPhotoMock.mockReturnValue(uploading.promise);
  const { user, queryClient } = renderPanel();
  const file = new File(['jpeg'], 'catch.jpg', { type: 'image/jpeg' });

  await user.upload(screen.getByLabelText('钓获照片'), file);
  await user.click(screen.getByRole('button', { name: '上传照片' }));
  expect(screen.getByRole('button', { name: '上传中…' })).toBeDisabled();
  uploading.resolve();

  const image = await screen.findByRole('img', { name: '乌鳢钓获照片' });
  expect(image.getAttribute('src')).toContain('/api/v1/catches/31/photo');
  expect(putCatchPhotoMock).toHaveBeenCalledWith(31, file, '7');
  expect(queryClient.getQueryState(['catches'])?.isInvalidated).toBe(true);
});

test('keeps a failed selection for safe retry without exposing backend details', async () => {
  putCatchPhotoMock
    .mockRejectedValueOnce(new Error('minio unavailable at storage.internal'))
    .mockResolvedValueOnce(undefined);
  const { user } = renderPanel();
  const file = new File(['png'], 'catch.png', { type: 'image/png' });

  await user.upload(screen.getByLabelText('钓获照片'), file);
  await user.click(screen.getByRole('button', { name: '上传照片' }));
  const status = await screen.findByText('照片上传失败，请稍后重试');
  expect(status).not.toHaveTextContent('storage.internal');
  await user.click(screen.getByRole('button', { name: '重试上传' }));

  expect(await screen.findByRole('img', { name: '乌鳢钓获照片' })).toBeInTheDocument();
  expect(putCatchPhotoMock).toHaveBeenCalledTimes(2);
});

test('supports image fallback, replacement, and confirmed removal', async () => {
  putCatchPhotoMock.mockResolvedValue(undefined);
  removeCatchPhotoMock.mockResolvedValue(undefined);
  const { user } = renderPanel(true);

  const image = screen.getByRole('img', { name: '乌鳢钓获照片' });
  fireEvent.error(image);
  expect(screen.getByText('照片暂时无法显示')).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '重新加载照片' }));
  expect(screen.getByRole('img', { name: '乌鳢钓获照片' })).toBeInTheDocument();

  const replacement = new File(['webp'], 'replacement.webp', { type: 'image/webp' });
  await user.upload(screen.getByLabelText('钓获照片'), replacement);
  await user.click(screen.getByRole('button', { name: '替换照片' }));
  await waitFor(() => expect(putCatchPhotoMock).toHaveBeenCalledWith(31, replacement, '7'));
  await waitFor(() => expect(screen.getByRole('button', { name: '移除照片' })).toBeEnabled());
  fetchCatchRecordMock.mockResolvedValue({
    ...queryDetail(), revision: '9', hasPhoto: false,
  });

  await user.click(screen.getByRole('button', { name: '移除照片' }));
  expect(removeCatchPhotoMock).not.toHaveBeenCalled();
  expect(screen.getByRole('alertdialog', { name: '确认移除照片' })).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '确认移除' }));

  expect(await screen.findByText('暂无照片')).toBeInTheDocument();
  expect(removeCatchPhotoMock).toHaveBeenCalledWith(31, '8');
});

function queryDetail(): CatchRecordDetail {
  return {
    id: 31, revision: '8', fishSlug: 'channa-argus', commonNameZh: '乌鳢',
    caughtOn: '2026-08-20', location: '水库', lengthCm: null, weightG: null,
    method: null, notes: null, hasPhoto: true, createdAt: '2026-08-20T08:00:00Z',
    updatedAt: '2026-08-20T08:00:00Z',
  };
}

test('conflict closes removal confirmation and refreshes without a second mutation', async () => {
  removeCatchPhotoMock.mockRejectedValue(new ApiError(409, {
    code: 'CATCH_PHOTO_CONFLICT', message: 'internal object key', fieldErrors: [], requestId: 'conflict',
  }));
  const { user } = renderPanel(true);
  await user.click(screen.getByRole('button', { name: '移除照片' }));
  await user.click(screen.getByRole('button', { name: '确认移除' }));

  expect(await screen.findByText('照片或记录已被修改，请刷新后重新确认操作')).toBeInTheDocument();
  await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
  expect(removeCatchPhotoMock).toHaveBeenCalledTimes(1);
  expect(removeCatchPhotoMock).toHaveBeenCalledWith(31, '7');
  expect(fetchCatchRecordMock).toHaveBeenCalled();
  expect(screen.queryByText('internal object key')).not.toBeInTheDocument();
});

test('another write stays disabled until actual current detail is fetched after success', async () => {
  const refreshing = deferred<CatchRecordDetail>();
  putCatchPhotoMock.mockResolvedValue(undefined);
  fetchCatchRecordMock.mockReturnValue(refreshing.promise);
  const { user } = renderPanel(true);
  await user.upload(screen.getByLabelText('钓获照片'), new File(['jpeg'], 'catch.jpg', { type: 'image/jpeg' }));
  await user.click(screen.getByRole('button', { name: '替换照片' }));
  await waitFor(() => expect(fetchCatchRecordMock).toHaveBeenCalled());
  expect(screen.getByLabelText('钓获照片')).toBeDisabled();
  expect(screen.getByRole('button', { name: '移除照片' })).toBeDisabled();
  refreshing.resolve(queryDetail());
  await waitFor(() => expect(screen.getByLabelText('钓获照片')).toBeEnabled());
  expect(screen.getByRole('img')).toHaveAttribute('src', '/api/v1/catches/31/photo?revision=8&reload=0');
});

test('late success metadata never restores private cache after a session change', async () => {
  const refreshing = deferred<CatchRecordDetail>();
  putCatchPhotoMock.mockResolvedValue(undefined);
  fetchCatchRecordMock.mockReturnValue(refreshing.promise);
  const { user, queryClient, unmount } = renderPanel(true);
  await user.upload(screen.getByLabelText('钓获照片'), new File(['jpeg'], 'catch.jpg', { type: 'image/jpeg' }));
  await user.click(screen.getByRole('button', { name: '替换照片' }));
  await waitFor(() => expect(fetchCatchRecordMock).toHaveBeenCalled());
  unmount();
  clearSessionScopedQueries(queryClient);
  refreshing.resolve(queryDetail());
  await waitFor(() => expect(queryClient.getMutationCache().getAll().at(-1)?.state.status).toBe('success'));
  expect(queryClient.getQueryData(catchDetailQueryKey(31))).toBeUndefined();
});

test('changing target clears the selected file and open confirmation', async () => {
  const { user } = renderPanel(true);
  await user.upload(screen.getByLabelText('钓获照片'), new File(['jpeg'], 'catch.jpg', { type: 'image/jpeg' }));
  await user.click(screen.getByRole('button', { name: '移除照片' }));
  await user.click(screen.getByRole('button', { name: '切换记录' }));
  expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  expect(screen.getByLabelText('钓获照片')).toHaveValue('');
  expect(screen.getByRole('button', { name: '替换照片' })).toBeDisabled();
  expect(putCatchPhotoMock).not.toHaveBeenCalled();
  expect(removeCatchPhotoMock).not.toHaveBeenCalled();
});

test('a pending upload and its refresh remain bound to the captured target', async () => {
  const uploading = deferred<void>();
  putCatchPhotoMock.mockReturnValue(uploading.promise);
  const { user, queryClient } = renderPanel(true);
  const photo = new File(['jpeg'], 'catch.jpg', { type: 'image/jpeg' });
  await user.upload(screen.getByLabelText('钓获照片'), photo);
  await user.click(screen.getByRole('button', { name: '替换照片' }));
  await user.click(screen.getByRole('button', { name: '切换记录' }));
  uploading.resolve();
  await waitFor(() => expect(fetchCatchRecordMock).toHaveBeenCalledWith(31));
  expect(putCatchPhotoMock).toHaveBeenCalledWith(31, photo, '7');
  expect(queryClient.getQueryData<CatchRecordDetail>(catchDetailQueryKey(99))?.revision).toBe('12');
  expect(screen.getByRole('img')).toHaveAttribute('src', '/api/v1/catches/99/photo?revision=12&reload=0');
});

test('upload 401 expires the session and clears private cache without retry', async () => {
  putCatchPhotoMock.mockRejectedValue(new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED', message: '请先登录', fieldErrors: [], requestId: 'expired',
  }));
  const { user, queryClient } = renderPanel();
  await user.upload(screen.getByLabelText('钓获照片'), new File(['jpeg'], 'catch.jpg', { type: 'image/jpeg' }));
  await user.click(screen.getByRole('button', { name: '上传照片' }));
  await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login'));
  expect(queryClient.getQueryData(catchDetailQueryKey(31))).toBeUndefined();
  expect(putCatchPhotoMock).toHaveBeenCalledTimes(1);
  expect(fetchCatchRecordMock).not.toHaveBeenCalled();
});

test('late upload 401 cannot clear the next account cache', async () => {
  const uploading = deferred<void>();
  putCatchPhotoMock.mockReturnValue(uploading.promise);
  const { user, queryClient, unmount } = renderPanel();
  await user.upload(screen.getByLabelText('钓获照片'), new File(['jpeg'], 'catch.jpg', { type: 'image/jpeg' }));
  await user.click(screen.getByRole('button', { name: '上传照片' }));
  unmount();
  clearSessionScopedQueries(queryClient);
  queryClient.setQueryData(CURRENT_USER_QUERY_KEY, { id: 2, email: 'b@example.com', nickname: 'B', role: 'USER' });
  uploading.reject(new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED', message: '请先登录', fieldErrors: [], requestId: 'old-expired',
  }));
  await waitFor(() => expect(queryClient.getMutationCache().getAll().at(-1)?.state.status).toBe('error'));
  expect(queryClient.getQueryData(CURRENT_USER_QUERY_KEY)).toMatchObject({ id: 2 });
  expect(fetchCatchRecordMock).not.toHaveBeenCalled();
});

test('failed success-state refresh blocks another write until an explicit refresh succeeds', async () => {
  putCatchPhotoMock.mockResolvedValue(undefined);
  fetchCatchRecordMock.mockRejectedValueOnce(new Error('storage.internal')).mockResolvedValue(queryDetail());
  const { user } = renderPanel(true);
  await user.upload(screen.getByLabelText('钓获照片'), new File(['jpeg'], 'catch.jpg', { type: 'image/jpeg' }));
  await user.click(screen.getByRole('button', { name: '替换照片' }));
  expect(await screen.findByText('刷新照片状态失败，请刷新后再操作')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '移除照片' })).toBeDisabled();
  await user.click(screen.getByRole('button', { name: '刷新照片状态' }));
  await waitFor(() => expect(screen.getByRole('button', { name: '移除照片' })).toBeEnabled());
  expect(putCatchPhotoMock).toHaveBeenCalledTimes(1);
});
