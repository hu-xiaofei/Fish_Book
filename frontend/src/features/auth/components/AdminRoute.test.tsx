import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import type { PropsWithChildren } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import { ApiError } from '../../../shared/api/ApiError';
import type { User } from '../../../shared/api/types';
import { deferred } from '../../../test/renderWithProviders';
import { catchPageQueryKey } from '../../catchlog/api/catchRecordsApi';
import { FAVORITES_QUERY_KEY } from '../../favorites/api/favoritesApi';
import { CURRENT_USER_QUERY_KEY } from '../api/currentUser';
import { AdminRoute } from './AdminRoute';

const { currentUserMock } = vi.hoisted(() => ({
  currentUserMock: vi.fn(),
}));

vi.mock('../api/currentUser', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/currentUser')>();
  return { ...actual, fetchCurrentUser: currentUserMock };
});

function renderAdminRoute(initialEntry: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retryDelay: 0 } },
  });

  function Wrapper({ children }: PropsWithChildren) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[initialEntry]}>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  }

  render(
    <Routes>
      <Route
        path="/admin/fishes"
        element={(
          <AdminRoute>
            <h1>管理内容</h1>
          </AdminRoute>
        )}
      />
      <Route path="/login" element={<><h1>登录</h1><LocationProbe /></>} />
    </Routes>,
    { wrapper: Wrapper },
  );

  return queryClient;
}

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}{location.search}{location.hash}</output>;
}

function unauthorizedError() {
  return new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED',
    message: '请先登录',
    fieldErrors: [],
    requestId: 'test-request',
  });
}

beforeEach(() => {
  currentUserMock.mockReset();
});

test('administrator session renders management content', async () => {
  currentUserMock.mockResolvedValue({
    id: 1,
    email: 'admin@example.com',
    nickname: '管理员',
    role: 'ADMIN',
  } satisfies User);

  renderAdminRoute('/admin/fishes?status=DRAFT');

  expect(await screen.findByRole('heading', { name: '管理内容' })).toBeInTheDocument();
});

test('ordinary user sees a local forbidden page without rendering children', async () => {
  currentUserMock.mockResolvedValue({
    id: 2,
    email: 'angler@example.com',
    nickname: '钓友',
    role: 'USER',
  } satisfies User);

  renderAdminRoute('/admin/fishes');

  expect(await screen.findByRole('heading', { name: '没有管理员权限' })).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: '管理内容' })).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: '返回首页' })).toHaveAttribute('href', '/');
});

test('administrator route waits for session lookup before making an access decision', async () => {
  const session = deferred<User>();
  currentUserMock.mockReturnValue(session.promise);
  renderAdminRoute('/admin/fishes');

  expect(screen.getByRole('status', { name: '正在检查登录状态' })).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: '没有管理员权限' })).not.toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: '管理内容' })).not.toBeInTheDocument();

  session.resolve({
    id: 1,
    email: 'admin@example.com',
    nickname: '管理员',
    role: 'ADMIN',
  });

  expect(await screen.findByRole('heading', { name: '管理内容' })).toBeInTheDocument();
});

test('anonymous administrator lookup clears session data and redirects with path query and hash', async () => {
  currentUserMock.mockRejectedValue(unauthorizedError());
  const queryClient = renderAdminRoute('/admin/fishes?status=DRAFT#pending');
  queryClient.setQueryData(FAVORITES_QUERY_KEY, [{ fishSlug: 'stale-favorite' }]);
  queryClient.setQueryData(catchPageQueryKey(0), [{ id: 9, fishSlug: 'stale-catch' }]);

  expect(await screen.findByRole('heading', { name: '登录' })).toBeInTheDocument();
  expect(screen.getByTestId('location')).toHaveTextContent(
    '/login?returnTo=%2Fadmin%2Ffishes%3Fstatus%3DDRAFT%23pending',
  );
  expect(queryClient.getQueryData(CURRENT_USER_QUERY_KEY)).toBeUndefined();
  expect(queryClient.getQueriesData({ queryKey: FAVORITES_QUERY_KEY })).toEqual([]);
  expect(queryClient.getQueriesData({ queryKey: ['catches'] })).toEqual([]);
});

test('non-authentication lookup failures stay on a safe error state', async () => {
  currentUserMock.mockRejectedValue(new ApiError(500, {
    code: 'INTERNAL_ERROR',
    message: 'jdbc://db.internal details',
    fieldErrors: [],
    requestId: 'test-request',
  }));

  renderAdminRoute('/admin/fishes');

  const status = await screen.findByText('暂时无法确认登录状态，请稍后重试');
  expect(status).toHaveTextContent('暂时无法确认登录状态，请稍后重试');
  expect(status).not.toHaveTextContent('jdbc://db.internal');
  expect(screen.queryByRole('heading', { name: '登录' })).not.toBeInTheDocument();
});
