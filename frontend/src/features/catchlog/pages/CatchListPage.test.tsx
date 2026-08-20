import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { PropsWithChildren } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import { ApiError } from '../../../shared/api/ApiError';
import type { User } from '../../../shared/api/types';
import { CURRENT_USER_QUERY_KEY } from '../../auth/api/currentUser';
import { ProtectedRoute } from '../../auth/components/ProtectedRoute';
import { catchDetailQueryKey, catchPageQueryKey } from '../api/catchRecordsApi';
import type { CatchRecordPage } from '../model/types';
import { CatchListPage } from './CatchListPage';

const { fetchCatchPageMock, fetchCurrentUserMock } = vi.hoisted(() => ({
  fetchCatchPageMock: vi.fn(),
  fetchCurrentUserMock: vi.fn(),
}));

vi.mock('../api/catchRecordsApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/catchRecordsApi')>();
  return { ...actual, fetchCatchPage: fetchCatchPageMock };
});

vi.mock('../../auth/api/currentUser', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../auth/api/currentUser')>();
  return { ...actual, fetchCurrentUser: fetchCurrentUserMock };
});

const authenticatedUser: User = {
  id: 1,
  email: 'angler@example.com',
  nickname: 'River',
  role: 'USER',
};

const populatedPage: CatchRecordPage = {
  items: [{
    id: 31,
    fishSlug: 'channa-argus',
    commonNameZh: '乌鳢',
    caughtOn: '2026-08-20',
    location: '城郊水库',
    lengthCm: 42.5,
    weightG: 1350,
    method: '路亚',
    hasPhoto: false,
    createdAt: '2026-08-20T08:00:00Z',
    updatedAt: '2026-08-20T08:00:00Z',
  }],
  page: 0,
  size: 20,
  totalItems: 1,
  totalPages: 1,
};

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}{location.search}</output>;
}

function renderCatchList({
  initialEntry = '/catches',
  protectedRoute = false,
  queryRetry = false,
  cachedUser,
  cachedUserUpdatedAt = 1,
  cachedCatchPage,
  prepareQueryClient,
}: {
  initialEntry?: string;
  protectedRoute?: boolean;
  queryRetry?: boolean | number;
  cachedUser?: User;
  cachedUserUpdatedAt?: number;
  cachedCatchPage?: CatchRecordPage;
  prepareQueryClient?: (queryClient: QueryClient) => void;
} = {}) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: queryRetry, retryDelay: 0 },
      mutations: { retry: false },
    },
  });
  if (!protectedRoute) {
    queryClient.setQueryData(CURRENT_USER_QUERY_KEY, authenticatedUser);
  }
  if (cachedUser) {
    queryClient.setQueryData(CURRENT_USER_QUERY_KEY, cachedUser, { updatedAt: cachedUserUpdatedAt });
  }
  if (cachedCatchPage) {
    queryClient.setQueryData(catchPageQueryKey(cachedCatchPage.page), cachedCatchPage, { updatedAt: 1 });
  }
  prepareQueryClient?.(queryClient);

  function Wrapper({ children }: PropsWithChildren) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[initialEntry]}>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  }

  const page = protectedRoute ? (
    <ProtectedRoute><CatchListPage /></ProtectedRoute>
  ) : <CatchListPage />;

  return {
    queryClient,
    user: userEvent.setup(),
    ...render(
      <Routes>
        <Route path="/catches" element={<>{page}<LocationProbe /></>} />
        <Route path="/catches/new" element={<h1>新建钓获</h1>} />
        <Route path="/catches/:id" element={<h1>钓获详情</h1>} />
        <Route path="/login" element={<><h1>登录</h1><LocationProbe /></>} />
      </Routes>,
      { wrapper: Wrapper },
    ),
  };
}

beforeEach(() => {
  fetchCatchPageMock.mockReset();
  fetchCurrentUserMock.mockReset();
  fetchCatchPageMock.mockResolvedValue(populatedPage);
  fetchCurrentUserMock.mockResolvedValue(authenticatedUser);
});

test('shows an accessible loading state while personal catches are pending', () => {
  fetchCatchPageMock.mockImplementation(() => new Promise(() => undefined));

  renderCatchList();

  expect(screen.getByText('正在加载钓获记录…')).toHaveAttribute('role', 'status');
});

test('shows an empty state and creation link without catalog requests', async () => {
  fetchCatchPageMock.mockResolvedValue({ ...populatedPage, items: [], totalItems: 0, totalPages: 0 });

  renderCatchList();

  expect(await screen.findByRole('heading', { name: '还没有钓获记录' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '记录第一次钓获' })).toHaveAttribute('href', '/catches/new');
});

