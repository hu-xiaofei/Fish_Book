import { QueryClient, QueryClientProvider, useQueryClient } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { PropsWithChildren } from 'react';
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import { ApiError } from '../../../shared/api/ApiError';
import { deferred } from '../../../test/renderWithProviders';
import { CURRENT_USER_QUERY_KEY } from '../../auth/api/currentUser';
import { clearSessionScopedQueries } from '../../auth/api/sessionCache';
import { ProtectedRoute } from '../../auth/components/ProtectedRoute';
import { FAVORITES_QUERY_KEY } from '../../favorites/api/favoritesApi';
import {
  catchDetailQueryKey,
  catchPageQueryKey,
} from '../api/catchRecordsApi';
import type { CatchRecordDetail, CatchRecordPage } from '../model/types';
import { CatchDetailPage } from './CatchDetailPage';

const {
  deleteCatchRecordMock,
  fetchCatchRecordMock,
  fetchCurrentUserMock,
} = vi.hoisted(() => ({
  deleteCatchRecordMock: vi.fn(),
  fetchCatchRecordMock: vi.fn(),
  fetchCurrentUserMock: vi.fn(),
}));

vi.mock('../api/catchRecordsApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/catchRecordsApi')>();
  return {
    ...actual,
    deleteCatchRecord: deleteCatchRecordMock,
    fetchCatchRecord: fetchCatchRecordMock,
  };
});

vi.mock('../../auth/api/currentUser', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../auth/api/currentUser')>();
  return { ...actual, fetchCurrentUser: fetchCurrentUserMock };
});

const savedCatch: CatchRecordDetail = {
  id: 31,
  fishSlug: 'channa-argus',
  commonNameZh: '乌鳢',
  caughtOn: '2026-08-20',
  location: '城郊水库',
  lengthCm: 42.5,
  weightG: 1350,
  method: '路亚',
  notes: '傍晚近岸中鱼',
  hasPhoto: false,
  createdAt: '2026-08-20T08:00:00Z',
  updatedAt: '2026-08-20T09:00:00Z',
};

const savedSummary = {
  id: savedCatch.id,
  fishSlug: savedCatch.fishSlug,
  commonNameZh: savedCatch.commonNameZh,
  caughtOn: savedCatch.caughtOn,
  location: savedCatch.location,
  lengthCm: savedCatch.lengthCm,
  weightG: savedCatch.weightG,
  method: savedCatch.method,
  hasPhoto: savedCatch.hasPhoto,
  createdAt: savedCatch.createdAt,
  updatedAt: savedCatch.updatedAt,
};

const cachedPage: CatchRecordPage = {
  items: [savedSummary],
  page: 0,
  size: 20,
  totalItems: 1,
  totalPages: 1,
};

const userBCatchPage: CatchRecordPage = {
  items: [{ ...savedSummary, id: 99, location: '用户 B 的钓点' }],
  page: 0,
  size: 20,
  totalItems: 1,
  totalPages: 1,
};

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}</output>;
}

function LoginCacheSafetyProbe() {
  const queryClient = useQueryClient();
  const hasPrivateData = queryClient.getQueryData(CURRENT_USER_QUERY_KEY) !== undefined
    || queryClient.getQueriesData({ queryKey: ['catches'] }).some(([, data]) => data !== undefined)
    || queryClient.getQueriesData({ queryKey: FAVORITES_QUERY_KEY }).some(([, data]) => data !== undefined);
  return <output data-testid="private-cache-at-login">{hasPrivateData ? 'unsafe' : 'safe'}</output>;
}

function AccountTransitionControl({ queryClient }: { queryClient: QueryClient }) {
  const navigate = useNavigate();
  return (
    <button
      type="button"
      onClick={() => {
        clearSessionScopedQueries(queryClient);
        clearSessionScopedQueries(queryClient);
        queryClient.setQueryData(CURRENT_USER_QUERY_KEY, {
          id: 2, email: 'user-b@example.com', nickname: 'User B', role: 'USER',
        });
        queryClient.setQueryData(catchPageQueryKey(0), userBCatchPage);
        queryClient.setQueryData(catchDetailQueryKey(99), { ...savedCatch, id: 99, location: '用户 B 的钓点' });
        queryClient.setQueryData([...FAVORITES_QUERY_KEY, 'status', 'cyprinus-carpio'], {
          items: [{ fishSlug: 'cyprinus-carpio', favorited: true }],
        });
        navigate('/profile');
      }}
    >
      完成退出并登录用户 B
    </button>
  );
}