test('renders private catch summaries without per-row catalog requests', async () => {
  renderCatchList();

  expect(await screen.findByRole('heading', { name: '乌鳢' })).toBeInTheDocument();
  expect(screen.getByText('2026-08-20 · 城郊水库')).toBeInTheDocument();
  expect(screen.getByText('42.5 cm · 1350 g')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '查看这次钓获' })).toHaveAttribute('href', '/catches/31');
});

test('shows a safe error and retries the catch page on demand', async () => {
  fetchCatchPageMock
    .mockRejectedValueOnce(new Error('catch query failed at db.internal'))
    .mockRejectedValueOnce(new Error('catch query failed at db.internal'))
    .mockRejectedValueOnce(new Error('catch query failed at db.internal'))
    .mockResolvedValueOnce(populatedPage);
  const { user } = renderCatchList();

  const status = await screen.findByText('加载钓获记录失败，请稍后重试');
  expect(status).not.toHaveTextContent('db.internal');
  await user.click(screen.getByRole('button', { name: '重试' }));

  expect(await screen.findByRole('heading', { name: '乌鳢' })).toBeInTheDocument();
  expect(fetchCatchPageMock).toHaveBeenCalledTimes(4);
});

test('normalizes malformed URL pages to zero and never supplies a client size', async () => {
  renderCatchList({ initialEntry: '/catches?page=invalid' });

  await screen.findByRole('heading', { name: '乌鳢' });

  expect(fetchCatchPageMock).toHaveBeenCalledWith(0);
});

test('uses server pagination metadata to update the URL', async () => {
  fetchCatchPageMock.mockImplementation((page: number) => Promise.resolve({
    ...populatedPage,
    page,
    totalItems: 24,
    totalPages: 2,
  }));
  const { user } = renderCatchList();

  const pagination = await screen.findByRole('navigation', { name: '钓获记录分页' });
  expect(within(pagination).getByRole('button', { name: '上一页' })).toBeDisabled();
  await user.click(within(pagination).getByRole('button', { name: '下一页' }));

  expect(screen.getByTestId('location')).toHaveTextContent('/catches?page=1');
  await waitFor(() => expect(fetchCatchPageMock).toHaveBeenLastCalledWith(1));
});

test('returns to the last valid page after an out-of-range result', async () => {
  fetchCatchPageMock
    .mockResolvedValueOnce({ ...populatedPage, items: [], page: 1, totalItems: 1, totalPages: 1 })
    .mockResolvedValueOnce(populatedPage);

  renderCatchList({ initialEntry: '/catches?page=1' });

  await waitFor(() => expect(fetchCatchPageMock).toHaveBeenLastCalledWith(0));
  expect(screen.getByTestId('location')).toHaveTextContent('/catches');
  expect(await screen.findByRole('heading', { name: '乌鳢' })).toBeInTheDocument();
});

test('the protected catch route preserves its page return target for anonymous users', async () => {
  fetchCurrentUserMock.mockRejectedValue(new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED',
    message: '请先登录',
    fieldErrors: [],
    requestId: 'test-request',
  }));

  renderCatchList({ initialEntry: '/catches?page=1', protectedRoute: true });

  expect(await screen.findByRole('heading', { name: '登录' })).toBeInTheDocument();
  expect(screen.getByTestId('location')).toHaveTextContent('/login?returnTo=%2Fcatches%3Fpage%3D1');
  expect(fetchCatchPageMock).not.toHaveBeenCalled();
});

test('a confirmed catch-page 401 clears cached records before redirecting to login', async () => {
  const unauthorized = new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED',
    message: '请先登录',
    fieldErrors: [],
    requestId: 'test-request',
  });
  fetchCatchPageMock.mockRejectedValue(unauthorized);
  const { queryClient } = renderCatchList({
    protectedRoute: true,
    cachedUser: authenticatedUser,
    cachedUserUpdatedAt: Date.now(),
    cachedCatchPage: populatedPage,
    prepareQueryClient: (client) => {
      client.setQueryData(catchDetailQueryKey(31), { id: 31, notes: '仅用户 A 可见' });
    },
  });

  expect(await screen.findByRole('heading', { name: '登录' })).toBeInTheDocument();
  expect(
    queryClient.getQueriesData({ queryKey: ['catches'] })
      .every(([, data]) => data === undefined),
  ).toBe(true);
  expect(screen.queryByRole('heading', { name: '乌鳢' })).not.toBeInTheDocument();
});