function renderCatchDetail({
  initialEntry = '/catches/31',
  cachedPrivateData = false,
  protectedRoute = false,
}: {
  initialEntry?: string;
  cachedPrivateData?: boolean;
  protectedRoute?: boolean;
} = {}) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, retryDelay: 0 },
      mutations: { retry: false },
    },
  });
  if (!protectedRoute) {
    queryClient.setQueryData(CURRENT_USER_QUERY_KEY, {
      id: 1, email: 'angler@example.com', nickname: 'River', role: 'USER',
    });
  }
  if (cachedPrivateData) {
    queryClient.setQueryData(catchPageQueryKey(0), cachedPage);
    queryClient.setQueryData(catchDetailQueryKey(30), { ...savedCatch, id: 30 });
    queryClient.setQueryData(FAVORITES_QUERY_KEY, { items: [{ fishSlug: 'channa-argus' }] });
  }

  function Wrapper({ children }: PropsWithChildren) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[initialEntry]}>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  }

  const page = protectedRoute ? (
    <ProtectedRoute><CatchDetailPage /></ProtectedRoute>
  ) : <CatchDetailPage />;

  return {
    queryClient,
    user: userEvent.setup(),
    ...render(
      <Routes>
        <Route path="/catches/:id" element={<><AccountTransitionControl queryClient={queryClient} />{page}<LocationProbe /></>} />
        <Route path="/catches" element={<><h1>钓获记录</h1><LocationProbe /></>} />
        <Route path="/login" element={<><LoginCacheSafetyProbe /><LocationProbe /></>} />
        <Route path="/catches/:id/edit" element={<LocationProbe />} />
        <Route path="/profile" element={<><h1>用户 B 个人资料</h1><LocationProbe /></>} />
      </Routes>,
      { wrapper: Wrapper },
    ),
  };
}

function notFoundError() {
  return new ApiError(404, {
    code: 'CATCH_RECORD_NOT_FOUND', message: 'record does not exist', fieldErrors: [], requestId: 'test-request',
  });
}

beforeEach(() => {
  deleteCatchRecordMock.mockReset();
  fetchCatchRecordMock.mockReset();
  fetchCurrentUserMock.mockReset();
  fetchCatchRecordMock.mockResolvedValue(savedCatch);
  fetchCurrentUserMock.mockResolvedValue({
    id: 1, email: 'angler@example.com', nickname: 'River', role: 'USER',
  });
});

test('shows all saved fields and the no-photo state', async () => {
  renderCatchDetail();

  expect(await screen.findByRole('heading', { name: '乌鳢钓获记录' })).toBeInTheDocument();
  expect(screen.getByText('城郊水库')).toBeInTheDocument();
  expect(screen.getByText('2026-08-20')).toBeInTheDocument();
  expect(screen.getByText('42.5 cm · 1350 g')).toBeInTheDocument();
  expect(screen.getByText('路亚')).toBeInTheDocument();
  expect(screen.getByText('傍晚近岸中鱼')).toBeInTheDocument();
  expect(screen.getByText('尚未添加照片')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '编辑记录' })).toHaveAttribute('href', '/catches/31/edit');
});

test('renders empty optional fields without inventing measurements or notes', async () => {
  fetchCatchRecordMock.mockResolvedValue({
    ...savedCatch,
    lengthCm: null,
    weightG: null,
    method: null,
    notes: null,
  });
  renderCatchDetail();

  expect(await screen.findByText('未记录尺寸')).toBeInTheDocument();
  expect(screen.getAllByText('未记录').length).toBeGreaterThanOrEqual(2);
});

test('shows loading while the private record is pending', () => {
  fetchCatchRecordMock.mockImplementation(() => new Promise(() => undefined));
  renderCatchDetail();

  expect(screen.getByText('正在加载钓获记录…')).toHaveAttribute('role', 'status');
});

test('shows a safe error and retries the detail query', async () => {
  fetchCatchRecordMock
    .mockRejectedValueOnce(new Error('database unavailable at db.internal'))
    .mockRejectedValueOnce(new Error('database unavailable at db.internal'))
    .mockRejectedValueOnce(new Error('database unavailable at db.internal'))
    .mockResolvedValueOnce(savedCatch);
  const { user } = renderCatchDetail();

  const status = await screen.findByText('加载钓获记录失败，请稍后重试');
  expect(status).not.toHaveTextContent('db.internal');
  await user.click(screen.getByRole('button', { name: '重试' }));

  expect(await screen.findByRole('heading', { name: '乌鳢钓获记录' })).toBeInTheDocument();
  expect(fetchCatchRecordMock).toHaveBeenCalledTimes(4);
});

test('handles malformed IDs and owned-record 404s as the same safe missing state', async () => {
  const malformed = renderCatchDetail({ initialEntry: '/catches/not-a-number' });
  expect(await screen.findByRole('heading', { name: '没有找到钓获记录' })).toBeInTheDocument();
  expect(fetchCatchRecordMock).not.toHaveBeenCalled();
  malformed.unmount();

  fetchCatchRecordMock.mockRejectedValue(notFoundError());
  renderCatchDetail();
  expect(await screen.findByRole('heading', { name: '没有找到钓获记录' })).toBeInTheDocument();
});

test('requires an explicit confirmation before deleting', async () => {
  deleteCatchRecordMock.mockResolvedValue(undefined);
  const { user, queryClient } = renderCatchDetail({ cachedPrivateData: true });

  await user.click(await screen.findByRole('button', { name: '删除记录' }));
  expect(deleteCatchRecordMock).not.toHaveBeenCalled();
  expect(screen.getByRole('alertdialog', { name: '确认删除钓获记录' })).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '确认删除' }));

  await waitFor(() => expect(deleteCatchRecordMock).toHaveBeenCalledWith(31));
  expect(await screen.findByTestId('location')).toHaveTextContent('/catches');
  expect(queryClient.getQueryData(catchDetailQueryKey(31))).toBeUndefined();
  expect(queryClient.getQueryState(catchPageQueryKey(0))?.isInvalidated).toBe(true);
});

test('cancels deletion without changing the private record', async () => {
  const { user } = renderCatchDetail();

  await user.click(await screen.findByRole('button', { name: '删除记录' }));
  await user.click(screen.getByRole('button', { name: '取消' }));

  expect(deleteCatchRecordMock).not.toHaveBeenCalled();
  expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '乌鳢钓获记录' })).toBeInTheDocument();
});

test('keeps the record visible after a failed delete and offers a safe retry', async () => {
  deleteCatchRecordMock
    .mockRejectedValueOnce(new Error('delete failed at mysql.internal'))
    .mockResolvedValueOnce(undefined);
  const { user } = renderCatchDetail();

  await user.click(await screen.findByRole('button', { name: '删除记录' }));
  await user.click(screen.getByRole('button', { name: '确认删除' }));

  const status = await screen.findByText('删除记录失败，请稍后重试');
  expect(status).not.toHaveTextContent('mysql.internal');
  expect(screen.getByRole('heading', { name: '乌鳢钓获记录' })).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '重试删除' }));
  expect(screen.getByRole('alertdialog')).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '确认删除' }));
  await waitFor(() => expect(deleteCatchRecordMock).toHaveBeenCalledTimes(2));
});

test('confirmed detail GET 401 clears private caches before the protected login route without another me request', async () => {
  fetchCatchRecordMock.mockRejectedValue(new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED', message: '请先登录', fieldErrors: [], requestId: 'test-request',
  }));
  const { queryClient } = renderCatchDetail({ cachedPrivateData: true, protectedRoute: true });

  await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login'));
  expect(screen.getByTestId('private-cache-at-login')).toHaveTextContent('safe');
  expect(queryClient.getQueryData(CURRENT_USER_QUERY_KEY)).toBeUndefined();
  expect(queryClient.getQueriesData({ queryKey: ['catches'] }).every(([, data]) => data === undefined))
    .toBe(true);
  expect(queryClient.getQueriesData({ queryKey: FAVORITES_QUERY_KEY }).every(([, data]) => data === undefined))
    .toBe(true);
  expect(fetchCurrentUserMock).toHaveBeenCalledTimes(1);
});

test('confirmed delete 401 clears private caches before the protected login route without another me request', async () => {
  deleteCatchRecordMock.mockRejectedValue(new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED', message: '请先登录', fieldErrors: [], requestId: 'test-request',
  }));
  const { queryClient, user } = renderCatchDetail({ cachedPrivateData: true, protectedRoute: true });

  await user.click(await screen.findByRole('button', { name: '删除记录' }));
  await user.click(screen.getByRole('button', { name: '确认删除' }));

  await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login'));
  expect(screen.getByTestId('private-cache-at-login')).toHaveTextContent('safe');
  expect(queryClient.getQueryData(CURRENT_USER_QUERY_KEY)).toBeUndefined();
  expect(queryClient.getQueriesData({ queryKey: ['catches'] }).every(([, data]) => data === undefined))
    .toBe(true);
  expect(queryClient.getQueriesData({ queryKey: FAVORITES_QUERY_KEY }).every(([, data]) => data === undefined))
    .toBe(true);
  expect(fetchCurrentUserMock).toHaveBeenCalledTimes(1);
});

test('ignores user A delete success after logout and user B login', async () => {
  const deleting = deferred<void>();
  deleteCatchRecordMock.mockReturnValue(deleting.promise);
  const { user, queryClient } = renderCatchDetail();

  await user.click(await screen.findByRole('button', { name: '删除记录' }));
  await user.click(screen.getByRole('button', { name: '确认删除' }));
  await waitFor(() => expect(deleteCatchRecordMock).toHaveBeenCalledTimes(1));
  await user.click(screen.getByRole('button', { name: '完成退出并登录用户 B' }));
  expect(await screen.findByRole('heading', { name: '用户 B 个人资料' })).toBeInTheDocument();

  deleting.resolve();
  await waitFor(() => expect(queryClient.getMutationCache().getAll().at(-1)?.state.status).toBe('success'));

  expect(queryClient.getQueryData(catchPageQueryKey(0))).toEqual(userBCatchPage);
  expect(queryClient.getQueryData(catchDetailQueryKey(99))).toMatchObject({ id: 99 });
  expect(queryClient.getQueryState(catchPageQueryKey(0))?.isInvalidated).toBe(false);
  expect(screen.getByTestId('location')).toHaveTextContent('/profile');
});

test('ignores user A delete 401 after logout and user B login', async () => {
  const deleting = deferred<void>();
  deleteCatchRecordMock.mockReturnValue(deleting.promise);
  const { user, queryClient } = renderCatchDetail();

  await user.click(await screen.findByRole('button', { name: '删除记录' }));
  await user.click(screen.getByRole('button', { name: '确认删除' }));
  await waitFor(() => expect(deleteCatchRecordMock).toHaveBeenCalledTimes(1));
  await user.click(screen.getByRole('button', { name: '完成退出并登录用户 B' }));

  deleting.reject(new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED', message: '用户 A 会话已过期', fieldErrors: [], requestId: 'user-a-delete',
  }));
  await waitFor(() => expect(queryClient.getMutationCache().getAll().at(-1)?.state.status).toBe('error'));

  expect(queryClient.getQueryData(CURRENT_USER_QUERY_KEY)).toMatchObject({ id: 2 });
  expect(queryClient.getQueryData(catchPageQueryKey(0))).toEqual(userBCatchPage);
  expect(queryClient.getQueriesData({ queryKey: FAVORITES_QUERY_KEY }).some(([, data]) => data !== undefined))
    .toBe(true);
  expect(screen.getByTestId('location')).toHaveTextContent('/profile');
});

test('stops delete success side effects when the account changes during invalidation', async () => {
  const invalidating = deferred<void>();
  deleteCatchRecordMock.mockResolvedValue(undefined);
  const { user, queryClient } = renderCatchDetail();
  const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries').mockReturnValue(invalidating.promise);

  await user.click(await screen.findByRole('button', { name: '删除记录' }));
  await user.click(screen.getByRole('button', { name: '确认删除' }));
  await waitFor(() => expect(invalidateSpy).toHaveBeenCalledTimes(1));
  await user.click(screen.getByRole('button', { name: '完成退出并登录用户 B' }));

  invalidating.resolve();
  await waitFor(() => expect(queryClient.getMutationCache().getAll().at(-1)?.state.status).toBe('success'));

  expect(queryClient.getQueryData(catchDetailQueryKey(99))).toMatchObject({ id: 99 });
  expect(screen.getByTestId('location')).toHaveTextContent('/profile');
});
